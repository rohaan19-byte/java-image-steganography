import java.awt.image.BufferedImage;

/**
 * Performs Least Significant Bit (LSB) statistical analysis on digital images.
 *
 * <p>Focuses primarily on the Blue channel (the channel used for Phase 1 LSB embedding),
 * tracking:
 * <ul>
 *   <li>Total pixel count</li>
 *   <li>Counts and percentages of LSB 0s and 1s</li>
 *   <li>Row-major sequential transition counts and transition rate</li>
 *   <li>Detailed transition matrix (0->0, 0->1, 1->0, 1->1)</li>
 *   <li>Consecutive bit run statistics (total runs, average run length, max run length)</li>
 * </ul>
 *
 * <p><b>Interpretation Guidelines:</b>
 * Natural images often exhibit spatial correlation that lowers transition rates below 50%
 * and creates runs of identical bits. Encrypted/compressed steganographic payloads approach
 * a Bernoulli(0.5) distribution (50% zeros, 50% ones, ~50% transition rate).
 * However, statistical balance alone does <i>not</i> prove steganography, nor does imbalance
 * prove an image is clean.
 */
public class LSBStatisticsAnalyzer {

    /**
     * Color channels available for LSB statistical analysis.
     */
    public enum Channel {
        RED,
        GREEN,
        BLUE
    }

    /**
     * Holds comprehensive LSB statistical results for an analyzed color channel.
     */
    public static class Result {
        private final Channel channel;
        private final long totalPixels;
        private final long zerosCount;
        private final long onesCount;
        private final double zeroPercentage;
        private final double onePercentage;

        private final long totalTransitions;
        private final long possibleTransitions;
        private final double transitionRatePercent;

        private final long trans00;
        private final long trans01;
        private final long trans10;
        private final long trans11;

        private final long totalRuns;
        private final double averageRunLength;
        private final long maxRunLength;

        public Result(Channel channel, long totalPixels, long zerosCount, long onesCount,
                      double zeroPercentage, double onePercentage,
                      long totalTransitions, long possibleTransitions, double transitionRatePercent,
                      long trans00, long trans01, long trans10, long trans11,
                      long totalRuns, double averageRunLength, long maxRunLength) {
            this.channel = channel;
            this.totalPixels = totalPixels;
            this.zerosCount = zerosCount;
            this.onesCount = onesCount;
            this.zeroPercentage = zeroPercentage;
            this.onePercentage = onePercentage;
            this.totalTransitions = totalTransitions;
            this.possibleTransitions = possibleTransitions;
            this.transitionRatePercent = transitionRatePercent;
            this.trans00 = trans00;
            this.trans01 = trans01;
            this.trans10 = trans10;
            this.trans11 = trans11;
            this.totalRuns = totalRuns;
            this.averageRunLength = averageRunLength;
            this.maxRunLength = maxRunLength;
        }

        public Channel getChannel() { return channel; }
        public long getTotalPixels() { return totalPixels; }
        public long getZerosCount() { return zerosCount; }
        public long getOnesCount() { return onesCount; }
        public double getZeroPercentage() { return zeroPercentage; }
        public double getOnePercentage() { return onePercentage; }
        public long getTotalTransitions() { return totalTransitions; }
        public long getPossibleTransitions() { return possibleTransitions; }
        public double getTransitionRatePercent() { return transitionRatePercent; }
        public long getTrans00() { return trans00; }
        public long getTrans01() { return trans01; }
        public long getTrans10() { return trans10; }
        public long getTrans11() { return trans11; }
        public long getTotalRuns() { return totalRuns; }
        public double getAverageRunLength() { return averageRunLength; }
        public long getMaxRunLength() { return maxRunLength; }

        /**
         * Evaluates whether the LSB bit distribution is balanced (close to 50%).
         *
         * @return descriptive summary of distribution balance
         */
        public String getBalanceAssessment() {
            double diff = Math.abs(zeroPercentage - 50.0);
            if (diff < 1.0) {
                return String.format("Highly Balanced (deviation from 50%% is %.2f%%)", diff);
            } else if (diff < 5.0) {
                return String.format("Moderately Balanced (deviation from 50%% is %.2f%%)", diff);
            } else {
                return String.format("Imbalanced (deviation from 50%% is %.2f%%)", diff);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== LSB Statistics for %s Channel ===\n", channel));
            sb.append(String.format("Total Pixels Analyzed : %d\n", totalPixels));
            sb.append(String.format("LSB '0' Count         : %d (%.4f%%)\n", zerosCount, zeroPercentage));
            sb.append(String.format("LSB '1' Count         : %d (%.4f%%)\n", onesCount, onePercentage));
            sb.append(String.format("Distribution Balance  : %s\n", getBalanceAssessment()));
            sb.append("-------------------------------------------\n");
            sb.append(String.format("Sequential Transitions: %d of %d (%.4f%%)\n",
                    totalTransitions, possibleTransitions, transitionRatePercent));
            sb.append(String.format("Transition Breakdown  : 0->0: %d | 0->1: %d | 1->0: %d | 1->1: %d\n",
                    trans00, trans01, trans10, trans11));
            sb.append(String.format("Run Statistics        : Total Runs: %d, Avg Length: %.2f, Max Run: %d\n",
                    totalRuns, averageRunLength, maxRunLength));
            sb.append("Note: Statistical balance is expected in random payloads, but natural images also vary. "
                    + "This metric should not be used in isolation as proof of steganography.");
            return sb.toString();
        }
    }

    /**
     * Analyzes LSB statistics for the Blue channel of the given image.
     *
     * @param image the image to analyze
     * @return Result containing comprehensive LSB statistics
     */
    public static Result analyze(BufferedImage image) {
        return analyze(image, Channel.BLUE);
    }

    /**
     * Analyzes LSB statistics for a specified channel in row-major scan order.
     *
     * @param image   the image to analyze
     * @param channel the color channel (RED, GREEN, or BLUE)
     * @return Result containing comprehensive LSB statistics
     * @throws IllegalArgumentException if image or channel is null
     */
    public static Result analyze(BufferedImage image, Channel channel) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        long totalPixels = (long) width * height;

        if (totalPixels == 0) {
            return new Result(channel, 0, 0, 0, 0.0, 0.0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0.0, 0);
        }

        long zerosCount = 0;
        long onesCount = 0;

        long trans00 = 0;
        long trans01 = 0;
        long trans10 = 0;
        long trans11 = 0;

        int prevBit = -1;
        long currentRunLength = 0;
        long totalRuns = 0;
        long maxRunLength = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int channelVal;

                switch (channel) {
                    case RED:
                        channelVal = (rgb >> 16) & 0xFF;
                        break;
                    case GREEN:
                        channelVal = (rgb >> 8) & 0xFF;
                        break;
                    case BLUE:
                        channelVal = rgb & 0xFF;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected channel: " + channel);
                }

                int lsb = channelVal & 1;

                if (lsb == 0) {
                    zerosCount++;
                } else {
                    onesCount++;
                }

                if (prevBit != -1) {
                    if (prevBit == 0 && lsb == 0) trans00++;
                    else if (prevBit == 0 && lsb == 1) trans01++;
                    else if (prevBit == 1 && lsb == 0) trans10++;
                    else if (prevBit == 1 && lsb == 1) trans11++;

                    if (lsb == prevBit) {
                        currentRunLength++;
                    } else {
                        totalRuns++;
                        if (currentRunLength > maxRunLength) {
                            maxRunLength = currentRunLength;
                        }
                        currentRunLength = 1;
                    }
                } else {
                    // First bit encountered
                    currentRunLength = 1;
                }

                prevBit = lsb;
            }
        }

        // Account for final run
        if (currentRunLength > 0) {
            totalRuns++;
            if (currentRunLength > maxRunLength) {
                maxRunLength = currentRunLength;
            }
        }

        double zeroPercentage = (zerosCount / (double) totalPixels) * 100.0;
        double onePercentage = (onesCount / (double) totalPixels) * 100.0;

        long totalTransitions = trans01 + trans10;
        long possibleTransitions = totalPixels > 1 ? totalPixels - 1 : 0;
        double transitionRatePercent = possibleTransitions > 0
                ? (totalTransitions / (double) possibleTransitions) * 100.0 : 0.0;

        double averageRunLength = totalRuns > 0 ? (totalPixels / (double) totalRuns) : 0.0;

        return new Result(channel, totalPixels, zerosCount, onesCount,
                zeroPercentage, onePercentage,
                totalTransitions, possibleTransitions, transitionRatePercent,
                trans00, trans01, trans10, trans11,
                totalRuns, averageRunLength, maxRunLength);
    }
}
