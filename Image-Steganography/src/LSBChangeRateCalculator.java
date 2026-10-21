import java.awt.image.BufferedImage;

/**
 * Calculates the Blue-channel Least Significant Bit (LSB) change rate between two images.
 *
 * <p>Because Phase 1 embeds 1 bit per pixel in the Blue-channel LSB, this metric
 * tracks the precise ratio and count of Blue-channel LSBs that were altered.
 *
 * <p>Formula:
 * <pre>
 * Blue-Channel LSB Change Rate = (changed blue LSBs / total compared blue LSBs) * 100
 * </pre>
 */
public class LSBChangeRateCalculator {

    /**
     * Holds the detailed result of the Blue-channel LSB comparison.
     */
    public static class Result {
        private final long changedBits;
        private final long totalBits;
        private final double changeRatePercent;

        public Result(long changedBits, long totalBits, double changeRatePercent) {
            this.changedBits = changedBits;
            this.totalBits = totalBits;
            this.changeRatePercent = changeRatePercent;
        }

        public long getChangedBits() {
            return changedBits;
        }

        public long getTotalBits() {
            return totalBits;
        }

        public double getChangeRatePercent() {
            return changeRatePercent;
        }

        @Override
        public String toString() {
            return String.format("Blue-channel LSB change rate: %.4f%% (%d of %d bits changed)",
                    changeRatePercent, changedBits, totalBits);
        }
    }

    /**
     * Calculates the Blue-channel LSB change rate between two images.
     *
     * @param img1 cover image
     * @param img2 stego image
     * @return Result containing changed count, total count, and percentage
     * @throws IllegalArgumentException    if either image is null
     * @throws DimensionMismatchException if dimensions do not match
     */
    public static Result calculate(BufferedImage img1, BufferedImage img2) {
        if (img1 == null || img2 == null) {
            throw new IllegalArgumentException("Images cannot be null.");
        }
        int w1 = img1.getWidth();
        int h1 = img1.getHeight();
        int w2 = img2.getWidth();
        int h2 = img2.getHeight();

        if (w1 != w2 || h1 != h2) {
            throw new DimensionMismatchException(w1, h1, w2, h2);
        }

        long totalBits = (long) w1 * h1;
        if (totalBits == 0) {
            return new Result(0, 0, 0.0);
        }

        long changedBits = 0;

        for (int y = 0; y < h1; y++) {
            for (int x = 0; x < w1; x++) {
                int b1 = img1.getRGB(x, y) & 1;
                int b2 = img2.getRGB(x, y) & 1;
                if (b1 != b2) {
                    changedBits++;
                }
            }
        }

        double changeRatePercent = (changedBits / (double) totalBits) * 100.0;
        return new Result(changedBits, totalBits, changeRatePercent);
    }
}
