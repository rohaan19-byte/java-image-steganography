import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Implements Regular/Singular (RS) Steganalysis for detecting spatial-domain LSB replacement steganography.
 *
 * <h3>Theoretical Background & Methodology:</h3>
 * <p>RS Steganalysis was developed by J. Fridrich, M. Goljan, and R. Du (2001).
 * It evaluates how invertible noise-like permutations (flipping operations) alter the spatial smoothness
 * of contiguous pixel groups.</p>
 *
 * <h4>1. Pixel Groups & Discrimination Function:</h4>
 * <ul>
 *   <li>The image is partitioned into disjoint horizontal groups of $n = 4$ adjacent pixels:
 *       $G = (x_1, x_2, x_3, x_4)$ in row-major scan order. Groups do not cross row boundaries.</li>
 *   <li>Smoothness is measured by the total variation discrimination function:
 *       <pre>f(x_1, x_2, x_3, x_4) = |x_2 - x_1| + |x_3 - x_2| + |x_4 - x_3|</pre>
 *   </li>
 * </ul>
 *
 * <h4>2. Invertible Permutations (Flipping Operations):</h4>
 * <ul>
 *   <li><b>Identity $F_0$:</b> $F_0(x) = x$</li>
 *   <li><b>Standard LSB flipping $F_1$:</b> $F_1(x) = x \oplus 1$ ($0 \leftrightarrow 1, 2 \leftrightarrow 3, \dots, 254 \leftrightarrow 255$)</li>
 *   <li><b>Dual shifted flipping $F_{-1}$:</b> Involutive bijection on $\{0, \dots, 255\}$ defined by:
 *       $F_{-1}(0) = 0$, $F_{-1}(255) = 255$, and for $x \in [1, 254]$:
 *       $F_{-1}(x) = x + 1$ if $x$ is odd, and $x - 1$ if $x$ is even.
 *       This pairs $(1 \leftrightarrow 2, 3 \leftrightarrow 4, \dots, 253 \leftrightarrow 254)$ while keeping boundary values in range.</li>
 * </ul>
 *
 * <h4>3. Mask Operations & Group Classification:</h4>
 * <ul>
 *   <li>Standard mask $M = [0, 1, 1, 0]$ and negative mask $-M = [0, -1, -1, 0]$.</li>
 *   <li>Applying mask $M$ to $G$: $F_M(G) = (F_{m_1}(x_1), F_{m_2}(x_2), F_{m_3}(x_3), F_{m_4}(x_4))$.</li>
 *   <li><b>Regular Group ($R_M$):</b> $f(F_M(G)) > f(G)$ (flipping increases roughness)</li>
 *   <li><b>Singular Group ($S_M$):</b> $f(F_M(G)) < f(G)$ (flipping decreases roughness)</li>
 *   <li><b>Unusable Group ($U_M$):</b> $f(F_M(G)) = f(G)$ (flipping leaves roughness unchanged)</li>
 *   <li>The same definitions apply to $-M$, yielding counts $R_{-M}, S_{-M}, U_{-M}$.</li>
 * </ul>
 *
 * <h4>4. Steganographic Behavior & Hypothesis:</h4>
 * <ul>
 *   <li>In natural cover images, local spatial correlation implies that flipping LSBs almost always increases roughness:
 *       $R_M \approx R_{-M}$ and $S_M \approx S_{-M}$, with $R_M > S_M$.</li>
 *   <li>As LSB replacement data is embedded, the randomization of LSBs causes $R_M$ and $S_M$ to converge
 *       ($R_M - S_M \to 0$), while $R_{-M}$ and $S_{-M}$ diverge ($R_{-M} - S_{-M}$ increases).</li>
 * </ul>
 *
 * <h4>5. Payload Rate Estimation:</h4>
 * <p>Using Fridrich's quadratic polynomial model fitted against group counts from the image and its fully
 * inverted counterpart $F_1(I)$ ($R_{M,1}, S_{M,1}, R_{-M,1}, S_{-M,1}$):
 * <pre>
 *   d_0 = R_M - S_M,          d_1 = R_{M,1} - S_{M,1}
 *   d_{-0} = R_{-M} - S_{-M}, d_{-1} = R_{-M,1} - S_{-M,1}
 *   A = 2(d_1 + d_0)
 *   B = (d_{-0} - d_{-1}) - (d_1 + 3 d_0)
 *   C = d_0 - d_{-0}
 *   z = (-B \pm \sqrt{B^2 - 4AC}) / (2A)  [root of smaller absolute value]
 *   estimated_payload_rate p = z / (z - 0.5)
 * </pre>
 * </p>
 *
 * <p><b>Important Caveats & Limitations:</b>
 * RS analysis is an empirical statistical estimator. Natural image textures, sharp gradients,
 * low embedding capacity (< 10%), or highly smoothed/compressed images can bias the estimator.
 * RS results indicate statistical anomalies consistent with LSB replacement, but do not constitute
 * proof of hidden data.</p>
 */
public class RSAnalysis {

    public enum Channel {
        RED,
        GREEN,
        BLUE
    }

    /**
     * Reliability status of the RS payload estimation.
     */
    public enum EstimationStatus {
        /**
         * The raw estimate is finite, falls strictly within the physically valid interval [0.0, 1.0] (0% to 100%),
         * and the estimator's data-quality checks are satisfied.
         * Note: This status signifies approximate physical plausibility under the RS model, not guaranteed accuracy.
         */
        RELIABLE,

        /**
         * The raw estimate is below zero (p < 0.0).
         * Negative embedding rates are physically invalid. While statistical noise, finite sample size, or
         * violation of RS assumptions frequently produce negative numbers on clean covers,
         * this result cannot be presented as a valid embedding rate and does not prove the image is clean.
         */
        NEGATIVE_ESTIMATE,

        /**
         * The raw estimate exceeds 1.0 (> 100%), exceeding maximum physical image capacity.
         */
        OUT_OF_RANGE,

        /**
         * The mathematical model cannot produce a unique valid real estimate (e.g. negative discriminant Delta < 0,
         * singularity at z = 0.5, or degenerate coefficients A=0, B=0).
         */
        INDETERMINATE,

        /**
         * The image dimensions or usable group counts are insufficient for statistical estimation
         * (< 50 total groups or < 20 non-flat groups with initial variation).
         */
        INSUFFICIENT_DATA
    }

    /** Standard 4-pixel mask: M = [0, 1, 1, 0] */
    public static final int[] MASK = new int[] { 0, 1, 1, 0 };
    /** Standard negative 4-pixel mask: -M = [0, -1, -1, 0] */
    public static final int[] NEG_MASK = new int[] { 0, -1, -1, 0 };

    /**
     * Standard LSB flipping permutation: F1(x) = x ^ 1.
     * Maps 0 <-> 1, 2 <-> 3, ..., 254 <-> 255.
     */
    public static int flipStandard(int x) {
        return x ^ 1;
    }

    /**
     * Dual LSB flipping permutation: F-1(x).
     * Involutive bijection on {0, ..., 255} pairing 1 <-> 2, 3 <-> 4, ..., 253 <-> 254.
     * Fixes 0 and 255 to maintain range safety.
     */
    public static int flipDual(int x) {
        if (x <= 0) return 0;
        if (x >= 255) return 255;
        return (x % 2 == 1) ? (x + 1) : (x - 1);
    }

    /**
     * Applies a mask component m to pixel value x.
     * m = 1 -> F1(x); m = -1 -> F-1(x); m = 0 -> F0(x) = x.
     */
    public static int applyFlipping(int x, int m) {
        if (m == 1) {
            return flipStandard(x);
        } else if (m == -1) {
            return flipDual(x);
        } else {
            return x;
        }
    }

    /**
     * Total variation discrimination function for a 4-pixel group:
     * f(x1, x2, x3, x4) = |x2 - x1| + |x3 - x2| + |x4 - x3|.
     */
    public static int discriminationFunction(int x1, int x2, int x3, int x4) {
        return Math.abs(x2 - x1) + Math.abs(x3 - x2) + Math.abs(x4 - x3);
    }

    /**
     * Encapsulates the mathematical outcome of the payload rate estimation.
     */
    public static class EstimationResult {
        public final double rawRate;
        public final EstimationStatus status;
        public final String reason;

        public EstimationResult(double rawRate, EstimationStatus status, String reason) {
            this.rawRate = rawRate;
            this.status = status;
            this.reason = reason;
        }
    }

    /**
     * Holds the complete results of RS Steganalysis for a color channel.
     */
    public static class Result {
        private final Channel channel;
        private final long totalGroups;

        // Group counts for original image under M
        private final long regularM;
        private final long singularM;
        private final long unusableM;

        // Group counts for original image under -M
        private final long regularNegM;
        private final long singularNegM;
        private final long unusableNegM;

        // Group counts for inverted image F1(I)
        private final long regularMInverted;
        private final long singularMInverted;
        private final long regularNegMInverted;
        private final long singularNegMInverted;

        // Raw estimated payload rate p (fraction in [-inf, +inf] or Double.NaN)
        private final double estimatedPayloadRate;
        private final EstimationStatus estimationStatus;
        private final String estimationReason;
        private final boolean isSuspicious;
        private final String interpretation;

        public Result(Channel channel, long totalGroups,
                      long regularM, long singularM, long unusableM,
                      long regularNegM, long singularNegM, long unusableNegM,
                      long regularMInverted, long singularMInverted,
                      long regularNegMInverted, long singularNegMInverted,
                      double estimatedPayloadRate, EstimationStatus estimationStatus,
                      String estimationReason, boolean isSuspicious, String interpretation) {
            this.channel = channel;
            this.totalGroups = totalGroups;
            this.regularM = regularM;
            this.singularM = singularM;
            this.unusableM = unusableM;
            this.regularNegM = regularNegM;
            this.singularNegM = singularNegM;
            this.unusableNegM = unusableNegM;
            this.regularMInverted = regularMInverted;
            this.singularMInverted = singularMInverted;
            this.regularNegMInverted = regularNegMInverted;
            this.singularNegMInverted = singularNegMInverted;
            this.estimatedPayloadRate = estimatedPayloadRate;
            this.estimationStatus = estimationStatus;
            this.estimationReason = estimationReason;
            this.isSuspicious = isSuspicious;
            this.interpretation = interpretation;
        }

        /** Backward compatibility constructor. */
        public Result(Channel channel, long totalGroups,
                      long regularM, long singularM, long unusableM,
                      long regularNegM, long singularNegM, long unusableNegM,
                      long regularMInverted, long singularMInverted,
                      long regularNegMInverted, long singularNegMInverted,
                      double estimatedPayloadRate, boolean isSuspicious, String interpretation) {
            this(channel, totalGroups, regularM, singularM, unusableM,
                    regularNegM, singularNegM, unusableNegM,
                    regularMInverted, singularMInverted, regularNegMInverted, singularNegMInverted,
                    estimatedPayloadRate,
                    deriveStatus(estimatedPayloadRate),
                    Double.isNaN(estimatedPayloadRate) ? "Indeterminate" : "Calculated",
                    isSuspicious, interpretation);
        }

        private static EstimationStatus deriveStatus(double rate) {
            if (Double.isNaN(rate) || Double.isInfinite(rate)) return EstimationStatus.INDETERMINATE;
            if (rate < 0.0) return EstimationStatus.NEGATIVE_ESTIMATE;
            if (rate > 1.0) return EstimationStatus.OUT_OF_RANGE;
            return EstimationStatus.RELIABLE;
        }

        public Channel getChannel() { return channel; }
        public long getTotalGroups() { return totalGroups; }

        public long getRegularM() { return regularM; }
        public long getSingularM() { return singularM; }
        public long getUnusableM() { return unusableM; }

        public long getRegularNegM() { return regularNegM; }
        public long getSingularNegM() { return singularNegM; }
        public long getUnusableNegM() { return unusableNegM; }

        public double getRegularMPct() { return totalGroups == 0 ? 0 : (regularM * 100.0 / totalGroups); }
        public double getSingularMPct() { return totalGroups == 0 ? 0 : (singularM * 100.0 / totalGroups); }
        public double getUnusableMPct() { return totalGroups == 0 ? 0 : (unusableM * 100.0 / totalGroups); }

        public double getRegularNegMPct() { return totalGroups == 0 ? 0 : (regularNegM * 100.0 / totalGroups); }
        public double getSingularNegMPct() { return totalGroups == 0 ? 0 : (singularNegM * 100.0 / totalGroups); }
        public double getUnusableNegMPct() { return totalGroups == 0 ? 0 : (unusableNegM * 100.0 / totalGroups); }

        public double getDifferenceM() { return getRegularMPct() - getSingularMPct(); }
        public double getDifferenceNegM() { return getRegularNegMPct() - getSingularNegMPct(); }

        /** Returns the raw mathematical payload rate estimate. */
        public double getEstimatedPayloadRate() { return estimatedPayloadRate; }

        /** Returns the raw mathematical payload rate estimate formatted as a percentage. */
        public double getEstimatedPayloadRatePct() {
            return Double.isNaN(estimatedPayloadRate) ? Double.NaN : (estimatedPayloadRate * 100.0);
        }

        /**
         * Returns a physically valid payload rate in [0.0, 1.0], or Double.NaN if the estimate is
         * negative, out of range, or indeterminate.
         */
        public double getPhysicallyValidPayloadRate() {
            return (estimationStatus == EstimationStatus.RELIABLE) ? estimatedPayloadRate : Double.NaN;
        }

        public EstimationStatus getEstimationStatus() { return estimationStatus; }
        public String getEstimationReason() { return estimationReason; }

        public boolean isSuspicious() { return isSuspicious; }
        public String getInterpretation() { return interpretation; }
    }

    /**
     * Analyzes the default embedding channel (Blue) of a BufferedImage using RS Steganalysis.
     *
     * @param image the image to analyze
     * @return Result containing RS statistics and payload rate estimation
     */
    public static Result analyze(BufferedImage image) {
        return analyze(image, Channel.BLUE);
    }

    /**
     * Analyzes a specified color channel of a BufferedImage using RS Steganalysis.
     *
     * @param image   the image to analyze
     * @param channel the color channel (RED, GREEN, or BLUE)
     * @return Result containing RS statistics and payload rate estimation
     */
    public static Result analyze(BufferedImage image, Channel channel) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        if (channel == null) {
            channel = Channel.BLUE;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // Extract channel values into a 2D integer array [y][x]
        int[][] plane = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int val;
                switch (channel) {
                    case RED:   val = (rgb >> 16) & 0xFF; break;
                    case GREEN: val = (rgb >> 8) & 0xFF;  break;
                    case BLUE:
                    default:    val = rgb & 0xFF;         break;
                }
                plane[y][x] = val;
            }
        }

        return analyzePlane(plane, channel);
    }

    /**
     * Analyzes a 2D intensity plane [height][width] with RS Steganalysis.
     */
    public static Result analyzePlane(int[][] plane, Channel channel) {
        if (plane == null || plane.length == 0 || plane[0].length == 0) {
            return new Result(channel, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    Double.NaN, EstimationStatus.INSUFFICIENT_DATA,
                    "Image has empty dimensions; no pixel groups available.", false,
                    "Image has empty dimensions; no pixel groups available.");
        }

        int height = plane.length;
        int width = plane[0].length;

        int groupsPerRow = width / 4;
        long totalGroups = (long) height * groupsPerRow;

        if (totalGroups == 0) {
            return new Result(channel, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    Double.NaN, EstimationStatus.INSUFFICIENT_DATA,
                    "Image width (" + width + ") is less than group size 4; no complete groups.", false,
                    "Image width (" + width + ") is less than group size 4; no complete groups.");
        }

        long rM = 0, sM = 0, uM = 0;
        long rNegM = 0, sNegM = 0, uNegM = 0;

        long rM_inv = 0, sM_inv = 0;
        long rNegM_inv = 0, sNegM_inv = 0;

        long initialVaryingGroups = 0;

        for (int y = 0; y < height; y++) {
            for (int g = 0; g < groupsPerRow; g++) {
                int baseX = g * 4;
                int x0 = plane[y][baseX];
                int x1 = plane[y][baseX + 1];
                int x2 = plane[y][baseX + 2];
                int x3 = plane[y][baseX + 3];

                int fOrig = discriminationFunction(x0, x1, x2, x3);
                if (fOrig > 0) {
                    initialVaryingGroups++;
                }

                // --- 1. Original image with mask M = [0, 1, 1, 0] ---
                int fm0 = applyFlipping(x0, MASK[0]);
                int fm1 = applyFlipping(x1, MASK[1]);
                int fm2 = applyFlipping(x2, MASK[2]);
                int fm3 = applyFlipping(x3, MASK[3]);
                int fM = discriminationFunction(fm0, fm1, fm2, fm3);

                if (fM > fOrig) rM++;
                else if (fM < fOrig) sM++;
                else uM++;

                // --- 2. Original image with mask -M = [0, -1, -1, 0] ---
                int fn0 = applyFlipping(x0, NEG_MASK[0]);
                int fn1 = applyFlipping(x1, NEG_MASK[1]);
                int fn2 = applyFlipping(x2, NEG_MASK[2]);
                int fn3 = applyFlipping(x3, NEG_MASK[3]);
                int fNegM = discriminationFunction(fn0, fn1, fn2, fn3);

                if (fNegM > fOrig) rNegM++;
                else if (fNegM < fOrig) sNegM++;
                else uNegM++;

                // --- 3. Inverted image F1(I) with mask M and -M (for Fridrich polynomial estimation) ---
                int ix0 = flipStandard(x0);
                int ix1 = flipStandard(x1);
                int ix2 = flipStandard(x2);
                int ix3 = flipStandard(x3);
                int fInvOrig = discriminationFunction(ix0, ix1, ix2, ix3);

                int ifm0 = applyFlipping(ix0, MASK[0]);
                int ifm1 = applyFlipping(ix1, MASK[1]);
                int ifm2 = applyFlipping(ix2, MASK[2]);
                int ifm3 = applyFlipping(ix3, MASK[3]);
                int fInvM = discriminationFunction(ifm0, ifm1, ifm2, ifm3);

                if (fInvM > fInvOrig) rM_inv++;
                else if (fInvM < fInvOrig) sM_inv++;

                int ifn0 = applyFlipping(ix0, NEG_MASK[0]);
                int ifn1 = applyFlipping(ix1, NEG_MASK[1]);
                int ifn2 = applyFlipping(ix2, NEG_MASK[2]);
                int ifn3 = applyFlipping(ix3, NEG_MASK[3]);
                int fInvNegM = discriminationFunction(ifn0, ifn1, ifn2, ifn3);

                if (fInvNegM > fInvOrig) rNegM_inv++;
                else if (fInvNegM < fInvOrig) sNegM_inv++;
            }
        }

        // Relative percentage differences
        double rMPct = (rM * 100.0) / totalGroups;
        double sMPct = (sM * 100.0) / totalGroups;
        double rNegMPct = (rNegM * 100.0) / totalGroups;
        double sNegMPct = (sNegM * 100.0) / totalGroups;

        double rM_invPct = (rM_inv * 100.0) / totalGroups;
        double sM_invPct = (sM_inv * 100.0) / totalGroups;
        double rNegM_invPct = (rNegM_inv * 100.0) / totalGroups;
        double sNegM_invPct = (sNegM_inv * 100.0) / totalGroups;

        // Check for constant / flat image where initial spatial variation is virtually absent
        if (initialVaryingGroups < 20) {
            return new Result(channel, totalGroups, rM, sM, uM, rNegM, sNegM, uNegM,
                    rM_inv, sM_inv, rNegM_inv, sNegM_inv,
                    Double.NaN, EstimationStatus.INSUFFICIENT_DATA,
                    "Flat or constant-color channel: initial spatial variation is zero (f(G)=0 everywhere)",
                    false,
                    "Flat or constant-color channel: initial spatial variation is zero. RS linear model assumptions do not hold; payload estimation is indeterminate.");
        }

        // Fridrich quadratic estimation differences
        double d0 = rMPct - sMPct;
        double d1 = rM_invPct - sM_invPct;
        double dNeg0 = rNegMPct - sNegMPct;
        double dNeg1 = rNegM_invPct - sNegM_invPct;

        long nonFlatGroups = (rM + sM) + (rNegM + sNegM);
        EstimationResult est = estimatePayload(d0, d1, dNeg0, dNeg1, totalGroups, nonFlatGroups);

        boolean suspicious = false;
        String interpretation;

        if (est.status == EstimationStatus.INSUFFICIENT_DATA) {
            interpretation = "Insufficient data: " + est.reason;
        } else if (est.status == EstimationStatus.INDETERMINATE) {
            interpretation = "Indeterminate estimation: " + est.reason;
        } else if (est.status == EstimationStatus.NEGATIVE_ESTIMATE) {
            interpretation = String.format("The estimator returned a negative value (%+.2f%%), which is not a physically valid embedding rate. "
                    + "Statistical noise or violation of RS assumptions may explain the result. No reliable payload rate can be concluded.",
                    est.rawRate * 100.0);
        } else if (est.status == EstimationStatus.OUT_OF_RANGE) {
            interpretation = String.format("Atypical RS signature (RM-SM=%+.2f%%, R-M-S-M=%+.2f%%). %s",
                    d0, dNeg0, est.reason);
        } else {
            // RELIABLE: est.rawRate in [0.0, 1.0]
            double rate = est.rawRate;
            if (rate > 0.08 && d0 < dNeg0) {
                suspicious = true;
                interpretation = String.format("Statistical asymmetry detected (RM-SM=%+.2f%% vs R-M-S-M=%+.2f%%). "
                        + "Pattern is consistent with LSB replacement (estimated payload ~%.1f%%).",
                        d0, dNeg0, rate * 100.0);
            } else if (Math.abs(d0 - dNeg0) < 3.0 && Math.abs(d0) > 3.0) {
                interpretation = String.format("Natural correlation observed (RM~=R-M, SM~=S-M, RM-SM=%+.2f%%, R-M-S-M=%+.2f%%). "
                        + "No significant RS signature of LSB replacement.", d0, dNeg0);
            } else {
                interpretation = String.format("Inconclusive RS pattern (RM-SM=%+.2f%%, R-M-S-M=%+.2f%%). %s",
                        d0, dNeg0, est.reason);
            }
        }

        return new Result(channel, totalGroups, rM, sM, uM, rNegM, sNegM, uNegM,
                rM_inv, sM_inv, rNegM_inv, sNegM_inv, est.rawRate, est.status, est.reason,
                suspicious, interpretation);
    }

    /**
     * Estimates payload rate using Fridrich's RS quadratic equation with comprehensive validation.
     *
     * <p>Solves: 2(d1 + d0)z^2 + ((dNeg0 - dNeg1) - (d1 + 3d0))z + (d0 - dNeg0) = 0.
     * Then: p = z / (z - 0.5).
     *
     * @param d0             RM - SM on original image
     * @param d1             RM - SM on inverted image F1(I)
     * @param dNeg0          R-M - S-M on original image
     * @param dNeg1          R-M - S-M on inverted image F1(I)
     * @param totalGroups    total number of 4-pixel groups
     * @param nonFlatGroups  total count of groups that were regular or singular
     * @return EstimationResult containing raw rate, status, and descriptive reason
     */
    public static EstimationResult estimatePayload(double d0, double d1, double dNeg0, double dNeg1,
                                                   long totalGroups, long nonFlatGroups) {
        // Implementation safeguards against small/flat samples (not universal scientific constants)
        if (totalGroups < 50 || nonFlatGroups < 20) {
            return new EstimationResult(Double.NaN, EstimationStatus.INSUFFICIENT_DATA,
                    String.format("Sample too small or flat (%d total groups, %d non-flat groups; safeguard minimum: 50/20)",
                            totalGroups, nonFlatGroups));
        }

        // Check for degenerate flat response where flipping always increases variation because initial roughness is 0
        if (d0 > 95.0 && d1 > 95.0 && dNeg0 > 95.0 && dNeg1 > 95.0) {
            return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                    "Degenerate flat surface: all groups classified as Regular (d0~=d1~=100%); zero singular groups");
        }

        // If all spreads are practically zero, flipping has no observable impact
        if (Math.abs(d0) < 0.05 && Math.abs(dNeg0) < 0.05 && Math.abs(d1) < 0.05 && Math.abs(dNeg1) < 0.05) {
            return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                    "Spreads across all masks are virtually zero (< 0.05%); no usable curvature");
        }

        double A = 2.0 * (d1 + d0);
        double B = (dNeg0 - dNeg1) - (d1 + 3.0 * d0);
        double C = d0 - dNeg0;

        if (Math.abs(A) < 1e-9) {
            // Linear case: B*z + C = 0 => z = -C / B
            if (Math.abs(B) < 1e-9) {
                return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                        "Degenerate linear coefficients (A=0, B=0); no unique solution exists");
            }
            double z = -C / B;
            if (Math.abs(z - 0.5) < 1e-9) {
                return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                        "Singularity at z = 0.5 (division by zero)");
            }
            double p = z / (z - 0.5);
            return evaluateEstimatedRate(p, z);
        }

        double discriminant = B * B - 4.0 * A * C;
        if (discriminant < 0) {
            return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                    String.format("Negative discriminant (B^2 - 4AC = %.4f < 0); no real RS parabola intersection", discriminant));
        }

        double sqrtD = Math.sqrt(discriminant);
        double z1 = (-B + sqrtD) / (2.0 * A);
        double z2 = (-B - sqrtD) / (2.0 * A);

        // Fridrich's rule: root of smaller absolute value |z|
        double z = (Math.abs(z1) <= Math.abs(z2)) ? z1 : z2;

        if (Math.abs(z - 0.5) < 1e-9) {
            return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                    "Singularity at z = 0.5 (division by zero)");
        }

        double p = z / (z - 0.5);
        return evaluateEstimatedRate(p, z);
    }

    private static EstimationResult evaluateEstimatedRate(double p, double z) {
        if (Double.isNaN(p) || Double.isInfinite(p)) {
            return new EstimationResult(Double.NaN, EstimationStatus.INDETERMINATE,
                    "Non-finite payload calculation");
        }

        // Strict physical validity interval: [0.0, 1.0] (0% to 100%)
        if (p < 0.0) {
            return new EstimationResult(p, EstimationStatus.NEGATIVE_ESTIMATE,
                    String.format("The estimator returned a negative value (%.2f%%, root z = %.4f), which is not a physically valid embedding rate. "
                            + "Statistical noise or violation of RS assumptions may explain the result. No reliable payload rate can be concluded.",
                            p * 100.0, z));
        } else if (p > 1.0) {
            return new EstimationResult(p, EstimationStatus.OUT_OF_RANGE,
                    String.format("The estimator returned a value exceeding 100%% (%.2f%%, root z = %.4f), which exceeds maximum image capacity. "
                            + "Violation of RS linear roughness assumptions or severe texture distortion may explain the result.",
                            p * 100.0, z));
        } else {
            return new EstimationResult(p, EstimationStatus.RELIABLE,
                    String.format("Plausible physical RS estimate: %.2f%% (root z = %.4f; approximate estimate under LSB replacement model)",
                            p * 100.0, z));
        }
    }

    /**
     * Backward-compatible estimator method returning the raw estimated payload rate or Double.NaN.
     */
    public static double estimatePayloadRate(double d0, double d1, double dNeg0, double dNeg1) {
        EstimationResult res = estimatePayload(d0, d1, dNeg0, dNeg1, 1000, 500);
        return res.rawRate;
    }

    /**
     * Loads an image file from disk and performs RS analysis.
     */
    public static Result analyze(File file, Channel channel) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Image file not found: " + (file == null ? "null" : file.getPath()));
        }
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IllegalArgumentException("Unsupported or corrupted image file: " + file.getPath());
        }
        return analyze(image, channel);
    }
}
