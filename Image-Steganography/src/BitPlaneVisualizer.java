import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Visualizes individual bit planes (0 through 7) for Red, Green, and Blue channels.
 *
 * <p>Bit extraction formula:
 * <pre>
 * bitValue = (channelValue >> planeIndex) & 1
 * </pre>
 *
 * <p>Bit 0 maps to Black (RGB 0,0,0) and Bit 1 maps to White (RGB 255,255,255).
 * Dimensions are strictly preserved.
 */
public class BitPlaneVisualizer {

    /**
     * Color channels supported for bit-plane slicing.
     */
    public enum Channel {
        RED,
        GREEN,
        BLUE
    }

    /**
     * Extracts a single bit-plane visualization image.
     *
     * @param image      source image
     * @param channel    color channel to extract (RED, GREEN, or BLUE)
     * @param planeIndex bit plane index (0 = LSB, 7 = MSB)
     * @return binary (black and white) BufferedImage of the specified bit-plane
     * @throws IllegalArgumentException if image is null, channel is null, or planeIndex is not 0..7
     */
    public static BufferedImage extractPlane(BufferedImage image, Channel channel, int planeIndex) {
        if (image == null) {
            throw new IllegalArgumentException("Source image cannot be null.");
        }
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null.");
        }
        if (planeIndex < 0 || planeIndex > 7) {
            throw new IllegalArgumentException("Invalid bit-plane index: " + planeIndex
                    + ". Must be between 0 (LSB) and 7 (MSB).");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage planeImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

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

                int bit = (channelVal >> planeIndex) & 1;
                int pixel = bit == 1 ? 0xFFFFFFFF : 0xFF000000; // 1 -> White, 0 -> Black
                planeImg.setRGB(x, y, pixel);
            }
        }

        return planeImg;
    }

    /**
     * Extracts all 8 bit planes (0..7) for a given channel.
     *
     * @param image   source image
     * @param channel color channel
     * @return array of 8 BufferedImages (index 0 = plane 0, ..., index 7 = plane 7)
     */
    public static BufferedImage[] extractAllPlanes(BufferedImage image, Channel channel) {
        BufferedImage[] planes = new BufferedImage[8];
        for (int i = 0; i < 8; i++) {
            planes[i] = extractPlane(image, channel, i);
        }
        return planes;
    }

    /**
     * Generates a side-by-side comparison image of the same bit-plane from original and stego images.
     *
     * @param original   cover image
     * @param stego      stego image
     * @param channel    color channel
     * @param planeIndex bit-plane index (0..7)
     * @return side-by-side comparison BufferedImage
     */
    public static BufferedImage generateComparison(BufferedImage original, BufferedImage stego,
                                                   Channel channel, int planeIndex) {
        if (original == null || stego == null) {
            throw new IllegalArgumentException("Images cannot be null.");
        }
        if (original.getWidth() != stego.getWidth() || original.getHeight() != stego.getHeight()) {
            throw new DimensionMismatchException(original.getWidth(), original.getHeight(),
                    stego.getWidth(), stego.getHeight());
        }

        BufferedImage origPlane = extractPlane(original, channel, planeIndex);
        BufferedImage stegoPlane = extractPlane(stego, channel, planeIndex);

        int w = original.getWidth();
        int h = original.getHeight();
        int headerHeight = 30;
        int dividerWidth = 10;
        int compWidth = (w * 2) + dividerWidth;
        int compHeight = h + headerHeight;

        BufferedImage comp = new BufferedImage(compWidth, compHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = comp.createGraphics();

        try {
            // Header bar
            g.setColor(new Color(30, 30, 35));
            g.fillRect(0, 0, compWidth, compHeight);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(String.format("Original Cover [%s Plane %d]", channel, planeIndex), 10, 20);
            g.drawString(String.format("Stego Image [%s Plane %d]", channel, planeIndex), w + dividerWidth + 10, 20);

            // Draw bit planes
            g.drawImage(origPlane, 0, headerHeight, null);
            g.drawImage(stegoPlane, w + dividerWidth, headerHeight, null);

            // Divider line
            g.setColor(new Color(80, 80, 90));
            g.fillRect(w, headerHeight, dividerWidth, h);

        } finally {
            g.dispose();
        }

        return comp;
    }

    /**
     * Saves a bit-plane visualization to a PNG file.
     *
     * @param image      source image
     * @param channel    color channel
     * @param planeIndex bit-plane index (0..7)
     * @param outputFile destination PNG file
     * @throws IOException if saving fails
     */
    public static void saveBitPlane(BufferedImage image, Channel channel, int planeIndex, File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        BufferedImage plane = extractPlane(image, channel, planeIndex);

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        boolean written = ImageIO.write(plane, "png", outputFile);
        if (!written) {
            throw new IOException("Failed to save bit-plane image: No PNG ImageIO writer found.");
        }
    }

    /**
     * Saves a side-by-side bit-plane comparison to a PNG file.
     *
     * @param original   cover image
     * @param stego      stego image
     * @param channel    color channel
     * @param planeIndex bit-plane index (0..7)
     * @param outputFile destination PNG file
     * @throws IOException if saving fails
     */
    public static void saveComparison(BufferedImage original, BufferedImage stego,
                                      Channel channel, int planeIndex, File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        BufferedImage comp = generateComparison(original, stego, channel, planeIndex);

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        boolean written = ImageIO.write(comp, "png", outputFile);
        if (!written) {
            throw new IOException("Failed to save bit-plane comparison image: No PNG ImageIO writer found.");
        }
    }
}
