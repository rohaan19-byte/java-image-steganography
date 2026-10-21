import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Generates an amplified visual difference image comparing an original cover image
 * with a steganographic image.
 *
 * <p>Differences in RGB channels are multiplied by a configurable scale factor and clamped
 * to the range 0–255. Dimensions are strictly preserved.
 */
public class DifferenceImageGenerator {

    /**
     * Default amplification scale factor (255 maps a 1-bit difference directly to maximum intensity).
     */
    public static final int DEFAULT_SCALE_FACTOR = 255;

    /**
     * Generates a difference image using the default scale factor (255).
     *
     * @param original original cover image
     * @param stego    stego image
     * @return new BufferedImage showing amplified differences
     */
    public static BufferedImage generate(BufferedImage original, BufferedImage stego) {
        return generate(original, stego, DEFAULT_SCALE_FACTOR);
    }

    /**
     * Generates a difference image using a custom amplification scale factor.
     *
     * @param original    original cover image
     * @param stego       stego image
     * @param scaleFactor multiplier for pixel difference values (must be >= 1)
     * @return new BufferedImage containing amplified channel differences
     * @throws IllegalArgumentException    if images are null or scale factor < 1
     * @throws DimensionMismatchException if image dimensions do not match
     */
    public static BufferedImage generate(BufferedImage original, BufferedImage stego, int scaleFactor) {
        if (original == null || stego == null) {
            throw new IllegalArgumentException("Original and stego images cannot be null.");
        }
        if (scaleFactor < 1) {
            throw new IllegalArgumentException("Scale factor must be at least 1, got: " + scaleFactor);
        }

        int w1 = original.getWidth();
        int h1 = original.getHeight();
        int w2 = stego.getWidth();
        int h2 = stego.getHeight();

        if (w1 != w2 || h1 != h2) {
            throw new DimensionMismatchException(w1, h1, w2, h2);
        }

        BufferedImage diffImage = new BufferedImage(w1, h1, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h1; y++) {
            for (int x = 0; x < w1; x++) {
                int rgb1 = original.getRGB(x, y);
                int rgb2 = stego.getRGB(x, y);

                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;

                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;

                int diffR = Math.min(255, Math.abs(r1 - r2) * scaleFactor);
                int diffG = Math.min(255, Math.abs(g1 - g2) * scaleFactor);
                int diffB = Math.min(255, Math.abs(b1 - b2) * scaleFactor);

                int diffRgb = (diffR << 16) | (diffG << 8) | diffB;
                diffImage.setRGB(x, y, diffRgb);
            }
        }

        return diffImage;
    }

    /**
     * Generates and writes the difference image to a PNG file.
     *
     * @param original    original cover image
     * @param stego       stego image
     * @param outputFile  destination file for the difference PNG
     * @param scaleFactor amplification scale factor
     * @throws IOException if saving the image fails
     */
    public static void saveDifferenceImage(BufferedImage original, BufferedImage stego, File outputFile, int scaleFactor) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        BufferedImage diff = generate(original, stego, scaleFactor);

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        boolean written = ImageIO.write(diff, "png", outputFile);
        if (!written) {
            throw new IOException("Failed to save difference image: No PNG ImageIO writer found.");
        }
    }
}
