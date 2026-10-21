import java.awt.image.BufferedImage;

/**
 * Calculates the Mean Absolute Error (MAE) between two images.
 *
 * <p>Formula:
 * <pre>
 * MAE = sum of absolute RGB channel differences / (W * H * 3)
 * </pre>
 *
 * <p>An identical pair of images produces MAE = 0.0.
 */
public class MAECalculator {

    /**
     * Calculates MAE between two BufferedImages across RGB channels.
     *
     * @param img1 first image (e.g. cover)
     * @param img2 second image (e.g. stego)
     * @return Mean Absolute Error across RGB channels
     * @throws IllegalArgumentException    if either image is null
     * @throws DimensionMismatchException if dimensions do not match
     */
    public static double calculate(BufferedImage img1, BufferedImage img2) {
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

        long totalPixels = (long) w1 * h1;
        if (totalPixels == 0) {
            return 0.0;
        }

        long sumAbsDiff = 0;

        for (int y = 0; y < h1; y++) {
            for (int x = 0; x < w1; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);

                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;

                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;

                sumAbsDiff += Math.abs(r1 - r2)
                            + Math.abs(g1 - g2)
                            + Math.abs(b1 - b2);
            }
        }

        long totalChannelSamples = totalPixels * 3L;
        return (double) sumAbsDiff / (double) totalChannelSamples;
    }
}
