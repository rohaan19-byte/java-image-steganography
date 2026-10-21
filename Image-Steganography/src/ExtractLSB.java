import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Core engine for Least Significant Bit (LSB) steganographic extraction.
 *
 * <p>Validates the 8-byte framing header:
 * <ul>
 *   <li>Magic bytes {@code "STEG"} (0x53, 0x54, 0x45, 0x47)</li>
 *   <li>32-bit big-endian payload length validated against image capacity</li>
 * </ul>
 *
 * <p>Bits are read 1 bit per pixel from the Blue channel LSB in row-major
 * raster order (x = pixelIndex % width, y = pixelIndex / width), Most Significant
 * Bit (MSB, bit 7) first.
 */
public class ExtractLSB {

    public static final byte[] MAGIC_HEADER = new byte[] { 'S', 'T', 'E', 'G' };
    public static final int HEADER_BYTES = 8; // 4 bytes magic + 4 bytes length

    /**
     * Extracts the raw binary payload from a steganographic BufferedImage.
     *
     * @param image the BufferedImage containing stego data
     * @return exact byte array of the original embedded payload
     * @throws InvalidStegoHeaderException if magic header is missing/invalid or length is corrupt
     * @throws IllegalArgumentException    if image is null or smaller than framing header
     */
    public static byte[] extract(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        long totalPixels = (long) width * (long) height;

        if (totalPixels < HEADER_BYTES * 8L) {
            throw new InvalidStegoHeaderException("Image is too small to contain a stego frame: "
                    + totalPixels + " pixels available, minimum required is " + (HEADER_BYTES * 8));
        }

        // Read 8-byte frame header (64 pixels)
        byte[] header = new byte[HEADER_BYTES];
        for (int i = 0; i < HEADER_BYTES; i++) {
            int b = 0;
            for (int bit = 7; bit >= 0; bit--) {
                int pixelIndex = i * 8 + (7 - bit);
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int bitVal = image.getRGB(x, y) & 1;
                b = (b << 1) | bitVal;
            }
            header[i] = (byte) b;
        }

        // Verify 4-byte magic header "STEG"
        if (header[0] != MAGIC_HEADER[0] || header[1] != MAGIC_HEADER[1]
                || header[2] != MAGIC_HEADER[2] || header[3] != MAGIC_HEADER[3]) {
            throw new InvalidStegoHeaderException("Invalid or missing stego magic header. "
                    + "Expected 'STEG', but found: " + formatHeader(header));
        }

        // Decode 32-bit big-endian payload length
        int payloadLength = ((header[4] & 0xFF) << 24)
                          | ((header[5] & 0xFF) << 16)
                          | ((header[6] & 0xFF) << 8)
                          | (header[7] & 0xFF);

        long maxCapacity = (totalPixels / 8) - HEADER_BYTES;

        if (payloadLength < 0) {
            throw new InvalidStegoHeaderException("Invalid stego payload length: negative length (" + payloadLength + ").");
        }
        if (payloadLength > maxCapacity) {
            throw new InvalidStegoHeaderException("Corrupted stego header: declared payload length ("
                    + payloadLength + " bytes) exceeds maximum image capacity (" + maxCapacity + " bytes).");
        }

        // Read exactly payloadLength bytes from pixels starting at pixel index 64
        byte[] payload = new byte[payloadLength];
        for (int i = 0; i < payloadLength; i++) {
            int b = 0;
            for (int bit = 7; bit >= 0; bit--) {
                int pixelIndex = (HEADER_BYTES + i) * 8 + (7 - bit);
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int bitVal = image.getRGB(x, y) & 1;
                b = (b << 1) | bitVal;
            }
            payload[i] = (byte) b;
        }

        return payload;
    }

    /**
     * Extracts raw binary payload from an image file.
     *
     * @param imageFile file containing stego image
     * @return exact byte array of the original embedded payload
     * @throws IOException                 if file reading fails
     * @throws InvalidStegoHeaderException if stego header is invalid
     */
    public static byte[] extract(File imageFile) throws IOException {
        if (imageFile == null || !imageFile.exists() || !imageFile.isFile()) {
            throw new FileNotFoundException("Image file not found: " + (imageFile == null ? "null" : imageFile.getPath()));
        }
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IllegalArgumentException("Unsupported or corrupted image file: " + imageFile.getPath());
        }
        return extract(image);
    }

    /**
     * Extracts string payload (UTF-8 encoded) from an image file.
     *
     * @param imageFile file containing stego image
     * @return extracted payload as UTF-8 string
     * @throws IOException if file reading fails
     */
    public static String extractString(File imageFile) throws IOException {
        byte[] payload = extract(imageFile);
        return new String(payload, StandardCharsets.UTF_8);
    }

    /**
     * Extracts encrypted Base64 ciphertext from an image file and decrypts it with AES.
     *
     * @param imageFile stego image file
     * @param secretKey AES secret key
     * @param cipher    initialized or uninitialized AES Cipher
     * @return decrypted plain text
     * @throws Exception if extraction or decryption fails
     */
    public static String extractAndDecrypt(File imageFile, SecretKey secretKey, Cipher cipher) throws Exception {
        String base64CipherText = extractString(imageFile);
        TextDecryptor textDecryptor = new TextDecryptor();
        return textDecryptor.decrypt(base64CipherText, secretKey, cipher);
    }

    /**
     * Legacy integration method preserved for compatibility with existing CLI workflow in {@code Main}.
     *
     * @param newImageFileString path to stego image file
     * @param secretKey          AES SecretKey
     * @param cipher             Cipher instance
     * @throws Exception if extraction or decryption fails
     */
    public static void Extract(String newImageFileString, SecretKey secretKey, Cipher cipher) throws Exception {
        if (newImageFileString == null || newImageFileString.trim().isEmpty()) {
            throw new IllegalArgumentException("Image path cannot be empty.");
        }
        File newImageFile = new File(newImageFileString);
        String decrypted = extractAndDecrypt(newImageFile, secretKey, cipher);
        System.out.println("Message: " + decrypted);
    }

    private static String formatHeader(byte[] header) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(4, header.length); i++) {
            if (i > 0) sb.append(", ");
            char c = (char) header[i];
            if (c >= 32 && c <= 126) {
                sb.append("'").append(c).append("'");
            } else {
                sb.append(String.format("0x%02X", header[i] & 0xFF));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
