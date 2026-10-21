import java.awt.image.BufferedImage;

/**
 * Calculates the Peak Signal-to-Noise Ratio (PSNR) between two images.
 *
 * <p>Formula:
 * <pre>
 * PSNR = 10 * log10(255^2 / MSE)
 * </pre>
 *
 * <p>If MSE is 0 (identical images), PSNR is reported as {@link Double#POSITIVE_INFINITY}.
 * A higher PSNR generally indicates a smaller pixel-level difference.
 */
public class PSNRCalculator {

    public static final double MAX_I_SQUARED = 255.0 * 255.0; // 65025.0

    /**
     * Calculates PSNR directly from a given MSE value.
     *
     * @param mse Mean Squared Error
     * @return PSNR in decibels (dB), or {@link Double#POSITIVE_INFINITY} if MSE is 0.0
     * @throws IllegalArgumentException if MSE is negative or NaN
     */
    public static double calculate(double mse) {
        if (Double.isNaN(mse) || mse < 0.0) {
            throw new IllegalArgumentException("MSE must be non-negative, got: " + mse);
        }
        if (mse == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return 10.0 * Math.log10(MAX_I_SQUARED / mse);
    }

    /**
     * Calculates PSNR between two images by first computing their MSE.
     *
     * @param img1 first image (e.g. cover)
     * @param img2 second image (e.g. stego)
     * @return PSNR in decibels (dB), or {@link Double#POSITIVE_INFINITY} if images are identical
     */
    public static double calculate(BufferedImage img1, BufferedImage img2) {
        double mse = MSECalculator.calculate(img1, img2);
        return calculate(mse);
    }

    /**
     * Returns a user-friendly string representation of the PSNR value.
     *
     * @param psnr PSNR value in dB
     * @return formatted string (e.g. "68.42 dB" or "Identical images; PSNR is infinite")
     */
    public static String format(double psnr) {
        if (Double.isInfinite(psnr)) {
            return "Identical images; PSNR is infinite (\u221E dB)";
        }
        return String.format("%.2f dB", psnr);
    }

    /**
     * Simple educational explanation of the metric.
     *
     * @return plain-English description of PSNR
     */
    public static String getExplanation() {
        return "PSNR measures visual fidelity between images in decibels (dB). "
                + "A higher PSNR indicates a smaller pixel-level difference. "
                + "Typical LSB steganography yields PSNR > 60 dB, indicating imperceptible distortion.";
    }
}
