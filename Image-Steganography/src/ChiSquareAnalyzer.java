import java.awt.image.BufferedImage;

/**
 * Implements Chi-Square Steganalysis based on Pairs of Values (PoVs) as introduced by
 * Westfeld and Pfitzmann (1999), with corrected degrees of freedom and robust sparse-pair handling.
 *
 * <h3>Statistical Model & Methodology:</h3>
 * <ul>
 *   <li><b>Pairs of Values (PoVs):</b> Adjacent intensity levels are paired as (0, 1), (2, 3), ..., (254, 255),
 *       giving 128 disjoint pairs for an 8-bit channel.</li>
 *   <li><b>Null Hypothesis (H0):</b> The image has undergone LSB replacement steganography with random bits,
 *       equalizing the expected frequencies of even and odd values within each pair:
 *       <pre>E[2k] = E[2k+1] = (n_2k + n_2k+1) / 2</pre></li>
 *   <li><b>Alternative Hypothesis (H1):</b> The frequencies within pairs reflect natural image asymmetries
 *       (n_2k != n_2k+1) due to natural lighting and gradients.</li>
 *   <li><b>Test Statistic:</b>
 *       <pre>
 *       chi^2 = sum_{k: N_k >= threshold} ( (n_2k - n_2k+1)^2 / (n_2k + n_2k+1) )
 *       </pre>
 *   </li>
 *   <li><b>Degrees of Freedom (df = m):</b> Because the expected counts for each pair are estimated
 *       strictly from that pair's own marginal total N_k = n_2k + n_2k+1, each eligible pair has 1 internal
 *       constraint (2 bins - 1 constraint = 1 df). Summing across m independent eligible pairs yields
 *       <b>df = m</b> (the number of pairs actually included in the statistic).</li>
 *   <li><b>Sparse-Pair Handling:</b>
 *       Cochran's classic rule for chi-square validity requires expected counts E >= 5.0 (which corresponds
 *       to pair total N_k >= 10). A pair threshold of N_k >= 5 corresponds to expected counts E >= 2.5,
 *       which is a relaxed approximation rule. Excluded empty and sparse pairs are explicitly reported,
 *       and p-values are flagged if few pairs are eligible.</li>
 *   <li><b>p-value (Upper Tail):</b> Computed via regularized upper incomplete gamma function Q(df/2, chi^2/2).
 *       A small p-value (< 0.05) rejects H0 (significant natural asymmetry). A large p-value fails to reject H0
 *       (frequencies are statistically consistent with pair equality, but this is NOT definitive proof of steganography).
 *       If m = 0, p-value is {@link Double#NaN}.</li>
 * </ul>
 */
public class ChiSquareAnalyzer {

    /**
     * Default minimum sample count per PoV pair (5 corresponds to expected frequency E >= 2.5).
     * For strict Cochran compliance (E >= 5.0), use threshold = 10.
     */
    public static final int DEFAULT_MIN_PAIR_COUNT = 5;

    public enum Channel {
        RED,
        GREEN,
        BLUE
    }

    /**
     * Encapsulates the results of Chi-Square goodness-of-fit steganalysis.
     */
    public static class Result {
        private final Channel channel;
        private final int totalPairs;              // Always 128 for 8-bit channel
        private final int eligiblePairs;           // Pairs included in statistic (count >= threshold)
        private final int emptyPairs;              // Pairs with count == 0
        private final int sparsePairs;             // Pairs with 0 < count < threshold
        private final int minPairCountThreshold;
        private final double chiSquareStatistic;
        private final int degreesOfFreedom;        // Exactly equal to eligiblePairs (df = m)
        private final double pValue;               // Double.NaN if eligiblePairs == 0
        private final long totalSamplesAnalyzed;   // Total counts in eligible pairs
        private final long totalSamplesExcluded;   // Total counts in excluded sparse pairs
        private final boolean isApproximationReliable;

        public Result(Channel channel, int totalPairs, int eligiblePairs, int emptyPairs, int sparsePairs,
                      int minPairCountThreshold, double chiSquareStatistic, int degreesOfFreedom,
                      double pValue, long totalSamplesAnalyzed, long totalSamplesExcluded,
                      boolean isApproximationReliable) {
            this.channel = channel;
            this.totalPairs = totalPairs;
            this.eligiblePairs = eligiblePairs;
            this.emptyPairs = emptyPairs;
            this.sparsePairs = sparsePairs;
            this.minPairCountThreshold = minPairCountThreshold;
            this.chiSquareStatistic = chiSquareStatistic;
            this.degreesOfFreedom = degreesOfFreedom;
            this.pValue = pValue;
            this.totalSamplesAnalyzed = totalSamplesAnalyzed;
            this.totalSamplesExcluded = totalSamplesExcluded;
            this.isApproximationReliable = isApproximationReliable;
        }

        public Channel getChannel() { return channel; }
        public int getTotalPairs() { return totalPairs; }
        public int getEligiblePairs() { return eligiblePairs; }
        /** Backward compatibility alias for getEligiblePairs(). */
        public int getActivePairs() { return eligiblePairs; }
        public int getEmptyPairs() { return emptyPairs; }
        public int getSparsePairs() { return sparsePairs; }
        public int getMinPairCountThreshold() { return minPairCountThreshold; }
        public double getChiSquareStatistic() { return chiSquareStatistic; }
        public int getDegreesOfFreedom() { return degreesOfFreedom; }
        public double getPValue() { return pValue; }
        public long getTotalSamplesAnalyzed() { return totalSamplesAnalyzed; }
        public long getTotalSamplesExcluded() { return totalSamplesExcluded; }
        public boolean isApproximationReliable() { return isApproximationReliable; }

        /**
         * Plain-English educational explanation of what this statistical result indicates,
         * avoiding false certainty.
         *
         * @return cautious statistical assessment
         */
        public String getInterpretation() {
            if (eligiblePairs == 0) {
                return "Inapplicable: 0 eligible PoV pairs available for analysis (all pairs are empty or below threshold).";
            }
            if (!isApproximationReliable) {
                return String.format("Caution: Few eligible PoV pairs (m = %d < 10). The chi-square asymptotic approximation "
                        + "is unreliable. The reported p-value (%.4f) should be treated as approximate only.",
                        eligiblePairs, pValue);
            }
            if (pValue < 0.05) {
                return String.format("Rejects the equalized-PoV model (H0) at the 5%% level (p = %.6f, chi2 = %.4f, df = %d). "
                        + "The observed pair frequencies exhibit statistically significant asymmetry, typical of natural cover images with intact intensity gradients. "
                        + "Caveat: Low-payload or localized LSB embeddings may not perturb global frequencies enough to alter this result.",
                        pValue, chiSquareStatistic, degreesOfFreedom);
            } else {
                return String.format("Fails to reject the equalized-PoV model (H0) at the 5%% level (p = %.6f, chi2 = %.4f, df = %d). "
                        + "The observed pair frequencies do not significantly deviate from equality. "
                        + "While this pattern is consistent with full-spread LSB replacement steganography, it does NOT prove hidden data exists; "
                        + "flat areas, synthetic graphics, quantization, or specific natural textures can also produce balanced PoVs.",
                        pValue, chiSquareStatistic, degreesOfFreedom);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== Chi-Square Steganalysis for %s Channel ===\n", channel));
            sb.append(String.format("Total PoV Pairs       : %d (Eligible: %d, Sparse: %d, Empty: %d)\n",
                    totalPairs, eligiblePairs, sparsePairs, emptyPairs));
            sb.append(String.format("Threshold per Pair    : >= %d samples (Expected E >= %.1f)\n",
                    minPairCountThreshold, minPairCountThreshold / 2.0));
            sb.append(String.format("Samples Analyzed      : %d included, %d excluded (sparse)\n",
                    totalSamplesAnalyzed, totalSamplesExcluded));
            sb.append(String.format("Chi-Square Statistic  : %.4f\n", chiSquareStatistic));
            sb.append(String.format("Degrees of Freedom    : %d (df = m eligible pairs)\n", degreesOfFreedom));
            if (Double.isNaN(pValue)) {
                sb.append("p-value (H0 fit prob) : N/A (0 eligible pairs)\n");
            } else {
                sb.append(String.format("p-value (H0 fit prob) : %.6f%s\n",
                        pValue, isApproximationReliable ? "" : " (approximate: small sample)"));
            }
            sb.append("-------------------------------------------\n");
            sb.append(String.format("Statistical Assessment: %s\n", getInterpretation()));
            sb.append("Disclaimer: Chi-Square evaluates PoV equalization. Low-payload or localized embeddings may escape detection, "
                    + "while certain natural or pre-processed images can exhibit synthetic-like symmetry.");
            return sb.toString();
        }
    }

    /**
     * Analyzes the Blue channel of an image using the default minimum pair count threshold (5).
     *
     * @param image image to analyze
     * @return Result containing Chi-Square statistics and interpretation
     */
    public static Result analyze(BufferedImage image) {
        return analyze(image, Channel.BLUE, DEFAULT_MIN_PAIR_COUNT);
    }

    /**
     * Analyzes a specific color channel of an image using the default minimum pair count threshold (5).
     *
     * @param image   image to analyze
     * @param channel color channel (RED, GREEN, or BLUE)
     * @return Result containing Chi-Square statistics and interpretation
     */
    public static Result analyze(BufferedImage image, Channel channel) {
        return analyze(image, channel, DEFAULT_MIN_PAIR_COUNT);
    }

    /**
     * Analyzes a specific color channel of an image with a configurable pair count threshold.
     *
     * @param image         image to analyze
     * @param channel       color channel
     * @param minPairCount  minimum sample count per pair to include in calculation
     * @return Result containing Chi-Square statistics and interpretation
     */
    public static Result analyze(BufferedImage image, Channel channel, int minPairCount) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null.");
        }

        HistogramAnalyzer.ChannelHistogram hist = new HistogramAnalyzer.ChannelHistogram(image);
        int[] bins;
        switch (channel) {
            case RED: bins = hist.getRed(); break;
            case GREEN: bins = hist.getGreen(); break;
            case BLUE: bins = hist.getBlue(); break;
            default: throw new IllegalStateException("Unexpected channel: " + channel);
        }

        return analyzeHistogram(bins, channel, minPairCount);
    }

    /**
     * Analyzes a 256-bin histogram array directly.
     *
     * @param histogram    256-element array of intensity frequencies
     * @param channel      channel label for reporting
     * @param minPairCount minimum count per pair (>= 0)
     * @return Result containing Chi-Square statistics
     */
    public static Result analyzeHistogram(int[] histogram, Channel channel, int minPairCount) {
        if (histogram == null || histogram.length != 256) {
            throw new IllegalArgumentException("Histogram must be a non-null 256-element array.");
        }
        if (minPairCount < 0) {
            throw new IllegalArgumentException("Minimum pair count threshold must be non-negative.");
        }

        int totalPairs = 128;
        int eligiblePairs = 0;
        int emptyPairs = 0;
        int sparsePairs = 0;
        double chiSquare = 0.0;
        long totalSamplesAnalyzed = 0;
        long totalSamplesExcluded = 0;

        for (int k = 0; k < totalPairs; k++) {
            int n2k = histogram[2 * k];
            int n2k1 = histogram[2 * k + 1];
            int pairTotal = n2k + n2k1;

            if (pairTotal == 0) {
                emptyPairs++;
            } else if (pairTotal < minPairCount) {
                sparsePairs++;
                totalSamplesExcluded += pairTotal;
            } else {
                eligiblePairs++;
                totalSamplesAnalyzed += pairTotal;
                // Under H0, E_2k = E_2k+1 = pairTotal / 2.
                // chi^2_k = (n_2k - E)^2 / E + (n_2k+1 - E)^2 / E = (n_2k - n_2k+1)^2 / pairTotal
                double diff = n2k - n2k1;
                chiSquare += (diff * diff) / (double) pairTotal;
            }
        }

        // Each eligible pair has 1 internal constraint (sum = N_k), contributing 1 df.
        // Summing over m independent eligible pairs yields df = m.
        int df = eligiblePairs;
        double pValue;
        if (df == 0) {
            pValue = Double.NaN;
        } else {
            pValue = computePValue(chiSquare, df);
        }

        boolean isApproximationReliable = (eligiblePairs >= 10);

        return new Result(channel, totalPairs, eligiblePairs, emptyPairs, sparsePairs,
                minPairCount, chiSquare, df, pValue, totalSamplesAnalyzed, totalSamplesExcluded,
                isApproximationReliable);
    }

    /**
     * Numerically stable calculation of the Chi-Square p-value (upper-tail survival function):
     * <pre>
     * p = P(X >= chiSquare) = Q(df / 2, chiSquare / 2) = Gamma(df / 2, chiSquare / 2) / Gamma(df / 2)
     * </pre>
     *
     * <p>Uses Lanczos approximation for log-gamma and adaptive series expansion /
     * Lentz continued fractions for incomplete gamma, achieving 10^-15 numerical precision.
     *
     * @param chiSquare chi-square test statistic
     * @param df        degrees of freedom (must be > 0)
     * @return probability of observing a statistic >= chiSquare under H0, or Double.NaN if df <= 0
     */
    public static double computePValue(double chiSquare, int df) {
        if (df <= 0) return Double.NaN;
        if (chiSquare <= 0.0) return 1.0;

        double a = df / 2.0;
        double x = chiSquare / 2.0;

        if (x < a + 1.0) {
            // Series expansion for lower incomplete gamma P(a, x)
            double sum = 1.0 / a;
            double term = 1.0 / a;
            for (int n = 1; n < 200; n++) {
                term *= x / (a + n);
                sum += term;
                if (Math.abs(term) < Math.abs(sum) * 1e-15) break;
            }
            double logGammaA = logGamma(a);
            double logFactor = a * Math.log(x) - x - logGammaA;
            double p = sum * Math.exp(logFactor);
            return Math.max(0.0, Math.min(1.0, 1.0 - p));
        } else {
            // Continued fraction for upper incomplete gamma Q(a, x)
            double b = x + 1.0 - a;
            double c = 1.0 / 1e-30;
            double d = 1.0 / b;
            double h = d;
            for (int i = 1; i < 200; i++) {
                double an = -i * (i - a);
                b += 2.0;
                d = an * d + b;
                if (Math.abs(d) < 1e-30) d = 1e-30;
                c = b + an / c;
                if (Math.abs(c) < 1e-30) c = 1e-30;
                d = 1.0 / d;
                double del = d * c;
                h *= del;
                if (Math.abs(del - 1.0) < 1e-15) break;
            }
            double logGammaA = logGamma(a);
            double logFactor = a * Math.log(x) - x - logGammaA;
            double q = h * Math.exp(logFactor);
            return Math.max(0.0, Math.min(1.0, q));
        }
    }

    /**
     * Lanczos log-gamma approximation with g=7, N=9 coefficients (precision > 10^-15).
     */
    public static double logGamma(double x) {
        double[] c = {
            0.99999999999980993,
            676.52036812876921,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109583659915,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        };
        double z = x - 1.0;
        double sum = c[0];
        for (int i = 1; i < 9; i++) {
            sum += c[i] / (z + i);
        }
        double t = z + 7.5;
        return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(sum);
    }
}
