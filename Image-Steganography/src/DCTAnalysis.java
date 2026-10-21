import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Implements block-based 8x8 2D Discrete Cosine Transform (DCT-II) frequency analysis for digital images.
 *
 * <h3>Mathematical & Methodological Foundation:</h3>
 * <p>The Discrete Cosine Transform expresses finite sequences of spatial pixel data in terms of a sum of
 * cosine functions oscillating at different frequencies. In digital image processing, block-based 8x8 DCT
 * is the standard transformation used in JPEG compression and transform-domain steganalysis.</p>
 *
 * <h4>1. Centering & Block Partitioning:</h4>
 * <ul>
 *   <li>Pixel intensities in $[0, 255]$ are level-shifted by subtracting 128: $\tilde{f}(x, y) = f(x, y) - 128.0$.
 *       This centers the dynamic range around zero and aligns the DC coefficient with block brightness deviation.</li>
 *   <li>The image is partitioned into non-overlapping $8 \times 8$ blocks. For image dimensions not evenly
 *       divisible by 8 (or smaller than $8 \times 8$), boundary pixels are replicated (edge-clamped)
 *       to construct complete $8 \times 8$ blocks without introducing artificial boundary step-discontinuities.</li>
 * </ul>
 *
 * <h4>2. 2D Orthonormal DCT-II Formulation:</h4>
 * <p>For an $8 \times 8$ centered block $\tilde{f}(x, y)$ ($0 \le x, y < 8$), the 2D DCT coefficient $F(u, v)$ is:
 * <pre>
 *   F(u, v) = \frac{1}{4} C(u) C(v) \sum_{x=0}^{7} \sum_{y=0}^{7} \tilde{f}(x, y)
 *             \cos\left( \frac{(2x + 1) u \pi}{16} \right) \cos\left( \frac{(2y + 1) v \pi}{16} \right)
 * </pre>
 * where:
 * <pre>
 *   C(0) = 1 / \sqrt{2},   C(k) = 1  for k > 0
 * </pre>
 * Orthonormality ensures Parseval's energy conservation:
 * <pre>
 *   \sum_{u=0}^{7} \sum_{v=0}^{7} F(u, v)^2 = \sum_{x=0}^{7} \sum_{y=0}^{7} \tilde{f}(x, y)^2
 * </pre>
 * </p>
 *
 * <h4>3. Frequency Band Energy Decomposition:</h4>
 * <ul>
 *   <li><b>DC Coefficient ($F(0, 0)$):</b> Reflects average luminance/color baseline ($8 \times \text{mean}$).</li>
 *   <li><b>AC Coefficients ($F(u, v)$ for $(u, v) \neq (0, 0)$):</b> 63 frequency components per block.</li>
 *   <li><b>AC Energy:</b> $\sum_{(u, v) \neq (0, 0)} F(u, v)^2$.</li>
 *   <li><b>Low Frequency ($1 \le u + v \le 4$):</b> 14 coefficients capturing smooth structural shapes.</li>
 *   <li><b>Mid Frequency ($5 \le u + v \le 8$):</b> 25 coefficients capturing textures and moderate transitions.</li>
 *   <li><b>High Frequency ($9 \le u + v \le 14$):</b> 24 coefficients capturing fine edge detail and high-frequency noise.</li>
 * </ul>
 *
 * <h4>4. Steganalysis Context & Crucial Scientific Disclaimer:</h4>
 * <p><b>Descriptive Notice:</b> Our steganography engine embeds data via spatial-domain LSB replacement in uncompressed PNG images.
 * In spatial LSB embedding, pseudo-random bit substitution injects low-amplitude uncorrelated noise ($p' = p \pm 1$),
 * which subtly increases high-frequency AC energy. However, classical DCT steganalysis (e.g., histogram characteristic functions)
 * is designed specifically for JPEG images where DCT coefficients undergo quantization.
 * Therefore, DCT analysis here serves as a <i>supplementary descriptive and comparative tool</i>,
 * not as a definitive standalone detector for spatial PNG steganography.</p>
 */
public class DCTAnalysis {

    public enum Channel {
        RED,
        GREEN,
        BLUE,
        LUMINANCE
    }

    public static final int BLOCK_SIZE = 8;

    /** Orthonormal DCT-II 8x8 transformation matrix T: F = T * f * T' */
    private static final double[][] T = new double[BLOCK_SIZE][BLOCK_SIZE];

    static {
        // Precompute the 8x8 orthonormal DCT basis matrix
        for (int u = 0; u < BLOCK_SIZE; u++) {
            double cu = (u == 0) ? (1.0 / Math.sqrt(2.0)) : 1.0;
            for (int x = 0; x < BLOCK_SIZE; x++) {
                T[u][x] = 0.5 * cu * Math.cos(((2 * x + 1) * u * Math.PI) / 16.0);
            }
        }
    }

    /**
     * Holds the comprehensive frequency-domain analysis results.
     */
    public static class Result {
        private final Channel channel;
        private final int imageWidth;
        private final int imageHeight;
        private final int totalBlocks;
        private final int completeBlocks;
        private final int partialBlocks;

        private final double meanDC;
        private final double dcVariance;

        private final double totalACEnergy;
        private final double meanACEnergyPerBlock;

        private final double lowFreqEnergyPct;
        private final double midFreqEnergyPct;
        private final double highFreqEnergyPct;

        private final double nearZeroACPercentage; // % of AC coefficients with |F(u,v)| < 1.0
        private final String interpretation;

        public Result(Channel channel, int imageWidth, int imageHeight,
                      int totalBlocks, int completeBlocks, int partialBlocks,
                      double meanDC, double dcVariance,
                      double totalACEnergy, double meanACEnergyPerBlock,
                      double lowFreqEnergyPct, double midFreqEnergyPct, double highFreqEnergyPct,
                      double nearZeroACPercentage, String interpretation) {
            this.channel = channel;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.totalBlocks = totalBlocks;
            this.completeBlocks = completeBlocks;
            this.partialBlocks = partialBlocks;
            this.meanDC = meanDC;
            this.dcVariance = dcVariance;
            this.totalACEnergy = totalACEnergy;
            this.meanACEnergyPerBlock = meanACEnergyPerBlock;
            this.lowFreqEnergyPct = lowFreqEnergyPct;
            this.midFreqEnergyPct = midFreqEnergyPct;
            this.highFreqEnergyPct = highFreqEnergyPct;
            this.nearZeroACPercentage = nearZeroACPercentage;
            this.interpretation = interpretation;
        }

        public Channel getChannel() { return channel; }
        public int getImageWidth() { return imageWidth; }
        public int getImageHeight() { return imageHeight; }
        public int getTotalBlocks() { return totalBlocks; }
        public int getCompleteBlocks() { return completeBlocks; }
        public int getPartialBlocks() { return partialBlocks; }

        public double getMeanDC() { return meanDC; }
        public double getDcVariance() { return dcVariance; }
        public double getTotalACEnergy() { return totalACEnergy; }
        public double getMeanACEnergyPerBlock() { return meanACEnergyPerBlock; }

        public double getLowFreqEnergyPct() { return lowFreqEnergyPct; }
        public double getMidFreqEnergyPct() { return midFreqEnergyPct; }
        public double getHighFreqEnergyPct() { return highFreqEnergyPct; }

        public double getNearZeroACPercentage() { return nearZeroACPercentage; }
        public String getInterpretation() { return interpretation; }
    }

    /**
     * Computes the 2D 8x8 DCT-II on an input 8x8 matrix using separable orthonormal matrix multiplication:
     * F = T * block * T^T.
     *
     * @param block 8x8 double matrix (centered or raw)
     * @return 8x8 double matrix of DCT coefficients F(u, v)
     */
    public static double[][] compute8x8DCT(double[][] block) {
        if (block == null || block.length != BLOCK_SIZE || block[0].length != BLOCK_SIZE) {
            throw new IllegalArgumentException("Block must be exactly 8x8.");
        }

        // temp = T * block
        double[][] temp = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int u = 0; u < BLOCK_SIZE; u++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {
                double sum = 0.0;
                for (int x = 0; x < BLOCK_SIZE; x++) {
                    sum += T[u][x] * block[x][y];
                }
                temp[u][y] = sum;
            }
        }

        // F = temp * T^T
        double[][] F = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int u = 0; u < BLOCK_SIZE; u++) {
            for (int v = 0; v < BLOCK_SIZE; v++) {
                double sum = 0.0;
                for (int y = 0; y < BLOCK_SIZE; y++) {
                    sum += temp[u][y] * T[v][y];
                }
                F[u][v] = sum;
            }
        }

        return F;
    }

    /**
     * Performs DCT frequency analysis on the default embedding channel (Blue) of an image.
     */
    public static Result analyze(BufferedImage image) {
        return analyze(image, Channel.BLUE);
    }

    /**
     * Performs DCT frequency analysis on a specified channel of an image.
     *
     * @param image   the BufferedImage to analyze
     * @param channel the color channel (RED, GREEN, BLUE, or LUMINANCE)
     * @return Result object containing complete frequency-domain metrics
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

        int blocksX = (width + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int blocksY = (height + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int totalBlocks = blocksX * blocksY;

        if (totalBlocks == 0) {
            return new Result(channel, width, height, 0, 0, 0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    "Empty image: no blocks available for DCT analysis.");
        }

        int completeBlocks = 0;
        int partialBlocks = 0;

        double sumDC = 0.0;
        double sumDCSq = 0.0;

        double totalACEnergy = 0.0;
        double lowFreqEnergy = 0.0;
        double midFreqEnergy = 0.0;
        double highFreqEnergy = 0.0;

        long totalACCoeffs = (long) totalBlocks * 63;
        long nearZeroACCount = 0;

        double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                boolean isComplete = true;

                // Populate 8x8 block with centering by -128.0, with edge clamping for incomplete blocks
                for (int y = 0; y < BLOCK_SIZE; y++) {
                    int imgY = by * BLOCK_SIZE + y;
                    if (imgY >= height) {
                        imgY = height - 1;
                        isComplete = false;
                    }

                    for (int x = 0; x < BLOCK_SIZE; x++) {
                        int imgX = bx * BLOCK_SIZE + x;
                        if (imgX >= width) {
                            imgX = width - 1;
                            isComplete = false;
                        }

                        int rgb = image.getRGB(imgX, imgY);
                        double val;
                        switch (channel) {
                            case RED:
                                val = (rgb >> 16) & 0xFF;
                                break;
                            case GREEN:
                                val = (rgb >> 8) & 0xFF;
                                break;
                            case LUMINANCE:
                                int r = (rgb >> 16) & 0xFF;
                                int g = (rgb >> 8) & 0xFF;
                                int b = rgb & 0xFF;
                                val = 0.299 * r + 0.587 * g + 0.114 * b;
                                break;
                            case BLUE:
                            default:
                                val = rgb & 0xFF;
                                break;
                        }
                        block[y][x] = val - 128.0; // Level shift centering
                    }
                }

                if (isComplete) completeBlocks++;
                else partialBlocks++;

                // Compute 2D DCT for this block
                double[][] F = compute8x8DCT(block);

                // DC analysis
                double dc = F[0][0];
                sumDC += dc;
                sumDCSq += dc * dc;

                // AC frequency bands and energy
                for (int u = 0; u < BLOCK_SIZE; u++) {
                    for (int v = 0; v < BLOCK_SIZE; v++) {
                        if (u == 0 && v == 0) continue; // Skip DC

                        double coeff = F[u][v];
                        double coeffEnergy = coeff * coeff;
                        totalACEnergy += coeffEnergy;

                        int freqDist = u + v;
                        if (freqDist <= 4) {
                            lowFreqEnergy += coeffEnergy;
                        } else if (freqDist <= 8) {
                            midFreqEnergy += coeffEnergy;
                        } else {
                            highFreqEnergy += coeffEnergy;
                        }

                        if (Math.abs(coeff) < 1.0) {
                            nearZeroACCount++;
                        }
                    }
                }
            }
        }

        double meanDC = sumDC / totalBlocks;
        double dcVariance = (sumDCSq / totalBlocks) - (meanDC * meanDC);
        if (dcVariance < 0) dcVariance = 0.0;

        double meanACEnergyPerBlock = totalACEnergy / totalBlocks;

        double lowPct = totalACEnergy > 0 ? (lowFreqEnergy * 100.0 / totalACEnergy) : 0.0;
        double midPct = totalACEnergy > 0 ? (midFreqEnergy * 100.0 / totalACEnergy) : 0.0;
        double highPct = totalACEnergy > 0 ? (highFreqEnergy * 100.0 / totalACEnergy) : 0.0;

        double nearZeroPct = totalACCoeffs > 0 ? (nearZeroACCount * 100.0 / totalACCoeffs) : 0.0;

        String interpretation = generateInterpretation(totalBlocks, meanACEnergyPerBlock, highPct, nearZeroPct);

        return new Result(channel, width, height, totalBlocks, completeBlocks, partialBlocks,
                meanDC, dcVariance, totalACEnergy, meanACEnergyPerBlock,
                lowPct, midPct, highPct, nearZeroPct, interpretation);
    }

    private static String generateInterpretation(int totalBlocks, double meanACEnergy, double highPct, double nearZeroPct) {
        StringBuilder sb = new StringBuilder();
        if (meanACEnergy < 1.0) {
            sb.append("Extremely flat/smooth image with negligible AC frequency energy. ");
        } else if (highPct > 15.0) {
            sb.append(String.format("High AC energy fraction (%.1f%% high-frequency). Image possesses fine textures, noise, or high-frequency disturbances. ", highPct));
        } else {
            sb.append(String.format("Typical natural image spectrum: energy concentrated in low frequencies (high-freq: %.1f%%, sparsity: %.1f%% near-zero). ", highPct, nearZeroPct));
        }
        sb.append("[Descriptive frequency profile; spatial LSB replacement does not alter quantized DCT coefficients like JPEG stego does]");
        return sb.toString();
    }

    /**
     * Loads an image file from disk and performs DCT frequency analysis.
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
