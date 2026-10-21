import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Core engine for Least Significant Bit (LSB) steganographic embedding.
 *
 * <p>Framing format:
 * <ul>
 *   <li>Bytes 0..3: Magic header {@code "STEG"} (0x53, 0x54, 0x45, 0x47)</li>
 *   <li>Bytes 4..7: Payload length (32-bit signed/unsigned integer, big-endian)</li>
 *   <li>Bytes 8..N: Actual payload bytes</li>
 * </ul>
 *
 * <p>Bits are embedded 1 bit per pixel into the Blue channel LSB in row-major
 * raster order (x = pixelIndex % width, y = pixelIndex / width), Most Significant
 * Bit (MSB, bit 7) first.
 */
public class EmbedLSB {

    public static final byte[] MAGIC_HEADER = new byte[] { 'S', 'T', 'E', 'G' };
    public static final int HEADER_BYTES = 8; // 4 bytes magic + 4 bytes length

    /**
     * Calculates the maximum usable payload capacity in bytes for given image dimensions
     * using Blue-channel LSB embedding.
     *
     * @param width  image width in pixels
     * @param height image height in pixels
     * @return maximum usable payload capacity in bytes (excluding framing overhead)
     */
    public static long calculateMaxPayloadBytes(int width, int height) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        long totalPixels = (long) width * (long) height;
        long totalBytes = totalPixels / 8;
        return totalBytes >= HEADER_BYTES ? totalBytes - HEADER_BYTES : 0;
    }

    /**
     * Calculates the maximum usable payload capacity in bytes for a BufferedImage.
     *
     * @param image the BufferedImage to evaluate
     * @return maximum usable payload capacity in bytes
     */
    public static long calculateMaxPayloadBytes(BufferedImage image) {
        if (image == null) {
            return 0;
        }
        return calculateMaxPayloadBytes(image.getWidth(), image.getHeight());
    }

    /**
     * Calculates the maximum usable payload capacity in bytes for an image file.
     *
     * @param imageFile the cover image file
     * @return maximum usable payload capacity in bytes
     * @throws IOException if reading the image fails
     */
    public static long calculateMaxPayloadBytes(File imageFile) throws IOException {
        if (imageFile == null || !imageFile.exists() || !imageFile.isFile()) {
            throw new FileNotFoundException("Cover image file not found: " + (imageFile == null ? "null" : imageFile.getPath()));
        }
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IllegalArgumentException("Unsupported or corrupted image file: " + imageFile.getPath());
        }
        return calculateMaxPayloadBytes(image);
    }

    /**
     * Embeds an arbitrary binary payload into a cover BufferedImage.
     * The original cover image is not modified; a modified copy is returned.
     *
     * @param coverImage the source image
     * @param payload    the binary data to embed (may be empty or contain arbitrary bytes)
     * @return a new BufferedImage containing the framed steganographic payload
     * @throws InsufficientCapacityException if payload exceeds image capacity
     * @throws IllegalArgumentException      if coverImage is null
     */
    public static BufferedImage embed(BufferedImage coverImage, byte[] payload) {
        if (coverImage == null) {
            throw new IllegalArgumentException("Cover image cannot be null.");
        }
        if (payload == null) {
            payload = new byte[0];
        }

        long maxCapacity = calculateMaxPayloadBytes(coverImage);
        if (payload.length > maxCapacity) {
            throw new InsufficientCapacityException(payload.length, maxCapacity);
        }

        // Create framed payload: 4-byte magic + 4-byte big-endian length + payload
        int payloadLength = payload.length;
        byte[] frame = new byte[HEADER_BYTES + payloadLength];
        frame[0] = MAGIC_HEADER[0];
        frame[1] = MAGIC_HEADER[1];
        frame[2] = MAGIC_HEADER[2];
        frame[3] = MAGIC_HEADER[3];
        frame[4] = (byte) ((payloadLength >>> 24) & 0xFF);
        frame[5] = (byte) ((payloadLength >>> 16) & 0xFF);
        frame[6] = (byte) ((payloadLength >>> 8) & 0xFF);
        frame[7] = (byte) (payloadLength & 0xFF);
        System.arraycopy(payload, 0, frame, HEADER_BYTES, payloadLength);

        // Copy image to preserve original and support alpha
        BufferedImage stegoImage = copyImage(coverImage);
        int width = stegoImage.getWidth();

        // Embed bitwise into Blue-channel LSB in row-major order (MSB first)
        int totalFrameBytes = frame.length;
        for (int i = 0; i < totalFrameBytes; i++) {
            int b = frame[i] & 0xFF;
            for (int bit = 7; bit >= 0; bit--) {
                int bitVal = (b >>> bit) & 1;
                int pixelIndex = i * 8 + (7 - bit);
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int rgb = stegoImage.getRGB(x, y);
                int newRgb = (rgb & ~1) | bitVal;
                stegoImage.setRGB(x, y, newRgb);
            }
        }

        return stegoImage;
    }

    /**
     * Embeds a binary payload into a cover image file and saves the output as PNG.
     *
     * @param inputFile  the input cover image file
     * @param payload    the payload bytes to embed
     * @param outputFile the output file where the stego PNG image will be written
     * @throws IOException                    if file I/O fails
     * @throws InsufficientCapacityException if the payload exceeds cover capacity
     * @throws IllegalArgumentException      if paths are invalid or input equals output
     */
    public static void embed(File inputFile, byte[] payload, File outputFile) throws IOException {
        if (inputFile == null || !inputFile.exists() || !inputFile.isFile()) {
            throw new FileNotFoundException("Input cover image file not found: " + (inputFile == null ? "null" : inputFile.getPath()));
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        if (outputFile.exists() && outputFile.getCanonicalPath().equalsIgnoreCase(inputFile.getCanonicalPath())) {
            throw new IllegalArgumentException("Output file cannot be the same as input cover image file to prevent accidental overwrite.");
        }

        BufferedImage coverImage = ImageIO.read(inputFile);
        if (coverImage == null) {
            throw new IllegalArgumentException("Failed to decode cover image: file is corrupted or unsupported: " + inputFile.getPath());
        }

        // Embed payload (validates capacity before any disk modifications)
        BufferedImage stegoImage = embed(coverImage, payload);

        // Ensure parent directory exists
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Save strictly as PNG to prevent lossy compression from corrupting LSBs
        boolean written = ImageIO.write(stegoImage, "png", outputFile);
        if (!written) {
            throw new IOException("Failed to save stego image: No suitable ImageIO writer found for PNG format.");
        }
    }

    /**
     * Embeds a string payload into an image file and saves the result as PNG.
     *
     * @param inputFile  input cover image file
     * @param message    string payload to embed (encoded as UTF-8)
     * @param outputFile output stego image file
     * @throws IOException if an I/O error occurs
     */
    public static void embed(File inputFile, String message, File outputFile) throws IOException {
        byte[] payload = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
        embed(inputFile, payload, outputFile);
    }

    /**
     * Legacy integration method preserved for compatibility with existing CLI workflow in {@code Main}.
     *
     * @param imageFile          input cover image file
     * @param message            message string to embed
     * @param newImageFileString output image file path
     * @throws Exception if embedding fails
     */
    public static void Embed(File imageFile, String message, String newImageFileString) throws Exception {
        if (newImageFileString == null || newImageFileString.trim().isEmpty()) {
            throw new IllegalArgumentException("Output image path cannot be empty.");
        }
        embed(imageFile, message, new File(newImageFileString));
    }

    /**
     * Creates a deep raster copy of a BufferedImage, preserving alpha channels and transparency.
     *
     * @param source source BufferedImage
     * @return copy of the source BufferedImage
     */
    public static BufferedImage copyImage(BufferedImage source) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }
}
