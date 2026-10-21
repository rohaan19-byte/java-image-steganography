import java.awt.image.BufferedImage;

/**
 * Calculates payload capacity, remaining space, and payload ratios for blue-channel LSB steganography.
 *
 * <p>Uses the Phase 1 capacity specification:
 * <ul>
 *   <li>1 bit per pixel in Blue channel</li>
 *   <li>Framing overhead: 8 bytes (4-byte magic "STEG" + 4-byte big-endian length)</li>
 *   <li>Max usable payload bytes = (width * height / 8) - 8</li>
 * </ul>
 */
public class PayloadCapacityCalculator {

    /**
     * Encapsulates all capacity and payload ratio metrics.
     */
    public static class Result {
        private final boolean hasVerifiedPayload;
        private final long userPayloadBytes;
        private final long framedPayloadBytes;
        private final long maxUsablePayloadBytes;
        private final long remainingCapacityBytes;
        private final double userPayloadRatioPercent;
        private final double framedPayloadRatioPercent;
        private final long totalRawByteCapacity;

        public Result(boolean hasVerifiedPayload, long userPayloadBytes, long framedPayloadBytes,
                      long maxUsablePayloadBytes, long remainingCapacityBytes,
                      double userPayloadRatioPercent, double framedPayloadRatioPercent,
                      long totalRawByteCapacity) {
            this.hasVerifiedPayload = hasVerifiedPayload;
            this.userPayloadBytes = userPayloadBytes;
            this.framedPayloadBytes = framedPayloadBytes;
            this.maxUsablePayloadBytes = maxUsablePayloadBytes;
            this.remainingCapacityBytes = remainingCapacityBytes;
            this.userPayloadRatioPercent = userPayloadRatioPercent;
            this.framedPayloadRatioPercent = framedPayloadRatioPercent;
            this.totalRawByteCapacity = totalRawByteCapacity;
        }

        public boolean hasVerifiedPayload() {
            return hasVerifiedPayload;
        }

        public long getUserPayloadBytes() {
            return userPayloadBytes;
        }

        public long getFramedPayloadBytes() {
            return framedPayloadBytes;
        }

        public long getMaxUsablePayloadBytes() {
            return maxUsablePayloadBytes;
        }

        public long getRemainingCapacityBytes() {
            return remainingCapacityBytes;
        }

        public double getUserPayloadRatioPercent() {
            return userPayloadRatioPercent;
        }

        public double getFramedPayloadRatioPercent() {
            return framedPayloadRatioPercent;
        }

        public long getTotalRawByteCapacity() {
            return totalRawByteCapacity;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Max usable payload capacity: %d bytes (Total raw capacity: %d bytes)%n",
                    maxUsablePayloadBytes, totalRawByteCapacity));
            if (hasVerifiedPayload) {
                sb.append(String.format("Original user payload size: %d bytes%n", userPayloadBytes));
                sb.append(String.format("Framed payload size: %d bytes (includes 8-byte header)%n", framedPayloadBytes));
                sb.append(String.format("Remaining usable capacity: %d bytes%n", remainingCapacityBytes));
                sb.append(String.format("User-payload ratio: %.4f%%%n", userPayloadRatioPercent));
                sb.append(String.format("Framed-payload ratio: %.4f%%", framedPayloadRatioPercent));
            } else {
                sb.append("Payload verification: No valid stego frame header detected in stego image.");
            }
            return sb.toString();
        }
    }

    /**
     * Calculates capacity metrics given dimensions and an explicitly known user payload size.
     *
     * @param width            image width in pixels
     * @param height           image height in pixels
     * @param userPayloadBytes size of original user payload in bytes
     * @return Result object containing all capacity and ratio metrics
     */
    public static Result calculate(int width, int height, long userPayloadBytes) {
        if (width <= 0 || height <= 0) {
            return new Result(false, 0, 0, 0, 0, 0.0, 0.0, 0);
        }

        long totalPixels = (long) width * height;
        long totalRawCapacity = totalPixels / 8;
        long maxUsable = EmbedLSB.calculateMaxPayloadBytes(width, height);

        if (userPayloadBytes < 0) {
            return new Result(false, -1, -1, maxUsable, maxUsable, 0.0, 0.0, totalRawCapacity);
        }

        long framedPayload = userPayloadBytes + EmbedLSB.HEADER_BYTES;
        long remaining = Math.max(0, maxUsable - userPayloadBytes);

        double userRatio = maxUsable > 0 ? (userPayloadBytes / (double) maxUsable) * 100.0 : 0.0;
        double framedRatio = totalRawCapacity > 0 ? (framedPayload / (double) totalRawCapacity) * 100.0 : 0.0;

        return new Result(true, userPayloadBytes, framedPayload, maxUsable, remaining, userRatio, framedRatio, totalRawCapacity);
    }

    /**
     * Calculates capacity metrics by inspecting the cover image and attempting to verify and extract
     * the payload from the stego image.
     *
     * @param coverImage cover image
     * @param stegoImage stego image to verify
     * @return Result object
     */
    public static Result calculate(BufferedImage coverImage, BufferedImage stegoImage) {
        if (coverImage == null) {
            throw new IllegalArgumentException("Cover image cannot be null.");
        }
        int w = coverImage.getWidth();
        int h = coverImage.getHeight();

        long maxUsable = EmbedLSB.calculateMaxPayloadBytes(coverImage);
        long totalRawCapacity = ((long) w * h) / 8;

        if (stegoImage == null) {
            return new Result(false, -1, -1, maxUsable, maxUsable, 0.0, 0.0, totalRawCapacity);
        }

        try {
            byte[] extracted = ExtractLSB.extract(stegoImage);
            return calculate(w, h, extracted.length);
        } catch (Exception e) {
            // No valid stego frame header detected
            return new Result(false, -1, -1, maxUsable, maxUsable, 0.0, 0.0, totalRawCapacity);
        }
    }
}
