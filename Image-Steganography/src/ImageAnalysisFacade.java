import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Unified facade coordinating image quality metrics, capacity analysis,
 * difference images, histograms, and bit-plane visualizations.
 */
public class ImageAnalysisFacade {

    /**
     * Complete analysis report containing all computed metrics.
     */
    public static class AnalysisReport {
        public final int width;
        public final int height;
        public final double mse;
        public final double psnr;
        public final double mae;
        public final LSBChangeRateCalculator.Result lsbResult;
        public final PayloadCapacityCalculator.Result capacityResult;
        public final HistogramAnalyzer.ComparisonResult histogramResult;

        public AnalysisReport(int width, int height, double mse, double psnr, double mae,
                              LSBChangeRateCalculator.Result lsbResult,
                              PayloadCapacityCalculator.Result capacityResult,
                              HistogramAnalyzer.ComparisonResult histogramResult) {
            this.width = width;
            this.height = height;
            this.mse = mse;
            this.psnr = psnr;
            this.mae = mae;
            this.lsbResult = lsbResult;
            this.capacityResult = capacityResult;
            this.histogramResult = histogramResult;
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=====================================================\n");
            sb.append("        Image Quality & Steganography Analysis       \n");
            sb.append("=====================================================\n");
            sb.append(String.format("Image Dimensions            : %d x %d pixels (%d total pixels)\n",
                    width, height, (long) width * height));
            sb.append("-----------------------------------------------------\n");
            sb.append(String.format("MSE (Mean Squared Error)    : %.6f\n", mse));
            sb.append(String.format("PSNR                        : %s\n", PSNRCalculator.format(psnr)));
            sb.append(String.format("MAE (Mean Absolute Error)   : %.6f\n", mae));
            sb.append(String.format("Blue LSB Change Rate        : %.4f%% (%d of %d bits changed)\n",
                    lsbResult.getChangeRatePercent(), lsbResult.getChangedBits(), lsbResult.getTotalBits()));
            sb.append("-----------------------------------------------------\n");
            sb.append(String.format("Max Usable Payload Capacity : %d bytes\n", capacityResult.getMaxUsablePayloadBytes()));
            if (capacityResult.hasVerifiedPayload()) {
                sb.append(String.format("Verified User Payload Size  : %d bytes\n", capacityResult.getUserPayloadBytes()));
                sb.append(String.format("Framed Payload Size         : %d bytes (includes 8-byte header)\n", capacityResult.getFramedPayloadBytes()));
                sb.append(String.format("Remaining Usable Capacity   : %d bytes\n", capacityResult.getRemainingCapacityBytes()));
                sb.append(String.format("User-Payload Ratio          : %.4f%%\n", capacityResult.getUserPayloadRatioPercent()));
                sb.append(String.format("Framed-Payload Ratio        : %.4f%%\n", capacityResult.getFramedPayloadRatioPercent()));
            } else {
                sb.append("Payload Verification        : No valid stego frame header detected.\n");
            }
            sb.append("-----------------------------------------------------\n");
            sb.append(String.format("Histogram Max Bin Diff      : Red=%d, Green=%d, Blue=%d\n",
                    histogramResult.getMaxRedDiff(), histogramResult.getMaxGreenDiff(), histogramResult.getMaxBlueDiff()));
            sb.append(String.format("Histogram Total Abs Diff    : Red=%d, Green=%d, Blue=%d\n",
                    histogramResult.getTotalAbsRedDiff(), histogramResult.getTotalAbsGreenDiff(), histogramResult.getTotalAbsBlueDiff()));
            sb.append("=====================================================\n");
            return sb.toString();
        }
    }

    /**
     * Executes comprehensive analysis comparing cover and stego BufferedImages.
     *
     * @param cover cover image
     * @param stego stego image
     * @return complete AnalysisReport
     */
    public static AnalysisReport analyze(BufferedImage cover, BufferedImage stego) {
        if (cover == null || stego == null) {
            throw new IllegalArgumentException("Cover and stego images cannot be null.");
        }
        if (cover.getWidth() != stego.getWidth() || cover.getHeight() != stego.getHeight()) {
            throw new DimensionMismatchException(cover.getWidth(), cover.getHeight(),
                    stego.getWidth(), stego.getHeight());
        }

        int w = cover.getWidth();
        int h = cover.getHeight();

        double mse = MSECalculator.calculate(cover, stego);
        double psnr = PSNRCalculator.calculate(mse);
        double mae = MAECalculator.calculate(cover, stego);
        LSBChangeRateCalculator.Result lsbResult = LSBChangeRateCalculator.calculate(cover, stego);
        PayloadCapacityCalculator.Result capResult = PayloadCapacityCalculator.calculate(cover, stego);
        HistogramAnalyzer.ComparisonResult histResult = HistogramAnalyzer.compare(cover, stego);

        return new AnalysisReport(w, h, mse, psnr, mae, lsbResult, capResult, histResult);
    }

    /**
     * Loads images from files and executes comprehensive analysis.
     *
     * @param coverFile cover image file
     * @param stegoFile stego image file
     * @return complete AnalysisReport
     * @throws IOException if loading either image fails
     */
    public static AnalysisReport analyze(File coverFile, File stegoFile) throws IOException {
        BufferedImage cover = loadImage(coverFile);
        BufferedImage stego = loadImage(stegoFile);
        return analyze(cover, stego);
    }

    private static BufferedImage loadImage(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Image file not found: " + (file == null ? "null" : file.getPath()));
        }
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IllegalArgumentException("Unsupported or corrupted image file: " + file.getPath());
        }
        return img;
    }
}
