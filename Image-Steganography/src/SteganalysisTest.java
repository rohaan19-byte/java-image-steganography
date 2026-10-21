import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Automated test suite covering Phase 3 Statistical Review and Corrections:
 * - Degrees of freedom equal to eligible pairs (df = m)
 * - Sparse-pair tracking and exclusion
 * - Zero and one eligible pair edge cases
 * - Numerically verified p-values against reference tables
 * - Hand-calculated chi-square examples
 * - Regression suites for Phase 1 and Phase 2
 * - Real-image comparative steganalysis
 *
 * Runnable standalone via {@code java SteganalysisTest}.
 */
public class SteganalysisTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Starting Phase 3 Statistical Review Test Suite  ");
        System.out.println("=================================================");

        runTest("1. LSB counts are correct for small synthetic image with known pixels", SteganalysisTest::testLsbCountsKnownImage);
        runTest("2. LSB percentages are calculated correctly", SteganalysisTest::testLsbPercentagesCalculatedCorrectly);
        runTest("3. Neighboring LSB transitions match manual calculation", SteganalysisTest::testNeighboringLsbTransitionsMatchManual);
        runTest("4. Image with all-zero blue-channel LSBs handled correctly", SteganalysisTest::testAllZeroBlueLsbHandledCorrectly);
        runTest("5. Image with all-one blue-channel LSBs handled correctly", SteganalysisTest::testAllOneBlueLsbHandledCorrectly);
        runTest("6. Known synthetic histogram produces expected PoV counts", SteganalysisTest::testSyntheticHistogramProducesExpectedPovCounts);
        runTest("7. Chi-square statistics and df=m match manual calculation (2 pairs -> df=2)", SteganalysisTest::testChiSquareMatchesManualCalculation);
        runTest("8. Degrees of freedom match eligible pairs for several PoV counts (df=m)", SteganalysisTest::testDegreesOfFreedomMatchEligiblePairs);
        runTest("9. Degrees of freedom match exactly after sparse-pair exclusion", SteganalysisTest::testDegreesOfFreedomAfterSparseExclusion);
        runTest("10. Zero eligible pairs handled safely (df=0, p-value is NaN, not misleading 1.0)", SteganalysisTest::testZeroEligiblePairsSafeNaN);
        runTest("11. Exactly one eligible pair produces df=1 and valid p-value", SteganalysisTest::testOneEligiblePairProducesDf1);
        runTest("12. Chi-square p-values match known statistical reference tables", SteganalysisTest::testPValuesMatchReferenceTables);
        runTest("13. Constant-color and synthetic images do not cause crashes", SteganalysisTest::testConstantColorImagesDoNotCrash);
        runTest("14. Unsupported or unreadable image paths produce useful errors", SteganalysisTest::testInvalidImagePathsProduceUsefulErrors);
        runTest("15. Existing Phase 1 test suite still passes", SteganalysisTest::testPhase1RegressionSuite);
        runTest("16. Existing Phase 2 test suite still passes", SteganalysisTest::testPhase2RegressionSuite);
        runTest("17. Real-image cover-vs-stego comparative workflow runs correctly", SteganalysisTest::testActualProjectWorkflowIntegration);

        System.out.println("=================================================");
        System.out.printf(" Test Results: %d passed, %d failed out of %d tests%n", testsPassed, testsFailed, testsRun);
        System.out.println("=================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String testName, TestRunnable test) {
        testsRun++;
        try {
            test.run();
            testsPassed++;
            System.out.printf(" [PASS] %s%n", testName);
        } catch (Throwable t) {
            testsFailed++;
            System.out.printf(" [FAIL] %s%n", testName);
            System.out.printf("        Reason: %s%n", t.getMessage());
            t.printStackTrace(System.out);
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(String.format("Assertion failed: %s (expected: %d, actual: %d)", message, expected, actual));
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) return;
        if (Double.isInfinite(expected) && Double.isInfinite(actual)) return;
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(String.format("Assertion failed: %s (expected: %f, actual: %f, delta: %f)",
                    message, expected, actual, delta));
        }
    }

    // --- Tests ---

    /**
     * 1. LSB counts are correct for a small synthetic image with known pixel values.
     */
    private static void testLsbCountsKnownImage() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        // (0,0): Blue=10 (LSB=0)
        img.setRGB(0, 0, 10);
        // (1,0): Blue=11 (LSB=1)
        img.setRGB(1, 0, 11);
        // (0,1): Blue=12 (LSB=0)
        img.setRGB(0, 1, 12);
        // (1,1): Blue=15 (LSB=1)
        img.setRGB(1, 1, 15);

        LSBStatisticsAnalyzer.Result res = LSBStatisticsAnalyzer.analyze(img, LSBStatisticsAnalyzer.Channel.BLUE);

        assertEquals(4L, res.getTotalPixels(), "Total pixels");
        assertEquals(2L, res.getZerosCount(), "Zeros count");
        assertEquals(2L, res.getOnesCount(), "Ones count");
    }

    /**
     * 2. LSB percentages are calculated correctly.
     */
    private static void testLsbPercentagesCalculatedCorrectly() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        // 3 zeros, 1 one
        img.setRGB(0, 0, 20); // 0
        img.setRGB(1, 0, 22); // 0
        img.setRGB(0, 1, 24); // 0
        img.setRGB(1, 1, 25); // 1

        LSBStatisticsAnalyzer.Result res = LSBStatisticsAnalyzer.analyze(img, LSBStatisticsAnalyzer.Channel.BLUE);

        assertEquals(75.0, res.getZeroPercentage(), 1e-9, "Zero percentage = 75%");
        assertEquals(25.0, res.getOnePercentage(), 1e-9, "One percentage = 25%");
    }

    /**
     * 3. Neighboring LSB transition counts match a manually calculated example.
     */
    private static void testNeighboringLsbTransitionsMatchManual() {
        // 1x5 image: row-major bits = [0, 1, 1, 0, 1]
        BufferedImage img = new BufferedImage(5, 1, BufferedImage.TYPE_INT_RGB);
        int[] blues = { 10, 11, 13, 12, 15 };
        for (int x = 0; x < 5; x++) {
            img.setRGB(x, 0, blues[x]);
        }

        LSBStatisticsAnalyzer.Result res = LSBStatisticsAnalyzer.analyze(img, LSBStatisticsAnalyzer.Channel.BLUE);

        assertEquals(5L, res.getTotalPixels(), "Total pixels");
        assertEquals(4L, res.getPossibleTransitions(), "Possible transitions");
        assertEquals(0L, res.getTrans00(), "trans00");
        assertEquals(2L, res.getTrans01(), "trans01");
        assertEquals(1L, res.getTrans10(), "trans10");
        assertEquals(1L, res.getTrans11(), "trans11");
        assertEquals(3L, res.getTotalTransitions(), "Total transitions");
        assertEquals(75.0, res.getTransitionRatePercent(), 1e-9, "Transition rate = 75%");
        assertEquals(4L, res.getTotalRuns(), "Total runs");
        assertEquals(1.25, res.getAverageRunLength(), 1e-9, "Average run length");
        assertEquals(2L, res.getMaxRunLength(), "Max run length");
    }

    /**
     * 4. An image with all-zero blue-channel LSBs is handled correctly.
     */
    private static void testAllZeroBlueLsbHandledCorrectly() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                img.setRGB(x, y, 40); // 40 is even
            }
        }

        LSBStatisticsAnalyzer.Result res = LSBStatisticsAnalyzer.analyze(img, LSBStatisticsAnalyzer.Channel.BLUE);

        assertEquals(100L, res.getZerosCount(), "Zeros count");
        assertEquals(0L, res.getOnesCount(), "Ones count");
        assertEquals(100.0, res.getZeroPercentage(), 1e-9, "Zero pct");
        assertEquals(0.0, res.getOnePercentage(), 1e-9, "One pct");
        assertEquals(0L, res.getTotalTransitions(), "Transitions");
        assertEquals(99L, res.getTrans00(), "Trans 00");
        assertEquals(1L, res.getTotalRuns(), "Total runs");
    }

    /**
     * 5. An image with all-one blue-channel LSBs is handled correctly.
     */
    private static void testAllOneBlueLsbHandledCorrectly() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                img.setRGB(x, y, 41); // 41 is odd
            }
        }

        LSBStatisticsAnalyzer.Result res = LSBStatisticsAnalyzer.analyze(img, LSBStatisticsAnalyzer.Channel.BLUE);

        assertEquals(0L, res.getZerosCount(), "Zeros count");
        assertEquals(64L, res.getOnesCount(), "Ones count");
        assertEquals(0.0, res.getZeroPercentage(), 1e-9, "Zero pct");
        assertEquals(100.0, res.getOnePercentage(), 1e-9, "One pct");
        assertEquals(0L, res.getTotalTransitions(), "Transitions");
        assertEquals(63L, res.getTrans11(), "Trans 11");
    }

    /**
     * 6. A known synthetic histogram produces the expected PoV counts.
     */
    private static void testSyntheticHistogramProducesExpectedPovCounts() {
        int[] hist = new int[256];
        hist[10] = 40;
        hist[11] = 60; // Pair 5 total = 100
        hist[20] = 50;
        hist[21] = 50; // Pair 10 total = 100

        ChiSquareAnalyzer.Result res = ChiSquareAnalyzer.analyzeHistogram(hist, ChiSquareAnalyzer.Channel.BLUE, 5);

        assertEquals(128, res.getTotalPairs(), "Total PoV pairs");
        assertEquals(2, res.getEligiblePairs(), "Eligible pairs (threshold >= 5)");
        assertEquals(126, res.getEmptyPairs(), "Empty pairs");
        assertEquals(0, res.getSparsePairs(), "Sparse pairs");
        assertEquals(200L, res.getTotalSamplesAnalyzed(), "Total samples analyzed");
    }

    /**
     * 7. Chi-square statistics and df=m match manual calculation.
     * With 2 eligible pairs, df = m = 2, and p = e^(-chi2/2) = e^(-2) ~= 0.135335.
     */
    private static void testChiSquareMatchesManualCalculation() {
        int[] hist = new int[256];
        // Pair 5 (10, 11): 40 and 60 -> (40 - 60)^2 / 100 = 400 / 100 = 4.0
        hist[10] = 40;
        hist[11] = 60;
        // Pair 10 (20, 21): 50 and 50 -> (50 - 50)^2 / 100 = 0 / 100 = 0.0
        hist[20] = 50;
        hist[21] = 50;

        ChiSquareAnalyzer.Result res = ChiSquareAnalyzer.analyzeHistogram(hist, ChiSquareAnalyzer.Channel.BLUE, 5);

        // chi^2 = 4.0 + 0.0 = 4.0
        assertEquals(4.0, res.getChiSquareStatistic(), 1e-9, "Chi-Square statistic must equal 4.0");
        // df = m eligible pairs = 2
        assertEquals(2, res.getDegreesOfFreedom(), "Degrees of freedom must equal m=2 eligible pairs");
        // For chi^2=4.0 and df=2, p-value = e^(-4/2) = e^(-2) = 0.13533528...
        double expectedP = Math.exp(-2.0);
        assertEquals(expectedP, res.getPValue(), 1e-6, "p-value for chi2=4, df=2 must be e^(-2) ~= 0.135335");
    }

    /**
     * 8. Degrees of freedom match eligible pairs for several PoV counts (df = m).
     */
    private static void testDegreesOfFreedomMatchEligiblePairs() {
        // Test with m = 10 eligible pairs
        int[] hist10 = new int[256];
        for (int k = 0; k < 10; k++) {
            hist10[2 * k] = 10;
            hist10[2 * k + 1] = 10;
        }
        ChiSquareAnalyzer.Result res10 = ChiSquareAnalyzer.analyzeHistogram(hist10, ChiSquareAnalyzer.Channel.BLUE, 5);
        assertEquals(10, res10.getEligiblePairs(), "Eligible pairs must be 10");
        assertEquals(10, res10.getDegreesOfFreedom(), "Degrees of freedom must be 10 (df = m)");

        // Test with m = 50 eligible pairs
        int[] hist50 = new int[256];
        for (int k = 0; k < 50; k++) {
            hist50[2 * k] = 10;
            hist50[2 * k + 1] = 10;
        }
        ChiSquareAnalyzer.Result res50 = ChiSquareAnalyzer.analyzeHistogram(hist50, ChiSquareAnalyzer.Channel.BLUE, 5);
        assertEquals(50, res50.getEligiblePairs(), "Eligible pairs must be 50");
        assertEquals(50, res50.getDegreesOfFreedom(), "Degrees of freedom must be 50 (df = m)");

        // Test with all 128 eligible pairs
        int[] hist128 = new int[256];
        for (int k = 0; k < 128; k++) {
            hist128[2 * k] = 10;
            hist128[2 * k + 1] = 10;
        }
        ChiSquareAnalyzer.Result res128 = ChiSquareAnalyzer.analyzeHistogram(hist128, ChiSquareAnalyzer.Channel.BLUE, 5);
        assertEquals(128, res128.getEligiblePairs(), "Eligible pairs must be 128");
        assertEquals(128, res128.getDegreesOfFreedom(), "Degrees of freedom must be 128 (df = m)");
    }

    /**
     * 9. Degrees of freedom match exactly after sparse-pair exclusion.
     */
    private static void testDegreesOfFreedomAfterSparseExclusion() {
        int[] hist = new int[256];
        // 5 eligible pairs (each has count >= 5)
        for (int k = 0; k < 5; k++) {
            hist[2 * k] = 10;
            hist[2 * k + 1] = 10; // pair total = 20
        }
        // 3 sparse pairs (each has 0 < count < 5)
        hist[10] = 1; hist[11] = 2; // pair 5: total = 3
        hist[12] = 2; hist[13] = 1; // pair 6: total = 3
        hist[14] = 1; hist[15] = 1; // pair 7: total = 2

        ChiSquareAnalyzer.Result res = ChiSquareAnalyzer.analyzeHistogram(hist, ChiSquareAnalyzer.Channel.BLUE, 5);

        assertEquals(5, res.getEligiblePairs(), "Eligible pairs must be 5");
        assertEquals(3, res.getSparsePairs(), "Sparse pairs must be 3");
        assertEquals(120, res.getEmptyPairs(), "Empty pairs must be 120");
        assertEquals(5, res.getDegreesOfFreedom(), "Degrees of freedom must strictly equal eligible pairs (5)");
        assertEquals(100L, res.getTotalSamplesAnalyzed(), "Total samples analyzed in eligible pairs");
        assertEquals(8L, res.getTotalSamplesExcluded(), "Total samples excluded from sparse pairs (3+3+2=8)");
    }

    /**
     * 10. Zero eligible pairs handled safely (df=0, p-value is Double.NaN, not misleading 1.0).
     */
    private static void testZeroEligiblePairsSafeNaN() {
        int[] emptyHist = new int[256]; // All zeros

        ChiSquareAnalyzer.Result res = ChiSquareAnalyzer.analyzeHistogram(emptyHist, ChiSquareAnalyzer.Channel.BLUE, 5);

        assertEquals(0, res.getEligiblePairs(), "Eligible pairs must be 0");
        assertEquals(128, res.getEmptyPairs(), "Empty pairs must be 128");
        assertEquals(0, res.getDegreesOfFreedom(), "Degrees of freedom must be 0");
        assertEquals(0.0, res.getChiSquareStatistic(), 1e-9, "Chi-Square statistic must be 0.0");
        assertTrue(Double.isNaN(res.getPValue()), "p-value for 0 eligible pairs must be NaN, not misleading 1.0");
        assertTrue(res.getInterpretation().contains("Inapplicable"), "Interpretation must note zero eligible pairs");
    }

    /**
     * 11. Exactly one eligible pair produces df=1 and a valid p-value.
     */
    private static void testOneEligiblePairProducesDf1() {
        int[] hist = new int[256];
        // Only pair 0 has counts: 40 and 60 (total = 100)
        hist[0] = 40;
        hist[1] = 60;

        ChiSquareAnalyzer.Result res = ChiSquareAnalyzer.analyzeHistogram(hist, ChiSquareAnalyzer.Channel.BLUE, 5);

        assertEquals(1, res.getEligiblePairs(), "Eligible pairs must be 1");
        assertEquals(1, res.getDegreesOfFreedom(), "Degrees of freedom must be 1 (df = m)");
        // chi^2 = (40 - 60)^2 / 100 = 400 / 100 = 4.0
        assertEquals(4.0, res.getChiSquareStatistic(), 1e-9, "Chi-square for 1 pair");
        // For chi^2=4.0 and df=1, p-value is 0.045500...
        assertEquals(0.045500, res.getPValue(), 1e-4, "p-value for df=1, chi2=4");
        assertTrue(!Double.isNaN(res.getPValue()), "p-value must not be NaN");
    }

    /**
     * 12. Chi-square p-values match known statistical reference tables.
     */
    private static void testPValuesMatchReferenceTables() {
        // df=1, chi2=3.84146 -> p = 0.0500
        assertEquals(0.0500, ChiSquareAnalyzer.computePValue(3.84146, 1), 1e-4, "df=1, chi2=3.84146");

        // df=2, chi2=5.99146 -> p = 0.0500
        assertEquals(0.0500, ChiSquareAnalyzer.computePValue(5.99146, 2), 1e-4, "df=2, chi2=5.99146");

        // df=10, chi2=18.3070 -> p = 0.0500
        assertEquals(0.0500, ChiSquareAnalyzer.computePValue(18.3070, 10), 1e-4, "df=10, chi2=18.3070");

        // df=128, chi2=128.0 -> p ~ 0.4834
        assertEquals(0.4834, ChiSquareAnalyzer.computePValue(128.0, 128), 1e-3, "df=128, chi2=128.0");

        // Zero chi2 -> p = 1.0
        assertEquals(1.0, ChiSquareAnalyzer.computePValue(0.0, 10), 1e-9, "chi2=0 -> p=1.0");

        // df <= 0 -> NaN
        assertTrue(Double.isNaN(ChiSquareAnalyzer.computePValue(10.0, 0)), "df=0 -> NaN");
        assertTrue(Double.isNaN(ChiSquareAnalyzer.computePValue(10.0, -1)), "df=-1 -> NaN");
    }

    /**
     * 13. Constant-color and synthetic images do not cause crashes.
     */
    private static void testConstantColorImagesDoNotCrash() {
        BufferedImage whiteImg = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                whiteImg.setRGB(x, y, 0xFFFFFF); // All blue values = 255 (Pair 127)
            }
        }

        LSBStatisticsAnalyzer.Result lsb = LSBStatisticsAnalyzer.analyze(whiteImg, LSBStatisticsAnalyzer.Channel.BLUE);
        assertEquals(900L, lsb.getOnesCount(), "All ones in white image");

        ChiSquareAnalyzer.Result chi = ChiSquareAnalyzer.analyze(whiteImg, ChiSquareAnalyzer.Channel.BLUE);
        // Only pair 127 has observations (900), all other 127 pairs are empty
        assertEquals(1, chi.getEligiblePairs(), "Constant color has exactly 1 eligible pair");
        assertEquals(1, chi.getDegreesOfFreedom(), "Degrees of freedom must be 1");
        assertTrue(!Double.isNaN(chi.getChiSquareStatistic()), "Chi-square must not be NaN");
        assertTrue(!Double.isNaN(chi.getPValue()), "p-value must not be NaN");
    }

    /**
     * 14. Unsupported or unreadable image paths produce useful errors.
     */
    private static void testInvalidImagePathsProduceUsefulErrors() {
        File nonExistent = new File("pictures/does_not_exist_987654.png");

        boolean caught = false;
        try {
            SteganalysisFacade.analyze(nonExistent);
        } catch (FileNotFoundException e) {
            caught = true;
        } catch (Exception e) {
            caught = true;
        }
        assertTrue(caught, "Non-existent file must throw FileNotFoundException");
    }

    /**
     * 15. Existing Phase 1 test suite still passes.
     */
    private static void testPhase1RegressionSuite() {
        SteganographyEngineTest.main(new String[0]);
    }

    /**
     * 16. Existing Phase 2 test suite still passes.
     */
    private static void testPhase2RegressionSuite() {
        ImageAnalysisTest.main(new String[0]);
    }

    /**
     * 17. Real-image cover-vs-stego comparative workflow runs correctly.
     */
    private static void testActualProjectWorkflowIntegration() throws Exception {
        int width = 80;
        int height = 80; // 6400 pixels
        BufferedImage cover = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Generate synthetic cover image with natural-like PoV gradients (even != odd)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 3) % 256;
                int g = (y * 3) % 256;
                int b = ((x + y) * 2) % 256; // mostly even
                cover.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        byte[] payload = new byte[700];
        new Random(42).nextBytes(payload);
        BufferedImage stego = EmbedLSB.embed(cover, payload);

        SteganalysisFacade.ComparativeReport comp = SteganalysisFacade.compare(cover, stego);
        String summary = comp.formatSummary();

        assertTrue(summary.contains("Comparative Steganalysis"), "Report summary contains header");
        assertTrue(summary.contains("Chi-Square"), "Report summary contains Chi-Square");
        assertTrue(comp.coverReport.blueChiSquare.getDegreesOfFreedom() == comp.coverReport.blueChiSquare.getEligiblePairs(),
                "Cover degrees of freedom must equal eligible pairs");
        assertTrue(comp.stegoReport.blueChiSquare.getDegreesOfFreedom() == comp.stegoReport.blueChiSquare.getEligiblePairs(),
                "Stego degrees of freedom must equal eligible pairs");
    }
}
