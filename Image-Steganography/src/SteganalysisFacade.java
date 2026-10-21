import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * High-level facade orchestrating LSB statistical analysis and Chi-Square steganalysis.
 */
public class SteganalysisFacade {

    /**
     * Steganalysis report for a single candidate image.
     */
    public static class SingleImageReport {
        public final int width;
        public final int height;
        public final LSBStatisticsAnalyzer.Result blueLsbStats;
        public final ChiSquareAnalyzer.Result blueChiSquare;
        public final LSBStatisticsAnalyzer.Result redLsbStats;
        public final ChiSquareAnalyzer.Result redChiSquare;
        public final LSBStatisticsAnalyzer.Result greenLsbStats;
        public final ChiSquareAnalyzer.Result greenChiSquare;

        public SingleImageReport(int width, int height,
                                 LSBStatisticsAnalyzer.Result blueLsbStats, ChiSquareAnalyzer.Result blueChiSquare,
                                 LSBStatisticsAnalyzer.Result redLsbStats, ChiSquareAnalyzer.Result redChiSquare,
                                 LSBStatisticsAnalyzer.Result greenLsbStats, ChiSquareAnalyzer.Result greenChiSquare) {
            this.width = width;
            this.height = height;
            this.blueLsbStats = blueLsbStats;
            this.blueChiSquare = blueChiSquare;
            this.redLsbStats = redLsbStats;
            this.redChiSquare = redChiSquare;
            this.greenLsbStats = greenLsbStats;
            this.greenChiSquare = greenChiSquare;
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=====================================================\n");
            sb.append("        Phase 3: Statistical Steganalysis Report     \n");
            sb.append("=====================================================\n");
            sb.append(String.format("Image Dimensions     : %d x %d pixels (%d total pixels)\n",
                    width, height, (long) width * height));
            sb.append("-----------------------------------------------------\n");
            sb.append(">>> PRIMARY EMBEDDING CHANNEL (BLUE) ANALYSIS <<<\n");
            sb.append(String.format("LSB Bit Distribution: 0s: %d (%.2f%%) | 1s: %d (%.2f%%) -> %s\n",
                    blueLsbStats.getZerosCount(), blueLsbStats.getZeroPercentage(),
                    blueLsbStats.getOnesCount(), blueLsbStats.getOnePercentage(),
                    blueLsbStats.getBalanceAssessment()));
            sb.append(String.format("Sequential Transitions: %d of %d (%.2f%%)\n",
                    blueLsbStats.getTotalTransitions(), blueLsbStats.getPossibleTransitions(),
                    blueLsbStats.getTransitionRatePercent()));
            sb.append(String.format("Transitions Matrix    : 0->0: %d | 0->1: %d | 1->0: %d | 1->1: %d\n",
                    blueLsbStats.getTrans00(), blueLsbStats.getTrans01(),
                    blueLsbStats.getTrans10(), blueLsbStats.getTrans11()));
            sb.append(String.format("Run Lengths           : Total Runs: %d, Avg: %.2f, Max: %d\n",
                    blueLsbStats.getTotalRuns(), blueLsbStats.getAverageRunLength(), blueLsbStats.getMaxRunLength()));
            sb.append("-----------------------------------------------------\n");
            sb.append(">>> CHI-SQUARE GOODNESS-OF-FIT (BLUE PoVs) <<<\n");
            sb.append(String.format("Eligible PoV Pairs    : %d of 128 (Threshold >= %d samples, E >= %.1f)\n",
                    blueChiSquare.getEligiblePairs(), blueChiSquare.getMinPairCountThreshold(),
                    blueChiSquare.getMinPairCountThreshold() / 2.0));
            sb.append(String.format("Excluded PoV Pairs    : %d sparse (< %d samples), %d empty\n",
                    blueChiSquare.getSparsePairs(), blueChiSquare.getMinPairCountThreshold(),
                    blueChiSquare.getEmptyPairs()));
            sb.append(String.format("Chi-Square Statistic  : %.4f\n", blueChiSquare.getChiSquareStatistic()));
            sb.append(String.format("Degrees of Freedom    : %d (df = m eligible pairs)\n", blueChiSquare.getDegreesOfFreedom()));
            if (Double.isNaN(blueChiSquare.getPValue())) {
                sb.append("p-value (H0 fit prob) : N/A (0 eligible pairs)\n");
            } else {
                sb.append(String.format("p-value (H0 fit prob) : %.6f%s\n",
                        blueChiSquare.getPValue(),
                        blueChiSquare.isApproximationReliable() ? "" : " (approximate: small sample)"));
            }
            sb.append(String.format("Assessment            : %s\n", blueChiSquare.getInterpretation()));
            sb.append("-----------------------------------------------------\n");
            sb.append(">>> CROSS-CHANNEL REFERENCE COMPARISON <<<\n");
            sb.append(String.format("Red Channel   : LSB 1s=%.2f%%, Transitions=%.2f%%, Chi2=%.2f (df=%d, p=%s)\n",
                    redLsbStats.getOnePercentage(), redLsbStats.getTransitionRatePercent(),
                    redChiSquare.getChiSquareStatistic(), redChiSquare.getDegreesOfFreedom(),
                    formatP(redChiSquare.getPValue())));
            sb.append(String.format("Green Channel : LSB 1s=%.2f%%, Transitions=%.2f%%, Chi2=%.2f (df=%d, p=%s)\n",
                    greenLsbStats.getOnePercentage(), greenLsbStats.getTransitionRatePercent(),
                    greenChiSquare.getChiSquareStatistic(), greenChiSquare.getDegreesOfFreedom(),
                    formatP(greenChiSquare.getPValue())));
            sb.append(String.format("Blue Channel  : LSB 1s=%.2f%%, Transitions=%.2f%%, Chi2=%.2f (df=%d, p=%s)\n",
                    blueLsbStats.getOnePercentage(), blueLsbStats.getTransitionRatePercent(),
                    blueChiSquare.getChiSquareStatistic(), blueChiSquare.getDegreesOfFreedom(),
                    formatP(blueChiSquare.getPValue())));
            sb.append("=====================================================\n");
            sb.append("Statistical Disclaimer: Chi-Square and LSB statistics provide statistical clues, NOT proof.\n"
                    + "Sparse embeddings may evade detection; naturally smooth/pre-quantized covers can produce false positives.\n");
            sb.append("=====================================================\n");
            return sb.toString();
        }

        private static String formatP(double p) {
            return Double.isNaN(p) ? "N/A" : String.format("%.4f", p);
        }
    }

    /**
     * Steganalysis comparative report between original cover and stego images.
     */
    public static class ComparativeReport {
        public final SingleImageReport coverReport;
        public final SingleImageReport stegoReport;

        public ComparativeReport(SingleImageReport coverReport, SingleImageReport stegoReport) {
            this.coverReport = coverReport;
            this.stegoReport = stegoReport;
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=========================================================================\n");
            sb.append("             Phase 3: Comparative Steganalysis (Cover vs Stego)          \n");
            sb.append("=========================================================================\n");
            sb.append(String.format("%-28s | %-20s | %-20s\n", "Metric (Blue Channel)", "Original Cover", "Stego Candidate"));
            sb.append("-----------------------------+----------------------+--------------------\n");
            sb.append(String.format("%-28s | %-20s | %-20s\n", "LSB '0' Count (Pct)",
                    String.format("%d (%.2f%%)", coverReport.blueLsbStats.getZerosCount(), coverReport.blueLsbStats.getZeroPercentage()),
                    String.format("%d (%.2f%%)", stegoReport.blueLsbStats.getZerosCount(), stegoReport.blueLsbStats.getZeroPercentage())));
            sb.append(String.format("%-28s | %-20s | %-20s\n", "LSB '1' Count (Pct)",
                    String.format("%d (%.2f%%)", coverReport.blueLsbStats.getOnesCount(), coverReport.blueLsbStats.getOnePercentage()),
                    String.format("%d (%.2f%%)", stegoReport.blueLsbStats.getOnesCount(), stegoReport.blueLsbStats.getOnePercentage())));
            sb.append(String.format("%-28s | %-20s | %-20s\n", "LSB Transitions (Pct)",
                    String.format("%d (%.2f%%)", coverReport.blueLsbStats.getTotalTransitions(), coverReport.blueLsbStats.getTransitionRatePercent()),
                    String.format("%d (%.2f%%)", stegoReport.blueLsbStats.getTotalTransitions(), stegoReport.blueLsbStats.getTransitionRatePercent())));
            sb.append(String.format("%-28s | %-20s | %-20s\n", "Avg Run Length",
                    String.format("%.2f pixels", coverReport.blueLsbStats.getAverageRunLength()),
                    String.format("%.2f pixels", stegoReport.blueLsbStats.getAverageRunLength())));
            sb.append("-----------------------------+----------------------+--------------------\n");
            sb.append(String.format("%-28s | %-20s | %-20s\n", "Chi-Square Statistic",
                    String.format("%.4f", coverReport.blueChiSquare.getChiSquareStatistic()),
                    String.format("%.4f", stegoReport.blueChiSquare.getChiSquareStatistic())));
            sb.append(String.format("%-28s | %-20s | %-20s\n", "Degrees of Freedom (df=m)",
                    String.format("%d", coverReport.blueChiSquare.getDegreesOfFreedom()),
                    String.format("%d", stegoReport.blueChiSquare.getDegreesOfFreedom())));
            sb.append(String.format("%-28s | %-20s | %-20s\n", "Chi-Square p-value",
                    formatP(coverReport.blueChiSquare.getPValue()),
                    formatP(stegoReport.blueChiSquare.getPValue())));
            sb.append("-----------------------------+----------------------+--------------------\n");
            sb.append(String.format("Cover Assessment : %s\n", coverReport.blueChiSquare.getInterpretation()));
            sb.append(String.format("Stego Assessment : %s\n", stegoReport.blueChiSquare.getInterpretation()));
            sb.append("=========================================================================\n");
            return sb.toString();
        }

        private static String formatP(double p) {
            return Double.isNaN(p) ? "N/A" : String.format("%.6f", p);
        }
    }

    /**
     * Analyzes a candidate image for LSB statistical patterns and Chi-Square steganography clues.
     *
     * @param image image to analyze
     * @return SingleImageReport
     */
    public static SingleImageReport analyze(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        int w = image.getWidth();
        int h = image.getHeight();

        LSBStatisticsAnalyzer.Result blueLsb = LSBStatisticsAnalyzer.analyze(image, LSBStatisticsAnalyzer.Channel.BLUE);
        ChiSquareAnalyzer.Result blueChi = ChiSquareAnalyzer.analyze(image, ChiSquareAnalyzer.Channel.BLUE);

        LSBStatisticsAnalyzer.Result redLsb = LSBStatisticsAnalyzer.analyze(image, LSBStatisticsAnalyzer.Channel.RED);
        ChiSquareAnalyzer.Result redChi = ChiSquareAnalyzer.analyze(image, ChiSquareAnalyzer.Channel.RED);

        LSBStatisticsAnalyzer.Result greenLsb = LSBStatisticsAnalyzer.analyze(image, LSBStatisticsAnalyzer.Channel.GREEN);
        ChiSquareAnalyzer.Result greenChi = ChiSquareAnalyzer.analyze(image, ChiSquareAnalyzer.Channel.GREEN);

        return new SingleImageReport(w, h, blueLsb, blueChi, redLsb, redChi, greenLsb, greenChi);
    }

    /**
     * Loads an image from disk and analyzes it.
     *
     * @param imageFile candidate image file
     * @return SingleImageReport
     * @throws IOException if loading fails
     */
    public static SingleImageReport analyze(File imageFile) throws IOException {
        BufferedImage img = loadImage(imageFile);
        return analyze(img);
    }

    /**
     * Performs comparative steganalysis between a cover image and a stego image.
     *
     * @param coverImage original cover image
     * @param stegoImage stego candidate image
     * @return ComparativeReport
     */
    public static ComparativeReport compare(BufferedImage coverImage, BufferedImage stegoImage) {
        SingleImageReport r1 = analyze(coverImage);
        SingleImageReport r2 = analyze(stegoImage);
        return new ComparativeReport(r1, r2);
    }

    /**
     * Loads two images from disk and performs comparative steganalysis.
     *
     * @param coverFile cover image file
     * @param stegoFile stego image file
     * @return ComparativeReport
     * @throws IOException if loading fails
     */
    public static ComparativeReport compare(File coverFile, File stegoFile) throws IOException {
        BufferedImage img1 = loadImage(coverFile);
        BufferedImage img2 = loadImage(stegoFile);
        return compare(img1, img2);
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
