import java.awt.image.BufferedImage;

/**
 * Calculates the Mean Squared Error (MSE) between two images.
 *
 * <p>Formula:
 * <pre>
 * MSE = sum of squared RGB channel differences / (W * H * 3)
 * </pre>
 *
 * <p>Each RGB channel value is evaluated in the standard 0–255 range.
 * An identical pair of images produces MSE = 0.0.
 */
public class MSECalculator {

    /**
     * Calculates MSE between two BufferedImages.
     *
     * @param img1 first image (e.g. cover image)
     * @param img2 second image (e.g. stego image)
     * @return Mean Squared Error across RGB channels
     * @throws IllegalArgumentException    if either image is null
     * @throws DimensionMismatchException if image dimensions do not match
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

        long sumSquaredDiff = 0;

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

                int diffR = r1 - r2;
                int diffG = g1 - g2;
                int diffB = b1 - b2;

                sumSquaredDiff += (long) diffR * diffR
                                + (long) diffG * diffG
                                + (long) diffB * diffB;
            }
        }

        long totalChannelSamples = totalPixels * 3L;
        return (double) sumSquaredDiff / (double) totalChannelSamples;
    }
}
