import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Facade coordinating Phase 4 Advanced Steganalysis:
 * <ul>
 *   <li>Regular/Singular (RS) Steganalysis (LSB replacement detection and payload estimation)</li>
 *   <li>Block-based 8x8 2D Discrete Cosine Transform (DCT) frequency domain analysis</li>
 * </ul>
 */
public class AdvancedSteganalysisFacade {

    /**
     * Comprehensive report for a single candidate image.
     */
    public static class SingleReport {
        public final int width;
        public final int height;
        public final RSAnalysis.Result blueRS;
        public final RSAnalysis.Result redRS;
        public final RSAnalysis.Result greenRS;
        public final DCTAnalysis.Result blueDCT;
        public final DCTAnalysis.Result luminanceDCT;

        public SingleReport(int width, int height,
                            RSAnalysis.Result blueRS, RSAnalysis.Result redRS, RSAnalysis.Result greenRS,
                            DCTAnalysis.Result blueDCT, DCTAnalysis.Result luminanceDCT) {
            this.width = width;
            this.height = height;
            this.blueRS = blueRS;
            this.redRS = redRS;
            this.greenRS = greenRS;
            this.blueDCT = blueDCT;
            this.luminanceDCT = luminanceDCT;
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=========================================================================\n");
            sb.append("         Phase 4: Advanced Steganalysis Report (RS & DCT Analysis)        \n");
            sb.append("=========================================================================\n");
            sb.append(String.format("Image Dimensions     : %d x %d pixels (%d total pixels)\n",
                    width, height, (long) width * height));
            sb.append("-------------------------------------------------------------------------\n");
            sb.append(">>> 1. REGULAR/SINGULAR (RS) STEGANALYSIS (BLUE CHANNEL) <<<\n");
            sb.append(String.format("Total Pixel Groups   : %d groups (1x4 horizontal, non-overlapping)\n", blueRS.getTotalGroups()));
            sb.append(String.format("Mask M [0,1,1,0]     : R_M = %.2f%% (%d) | S_M = %.2f%% (%d) | U_M = %.2f%% (%d)\n",
                    blueRS.getRegularMPct(), blueRS.getRegularM(),
                    blueRS.getSingularMPct(), blueRS.getSingularM(),
                    blueRS.getUnusableMPct(), blueRS.getUnusableM()));
            sb.append(String.format("Mask -M [0,-1,-1,0]  : R_-M = %.2f%% (%d) | S_-M = %.2f%% (%d) | U_-M = %.2f%% (%d)\n",
                    blueRS.getRegularNegMPct(), blueRS.getRegularNegM(),
                    blueRS.getSingularNegMPct(), blueRS.getSingularNegM(),
                    blueRS.getUnusableNegMPct(), blueRS.getUnusableNegM()));
            sb.append(String.format("Variation Spreads    : d_0 (R_M - S_M) = %+.2f%% | d_-0 (R_-M - S_-M) = %+.2f%%\n",
                    blueRS.getDifferenceM(), blueRS.getDifferenceNegM()));

            // Raw mathematical estimate vs physically valid payload rate
            if (Double.isNaN(blueRS.getEstimatedPayloadRate())) {
                sb.append("Raw RS estimate      : N/A\n");
            } else {
                sb.append(String.format("Raw RS estimate      : %+.2f%%\n", blueRS.getEstimatedPayloadRate() * 100.0));
            }

            switch (blueRS.getEstimationStatus()) {
                case RELIABLE:
                    sb.append("Status               : RELIABLE\n");
                    sb.append(String.format("Physically Valid Rate: %.2f%% of pixels (approximate estimate)\n",
                            blueRS.getEstimatedPayloadRate() * 100.0));
                    break;
                case NEGATIVE_ESTIMATE:
                    sb.append("Status               : UNRELIABLE — NEGATIVE ESTIMATE\n");
                    sb.append("Physically Valid Rate: None (raw estimate is negative; not physically possible)\n");
                    break;
                case OUT_OF_RANGE:
                    sb.append("Status               : UNRELIABLE — OUT OF RANGE (> 100%)\n");
                    sb.append("Physically Valid Rate: None (exceeds 100% physical image capacity)\n");
                    break;
                case INDETERMINATE:
                    sb.append("Status               : INDETERMINATE\n");
                    sb.append("Physically Valid Rate: None (" + blueRS.getEstimationReason() + ")\n");
                    break;
                case INSUFFICIENT_DATA:
                default:
                    sb.append("Status               : INSUFFICIENT DATA\n");
                    sb.append("Physically Valid Rate: None (" + blueRS.getEstimationReason() + ")\n");
                    break;
            }

            sb.append(String.format("RS Assessment        : %s\n", blueRS.getInterpretation()));
            sb.append("-------------------------------------------------------------------------\n");
            sb.append(">>> CROSS-CHANNEL RS SYMMETRY COMPARISON <<<\n");
            sb.append(String.format("Red Channel   : R_M=%.2f%%, S_M=%.2f%% (diff=%+.2f%%) | R_-M=%.2f%%, S_-M=%.2f%% (diff=%+.2f%%) -> Est: %s\n",
                    redRS.getRegularMPct(), redRS.getSingularMPct(), redRS.getDifferenceM(),
                    redRS.getRegularNegMPct(), redRS.getSingularNegMPct(), redRS.getDifferenceNegM(),
                    formatPayload(redRS)));
            sb.append(String.format("Green Channel : R_M=%.2f%%, S_M=%.2f%% (diff=%+.2f%%) | R_-M=%.2f%%, S_-M=%.2f%% (diff=%+.2f%%) -> Est: %s\n",
                    greenRS.getRegularMPct(), greenRS.getSingularMPct(), greenRS.getDifferenceM(),
                    greenRS.getRegularNegMPct(), greenRS.getSingularNegMPct(), greenRS.getDifferenceNegM(),
                    formatPayload(greenRS)));
            sb.append(String.format("Blue Channel  : R_M=%.2f%%, S_M=%.2f%% (diff=%+.2f%%) | R_-M=%.2f%%, S_-M=%.2f%% (diff=%+.2f%%) -> Est: %s\n",
                    blueRS.getRegularMPct(), blueRS.getSingularMPct(), blueRS.getDifferenceM(),
                    blueRS.getRegularNegMPct(), blueRS.getSingularNegMPct(), blueRS.getDifferenceNegM(),
                    formatPayload(blueRS)));
            sb.append("-------------------------------------------------------------------------\n");
            sb.append(">>> 2. 8x8 BLOCK 2D DISCRETE COSINE TRANSFORM (DCT) FREQUENCY ANALYSIS <<<\n");
            sb.append(String.format("8x8 Blocks Analyzed  : %d total (%d complete, %d edge-padded)\n",
                    blueDCT.getTotalBlocks(), blueDCT.getCompleteBlocks(), blueDCT.getPartialBlocks()));
            sb.append(String.format("DC Coefficient       : Mean = %.2f (Level-shifted by -128) | Variance = %.2f\n",
                    blueDCT.getMeanDC(), blueDCT.getDcVariance()));
            sb.append(String.format("AC Energy Per Block  : Mean = %.2f (Total = %.2e)\n",
                    blueDCT.getMeanACEnergyPerBlock(), blueDCT.getTotalACEnergy()));
            sb.append(String.format("AC Frequency Bands   : Low (u+v in 1..4): %.2f%% | Mid (5..8): %.2f%% | High (9..14): %.2f%%\n",
                    blueDCT.getLowFreqEnergyPct(), blueDCT.getMidFreqEnergyPct(), blueDCT.getHighFreqEnergyPct()));
            sb.append(String.format("Frequency Sparsity   : %.2f%% of AC coefficients have magnitude < 1.0\n",
                    blueDCT.getNearZeroACPercentage()));
            sb.append(String.format("DCT Assessment       : %s\n", blueDCT.getInterpretation()));
            sb.append("=========================================================================\n");
            sb.append("Methodological Note:\n"
                    + "  - RS Analysis is an empirical statistical detector for spatial LSB replacement;\n"
                    + "    low payload rates (<10%) or highly textured covers can alter estimation accuracy.\n"
                    + "  - Negative estimates reflect sample noise or model assumption divergence; they do\n"
                    + "    not represent negative data or prove the image is clean.\n"
                    + "  - DCT statistics describe spatial frequency distribution. Since this project uses\n"
                    + "    PNG (uncompressed spatial domain), DCT analysis is descriptive and exploratory.\n");
            sb.append("=========================================================================\n");
            return sb.toString();
        }

        private static String formatPayload(RSAnalysis.Result rs) {
            if (rs == null) return "N/A";
            switch (rs.getEstimationStatus()) {
                case RELIABLE:
                    return String.format("%.1f%%", rs.getEstimatedPayloadRate() * 100.0);
                case NEGATIVE_ESTIMATE:
                    return String.format("Negative (%+.1f%%) [Unreliable]", rs.getEstimatedPayloadRate() * 100.0);
                case OUT_OF_RANGE:
                    return String.format(">100%% (%+.1f%%) [Unreliable]", rs.getEstimatedPayloadRate() * 100.0);
                case INDETERMINATE:
                    return "Indeterminate";
                case INSUFFICIENT_DATA:
                default:
                    return "Insufficient data";
            }
        }
    }

    /**
     * Comparative report between an original cover image and a stego candidate image.
     */
    public static class ComparativeReport {
        public final SingleReport coverReport;
        public final SingleReport stegoReport;

        public ComparativeReport(SingleReport coverReport, SingleReport stegoReport) {
            this.coverReport = coverReport;
            this.stegoReport = stegoReport;
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=========================================================================================\n");
            sb.append("             Phase 4: Advanced Comparative Steganalysis (Cover vs Stego)                 \n");
            sb.append("=========================================================================================\n");
            sb.append(String.format("%-32s | %-24s | %-24s\n", "Metric (Blue Channel)", "Original Cover", "Stego Candidate"));
            sb.append("---------------------------------+--------------------------+----------------------------\n");

            // RS metrics
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Regular Groups (R_M)",
                    String.format("%.2f%% (%d)", coverReport.blueRS.getRegularMPct(), coverReport.blueRS.getRegularM()),
                    String.format("%.2f%% (%d)", stegoReport.blueRS.getRegularMPct(), stegoReport.blueRS.getRegularM())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Singular Groups (S_M)",
                    String.format("%.2f%% (%d)", coverReport.blueRS.getSingularMPct(), coverReport.blueRS.getSingularM()),
                    String.format("%.2f%% (%d)", stegoReport.blueRS.getSingularMPct(), stegoReport.blueRS.getSingularM())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Dual Regular (R_-M)",
                    String.format("%.2f%% (%d)", coverReport.blueRS.getRegularNegMPct(), coverReport.blueRS.getRegularNegM()),
                    String.format("%.2f%% (%d)", stegoReport.blueRS.getRegularNegMPct(), stegoReport.blueRS.getRegularNegM())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Dual Singular (S_-M)",
                    String.format("%.2f%% (%d)", coverReport.blueRS.getSingularNegMPct(), coverReport.blueRS.getSingularNegM()),
                    String.format("%.2f%% (%d)", stegoReport.blueRS.getSingularNegMPct(), stegoReport.blueRS.getSingularNegM())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Spread d_0 (R_M - S_M)",
                    String.format("%+.2f%%", coverReport.blueRS.getDifferenceM()),
                    String.format("%+.2f%%", stegoReport.blueRS.getDifferenceM())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Dual Spread d_-0",
                    String.format("%+.2f%%", coverReport.blueRS.getDifferenceNegM()),
                    String.format("%+.2f%%", stegoReport.blueRS.getDifferenceNegM())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Raw Estimated Rate",
                    formatRaw(coverReport.blueRS),
                    formatRaw(stegoReport.blueRS)));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Estimation Status",
                    coverReport.blueRS.getEstimationStatus().name(),
                    stegoReport.blueRS.getEstimationStatus().name()));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "RS Physically Valid Rate",
                    formatValid(coverReport.blueRS),
                    formatValid(stegoReport.blueRS)));
            sb.append("---------------------------------+--------------------------+----------------------------\n");

            // DCT metrics
            sb.append(String.format("%-32s | %-24s | %-24s\n", "DCT Mean AC Energy / Block",
                    String.format("%.2f", coverReport.blueDCT.getMeanACEnergyPerBlock()),
                    String.format("%.2f", stegoReport.blueDCT.getMeanACEnergyPerBlock())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "DCT High-Frequency AC %",
                    String.format("%.2f%%", coverReport.blueDCT.getHighFreqEnergyPct()),
                    String.format("%.2f%%", stegoReport.blueDCT.getHighFreqEnergyPct())));
            sb.append(String.format("%-32s | %-24s | %-24s\n", "DCT Near-Zero AC % (<1.0)",
                    String.format("%.2f%%", coverReport.blueDCT.getNearZeroACPercentage()),
                    String.format("%.2f%%", stegoReport.blueDCT.getNearZeroACPercentage())));
            sb.append("---------------------------------+--------------------------+----------------------------\n");

            sb.append("Cover RS Assessment : " + coverReport.blueRS.getInterpretation() + "\n");
            sb.append("Stego RS Assessment : " + stegoReport.blueRS.getInterpretation() + "\n");
            sb.append("=========================================================================================\n");
            return sb.toString();
        }

        private static String formatRaw(RSAnalysis.Result rs) {
            if (rs == null || Double.isNaN(rs.getEstimatedPayloadRate())) return "N/A";
            return String.format("%+.2f%%", rs.getEstimatedPayloadRate() * 100.0);
        }

        private static String formatValid(RSAnalysis.Result rs) {
            if (rs == null) return "None";
            if (rs.getEstimationStatus() == RSAnalysis.EstimationStatus.RELIABLE) {
                return String.format("%.2f%%", rs.getEstimatedPayloadRate() * 100.0);
            } else {
                return "None (" + rs.getEstimationStatus().name() + ")";
            }
        }
    }

    /**
     * Performs complete Phase 4 advanced steganalysis on a candidate image.
     */
    public static SingleReport analyze(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        int w = image.getWidth();
        int h = image.getHeight();

        RSAnalysis.Result blueRS = RSAnalysis.analyze(image, RSAnalysis.Channel.BLUE);
        RSAnalysis.Result redRS = RSAnalysis.analyze(image, RSAnalysis.Channel.RED);
        RSAnalysis.Result greenRS = RSAnalysis.analyze(image, RSAnalysis.Channel.GREEN);

        DCTAnalysis.Result blueDCT = DCTAnalysis.analyze(image, DCTAnalysis.Channel.BLUE);
        DCTAnalysis.Result lumDCT = DCTAnalysis.analyze(image, DCTAnalysis.Channel.LUMINANCE);

        return new SingleReport(w, h, blueRS, redRS, greenRS, blueDCT, lumDCT);
    }

    /**
     * Loads an image from disk and performs Phase 4 advanced steganalysis.
     */
    public static SingleReport analyze(File imageFile) throws IOException {
        BufferedImage img = loadImage(imageFile);
        return analyze(img);
    }

    /**
     * Performs comparative Phase 4 advanced steganalysis between cover and stego images.
     */
    public static ComparativeReport compare(BufferedImage coverImage, BufferedImage stegoImage) {
        SingleReport r1 = analyze(coverImage);
        SingleReport r2 = analyze(stegoImage);
        return new ComparativeReport(r1, r2);
    }

    /**
     * Loads cover and stego images from disk and performs comparative Phase 4 advanced steganalysis.
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
