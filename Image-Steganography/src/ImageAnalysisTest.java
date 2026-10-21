import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Automated test suite covering all 18 Phase 2 Image Quality Metrics and Visual Analysis requirements.
 * Runnable standalone via {@code java ImageAnalysisTest}.
 */
public class ImageAnalysisTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Starting Phase 2 Image Analysis Test Suite      ");
        System.out.println("=================================================");

        runTest("1. Identical images produce MSE = 0 and MAE = 0", ImageAnalysisTest::testIdenticalImagesProduceZeroMseMae);
        runTest("2. Identical images produce infinite PSNR", ImageAnalysisTest::testIdenticalImagesProduceInfinitePsnr);
        runTest("3. Known pixel differences produce expected MSE and MAE", ImageAnalysisTest::testKnownPixelDifferencesProduceExpectedMseMae);
        runTest("4. PSNR matches formula for known nonzero MSE", ImageAnalysisTest::testPsnrFormulaMatchesNonzeroMse);
        runTest("5. Blue-channel LSB change rate is correct for controlled image", ImageAnalysisTest::testBlueLsbChangeRateControlled);
        runTest("6. Identical image pair has 0% LSB change rate", ImageAnalysisTest::testIdenticalImagePairHasZeroLsbChangeRate);
        runTest("7. Dimension mismatches are rejected safely", ImageAnalysisTest::testDimensionMismatchesRejectedSafely);
        runTest("8. Payload and capacity calculations use Phase 1 method", ImageAnalysisTest::testPayloadCapacityUsesPhase1Method);
        runTest("9. Empty payload and zero-capacity cases handled safely", ImageAnalysisTest::testEmptyPayloadAndZeroCapacityHandledSafely);
        runTest("10. Difference images have correct dimensions and differences", ImageAnalysisTest::testDifferenceImageDimensionsAndDifferences);
        runTest("11. Source images remain unmodified after analysis", ImageAnalysisTest::testSourceImagesRemainUnmodified);
        runTest("12. RGB histograms contain 256 bins and match pixel counts", ImageAnalysisTest::testRgbHistogramsHave256BinsAndMatchPixelCounts);
        runTest("13. Histogram counts are correct for small known image", ImageAnalysisTest::testHistogramCountsForSmallKnownImage);
        runTest("14. Bit planes 0-7 generated correctly for known pixel values", ImageAnalysisTest::testBitPlanes0Through7GeneratedCorrectly);
        runTest("15. Invalid bit-plane indices are rejected", ImageAnalysisTest::testInvalidBitPlaneIndicesRejected);
        runTest("16. Generated visual PNG images can be saved and reopened", ImageAnalysisTest::testGeneratedPngImagesSavedAndReopened);
        runTest("17. Phase 1 steganography test suite still passes", ImageAnalysisTest::testPhase1RegressionSuite);
        runTest("18. End-to-end integration with actual project workflow", ImageAnalysisTest::testActualProjectWorkflow);

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

    private static BufferedImage createSampleImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(12345);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rng.nextInt(256);
                int g = rng.nextInt(256);
                int b = rng.nextInt(256);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    // --- Tests ---

    /**
     * 1. Identical images produce MSE = 0 and MAE = 0.
     */
    private static void testIdenticalImagesProduceZeroMseMae() {
        BufferedImage img1 = createSampleImage(50, 50);
        BufferedImage img2 = EmbedLSB.copyImage(img1);

        double mse = MSECalculator.calculate(img1, img2);
        double mae = MAECalculator.calculate(img1, img2);

        assertEquals(0.0, mse, 1e-12, "MSE for identical images must be exactly 0.0");
        assertEquals(0.0, mae, 1e-12, "MAE for identical images must be exactly 0.0");
    }

    /**
     * 2. Identical images produce infinite PSNR.
     */
    private static void testIdenticalImagesProduceInfinitePsnr() {
        BufferedImage img1 = createSampleImage(40, 40);
        BufferedImage img2 = EmbedLSB.copyImage(img1);

        double psnr = PSNRCalculator.calculate(img1, img2);
        assertTrue(Double.isInfinite(psnr), "PSNR for identical images must be positive infinity");
        assertTrue(psnr > 0, "PSNR for identical images must be positive infinity");

        double directPsnr = PSNRCalculator.calculate(0.0);
        assertTrue(Double.isInfinite(directPsnr), "PSNR for MSE=0 must be positive infinity");
    }

    /**
     * 3. Known pixel differences produce expected MSE and MAE.
     */
    private static void testKnownPixelDifferencesProduceExpectedMseMae() {
        // Create 2x2 image (4 pixels = 12 channel values)
        BufferedImage img1 = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        // Pixel (0,0): img1=(10,20,30), img2=(12,20,30) -> diffs: R=2, G=0, B=0 -> sq=4, abs=2
        img1.setRGB(0, 0, (10 << 16) | (20 << 8) | 30);
        img2.setRGB(0, 0, (12 << 16) | (20 << 8) | 30);

        // Pixel (1,0): img1=(50,50,50), img2=(50,53,50) -> diffs: R=0, G=3, B=0 -> sq=9, abs=3
        img1.setRGB(1, 0, (50 << 16) | (50 << 8) | 50);
        img2.setRGB(1, 0, (50 << 16) | (53 << 8) | 50);

        // Pixel (0,1): img1=(100,100,100), img2=(100,100,104) -> diffs: R=0, G=0, B=4 -> sq=16, abs=4
        img1.setRGB(0, 1, (100 << 16) | (100 << 8) | 100);
        img2.setRGB(0, 1, (100 << 16) | (100 << 8) | 104);

        // Pixel (1,1): img1=(200,200,200), img2=(201,201,201) -> diffs: R=1, G=1, B=1 -> sq=3, abs=3
        img1.setRGB(1, 1, (200 << 16) | (200 << 8) | 200);
        img2.setRGB(1, 1, (201 << 16) | (201 << 8) | 201);

        // Sum sq = 4 + 9 + 16 + 3 = 32. Total samples = 2 * 2 * 3 = 12.
        // Expected MSE = 32 / 12 = 2.6666666666666665
        double expectedMse = 32.0 / 12.0;
        double actualMse = MSECalculator.calculate(img1, img2);
        assertEquals(expectedMse, actualMse, 1e-9, "MSE must match exact manual calculation");

        // Sum abs = 2 + 3 + 4 + 3 = 12. Total samples = 12.
        // Expected MAE = 12 / 12 = 1.0
        double expectedMae = 12.0 / 12.0;
        double actualMae = MAECalculator.calculate(img1, img2);
        assertEquals(expectedMae, actualMae, 1e-9, "MAE must match exact manual calculation");
    }

    /**
     * 4. PSNR matches formula for known nonzero MSE.
     */
    private static void testPsnrFormulaMatchesNonzeroMse() {
        double mse = 32.0 / 12.0;
        double expectedPsnr = 10.0 * Math.log10(65025.0 / mse); // ~43.8711 dB
        double actualPsnr = PSNRCalculator.calculate(mse);

        assertEquals(expectedPsnr, actualPsnr, 1e-9, "PSNR must match formula 10 * log10(255^2 / MSE)");
    }

    /**
     * 5. Blue-channel LSB change rate is correct for a controlled test image.
     */
    private static void testBlueLsbChangeRateControlled() {
        int width = 10;
        int height = 10; // 100 pixels total
        BufferedImage img1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Fill img1 with blue LSB = 0 for all pixels
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img1.setRGB(x, y, (100 << 16) | (100 << 8) | 100); // 100 is even, LSB=0
                img2.setRGB(x, y, (100 << 16) | (100 << 8) | 100);
            }
        }

        // Alter exactly 37 blue LSBs in img2 (to 101, LSB=1)
        int changed = 37;
        for (int i = 0; i < changed; i++) {
            int x = i % width;
            int y = i / width;
            img2.setRGB(x, y, (100 << 16) | (100 << 8) | 101);
        }

        LSBChangeRateCalculator.Result result = LSBChangeRateCalculator.calculate(img1, img2);
        assertEquals(37L, result.getChangedBits(), "Changed bits must equal 37");
        assertEquals(100L, result.getTotalBits(), "Total compared bits must equal 100");
        assertEquals(37.0, result.getChangeRatePercent(), 1e-9, "Change rate percentage must be exactly 37.0%");
    }

    /**
     * 6. An identical image pair has a 0% LSB change rate.
     */
    private static void testIdenticalImagePairHasZeroLsbChangeRate() {
        BufferedImage img = createSampleImage(30, 30);
        LSBChangeRateCalculator.Result result = LSBChangeRateCalculator.calculate(img, img);

        assertEquals(0L, result.getChangedBits(), "Changed bits must be 0 for identical images");
        assertEquals(900L, result.getTotalBits(), "Total bits must match width * height");
        assertEquals(0.0, result.getChangeRatePercent(), 1e-9, "Change rate must be 0.0%");
    }

    /**
     * 7. Dimension mismatches are rejected safely.
     */
    private static void testDimensionMismatchesRejectedSafely() {
        BufferedImage img1 = createSampleImage(50, 50);
        BufferedImage img2 = createSampleImage(50, 51);

        boolean mseThrown = false;
        try {
            MSECalculator.calculate(img1, img2);
        } catch (DimensionMismatchException e) {
            mseThrown = true;
            assertEquals(50, e.getWidth1(), "width1");
            assertEquals(50, e.getHeight1(), "height1");
            assertEquals(50, e.getWidth2(), "width2");
            assertEquals(51, e.getHeight2(), "height2");
        }
        assertTrue(mseThrown, "MSECalculator must throw DimensionMismatchException");

        boolean maeThrown = false;
        try {
            MAECalculator.calculate(img1, img2);
        } catch (DimensionMismatchException e) {
            maeThrown = true;
        }
        assertTrue(maeThrown, "MAECalculator must throw DimensionMismatchException");

        boolean lsbThrown = false;
        try {
            LSBChangeRateCalculator.calculate(img1, img2);
        } catch (DimensionMismatchException e) {
            lsbThrown = true;
        }
        assertTrue(lsbThrown, "LSBChangeRateCalculator must throw DimensionMismatchException");

        boolean diffThrown = false;
        try {
            DifferenceImageGenerator.generate(img1, img2);
        } catch (DimensionMismatchException e) {
            diffThrown = true;
        }
        assertTrue(diffThrown, "DifferenceImageGenerator must throw DimensionMismatchException");

        boolean histThrown = false;
        try {
            HistogramAnalyzer.compare(img1, img2);
        } catch (DimensionMismatchException e) {
            histThrown = true;
        }
        assertTrue(histThrown, "HistogramAnalyzer must throw DimensionMismatchException");
    }

    /**
     * 8. Payload and capacity calculations use the Phase 1 capacity method.
     */
    private static void testPayloadCapacityUsesPhase1Method() {
        int width = 100;
        int height = 100; // 10,000 pixels = 1250 bytes raw -> 1242 max usable
        long userPayloadBytes = 621;

        PayloadCapacityCalculator.Result result = PayloadCapacityCalculator.calculate(width, height, userPayloadBytes);

        assertTrue(result.hasVerifiedPayload(), "Should have verified payload");
        assertEquals(621L, result.getUserPayloadBytes(), "User payload bytes");
        assertEquals(629L, result.getFramedPayloadBytes(), "Framed payload bytes (621 + 8)");
        assertEquals(1242L, result.getMaxUsablePayloadBytes(), "Max usable payload capacity");
        assertEquals(621L, result.getRemainingCapacityBytes(), "Remaining usable capacity (1242 - 621)");
        assertEquals(50.0, result.getUserPayloadRatioPercent(), 1e-9, "User payload ratio should be exactly 50.0%");
        assertEquals(1250L, result.getTotalRawByteCapacity(), "Total raw capacity");
    }

    /**
     * 9. Empty payload and zero-capacity cases are handled safely.
     */
    private static void testEmptyPayloadAndZeroCapacityHandledSafely() {
        // Empty payload (0 bytes) in normal image
        PayloadCapacityCalculator.Result emptyResult = PayloadCapacityCalculator.calculate(100, 100, 0);
        assertEquals(0L, emptyResult.getUserPayloadBytes(), "User payload should be 0");
        assertEquals(8L, emptyResult.getFramedPayloadBytes(), "Framed payload should be 8");
        assertEquals(1242L, emptyResult.getRemainingCapacityBytes(), "Remaining capacity should equal max usable");
        assertEquals(0.0, emptyResult.getUserPayloadRatioPercent(), 1e-9, "Ratio should be 0.0%");

        // Zero capacity image (7x1 pixels < 64 bits = 8 bytes)
        PayloadCapacityCalculator.Result zeroCapResult = PayloadCapacityCalculator.calculate(7, 1, 0);
        assertEquals(0L, zeroCapResult.getMaxUsablePayloadBytes(), "Max usable should be 0");
        assertEquals(0.0, zeroCapResult.getUserPayloadRatioPercent(), 1e-9, "Ratio must not divide by zero");
    }

    /**
     * 10. Difference images have the correct dimensions and expected pixel differences.
     */
    private static void testDifferenceImageDimensionsAndDifferences() {
        int w = 20;
        int h = 15;
        BufferedImage img1 = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // Pixel (0,0): diff R=5 -> scaled by 10 = 50
        img1.setRGB(0, 0, (100 << 16) | (100 << 8) | 100);
        img2.setRGB(0, 0, (105 << 16) | (100 << 8) | 100);

        // Pixel (1,0): diff G=30 -> scaled by 10 = 300 -> clamped to 255
        img1.setRGB(1, 0, (50 << 16) | (50 << 8) | 50);
        img2.setRGB(1, 0, (50 << 16) | (80 << 8) | 50);

        BufferedImage diff = DifferenceImageGenerator.generate(img1, img2, 10);

        assertEquals(w, diff.getWidth(), "Diff image width must match original");
        assertEquals(h, diff.getHeight(), "Diff image height must match original");

        int p0 = diff.getRGB(0, 0);
        assertEquals(50, (p0 >> 16) & 0xFF, "Diff Red at (0,0) must be 50");
        assertEquals(0, (p0 >> 8) & 0xFF, "Diff Green at (0,0) must be 0");
        assertEquals(0, p0 & 0xFF, "Diff Blue at (0,0) must be 0");

        int p1 = diff.getRGB(1, 0);
        assertEquals(0, (p1 >> 16) & 0xFF, "Diff Red at (1,0) must be 0");
        assertEquals(255, (p1 >> 8) & 0xFF, "Diff Green at (1,0) must be clamped to 255");
        assertEquals(0, p1 & 0xFF, "Diff Blue at (1,0) must be 0");
    }

    /**
     * 11. Source images remain unmodified after analysis.
     */
    private static void testSourceImagesRemainUnmodified() {
        BufferedImage img1 = createSampleImage(25, 25);
        BufferedImage img2 = createSampleImage(25, 25);

        int[] copy1 = new int[25 * 25];
        int[] copy2 = new int[25 * 25];
        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                copy1[y * 25 + x] = img1.getRGB(x, y);
                copy2[y * 25 + x] = img2.getRGB(x, y);
            }
        }

        // Run full suite of analysis tools on img1 and img2
        ImageAnalysisFacade.analyze(img1, img2);
        DifferenceImageGenerator.generate(img1, img2);
        BitPlaneVisualizer.extractAllPlanes(img1, BitPlaneVisualizer.Channel.BLUE);
        BitPlaneVisualizer.generateComparison(img1, img2, BitPlaneVisualizer.Channel.RED, 0);

        // Verify neither image was altered
        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                assertEquals(copy1[y * 25 + x], img1.getRGB(x, y), "img1 pixel at (" + x + "," + y + ") was modified!");
                assertEquals(copy2[y * 25 + x], img2.getRGB(x, y), "img2 pixel at (" + x + "," + y + ") was modified!");
            }
        }
    }

    /**
     * 12. RGB histograms contain 256 bins and match pixel counts.
     */
    private static void testRgbHistogramsHave256BinsAndMatchPixelCounts() {
        int w = 40;
        int h = 30; // 1200 pixels
        BufferedImage img = createSampleImage(w, h);

        HistogramAnalyzer.ChannelHistogram hist = new HistogramAnalyzer.ChannelHistogram(img);

        assertEquals(256, hist.getRed().length, "Red histogram must have 256 bins");
        assertEquals(256, hist.getGreen().length, "Green histogram must have 256 bins");
        assertEquals(256, hist.getBlue().length, "Blue histogram must have 256 bins");

        long sumR = 0, sumG = 0, sumB = 0;
        for (int i = 0; i < 256; i++) {
            sumR += hist.getRed()[i];
            sumG += hist.getGreen()[i];
            sumB += hist.getBlue()[i];
        }

        assertEquals(1200L, sumR, "Sum of Red bin counts must equal image pixel count");
        assertEquals(1200L, sumG, "Sum of Green bin counts must equal image pixel count");
        assertEquals(1200L, sumB, "Sum of Blue bin counts must equal image pixel count");
    }

    /**
     * 13. Histogram counts are correct for a small image with known pixel values.
     */
    private static void testHistogramCountsForSmallKnownImage() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        // (0,0): (10, 20, 30)
        img.setRGB(0, 0, (10 << 16) | (20 << 8) | 30);
        // (1,0): (10, 25, 30)
        img.setRGB(1, 0, (10 << 16) | (25 << 8) | 30);
        // (0,1): (15, 20, 40)
        img.setRGB(0, 1, (15 << 16) | (20 << 8) | 40);
        // (1,1): (15, 25, 40)
        img.setRGB(1, 1, (15 << 16) | (25 << 8) | 40);

        HistogramAnalyzer.ChannelHistogram hist = new HistogramAnalyzer.ChannelHistogram(img);
        int[] red = hist.getRed();
        int[] green = hist.getGreen();
        int[] blue = hist.getBlue();

        assertEquals(2, red[10], "Red bin 10 count must be 2");
        assertEquals(2, red[15], "Red bin 15 count must be 2");
        assertEquals(0, red[0], "Red bin 0 count must be 0");

        assertEquals(2, green[20], "Green bin 20 count must be 2");
        assertEquals(2, green[25], "Green bin 25 count must be 2");

        assertEquals(2, blue[30], "Blue bin 30 count must be 2");
        assertEquals(2, blue[40], "Blue bin 40 count must be 2");
    }

    /**
     * 14. Every bit plane from 0-7 is generated correctly for known pixel values.
     */
    private static void testBitPlanes0Through7GeneratedCorrectly() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        // 173 = binary 10101101
        // bit 0: 1 (White)
        // bit 1: 0 (Black)
        // bit 2: 1 (White)
        // bit 3: 1 (White)
        // bit 4: 0 (Black)
        // bit 5: 1 (White)
        // bit 6: 0 (Black)
        // bit 7: 1 (White)
        int val = 173;
        img.setRGB(0, 0, (val << 16) | (val << 8) | val);

        int[] expectedBits = new int[] { 1, 0, 1, 1, 0, 1, 0, 1 };

        for (BitPlaneVisualizer.Channel ch : BitPlaneVisualizer.Channel.values()) {
            for (int plane = 0; plane < 8; plane++) {
                BufferedImage planeImg = BitPlaneVisualizer.extractPlane(img, ch, plane);
                int rgb = planeImg.getRGB(0, 0) & 0xFFFFFF;
                int expectedColor = expectedBits[plane] == 1 ? 0xFFFFFF : 0x000000;
                assertEquals(expectedColor, rgb, "Channel " + ch + " plane " + plane + " color must match bit value");
            }
        }
    }

    /**
     * 15. Invalid bit-plane indices are rejected.
     */
    private static void testInvalidBitPlaneIndicesRejected() {
        BufferedImage img = createSampleImage(10, 10);

        int[] invalidIndices = new int[] { -1, -5, 8, 9, 100 };
        for (int idx : invalidIndices) {
            boolean thrown = false;
            try {
                BitPlaneVisualizer.extractPlane(img, BitPlaneVisualizer.Channel.BLUE, idx);
            } catch (IllegalArgumentException e) {
                thrown = true;
            }
            assertTrue(thrown, "Plane index " + idx + " must be rejected with IllegalArgumentException");
        }
    }

    /**
     * 16. Generated visual PNG images can be saved and reopened.
     */
    private static void testGeneratedPngImagesSavedAndReopened() throws IOException {
        BufferedImage img1 = createSampleImage(60, 60);
        BufferedImage img2 = EmbedLSB.embed(img1, "Visual PNG test".getBytes(StandardCharsets.UTF_8));

        File tempDiff = File.createTempFile("test_diff_", ".png");
        File tempHist = File.createTempFile("test_hist_", ".png");
        File tempPlane = File.createTempFile("test_plane_", ".png");

        try {
            // Save difference image
            DifferenceImageGenerator.saveDifferenceImage(img1, img2, tempDiff, 255);
            BufferedImage reloadedDiff = ImageIO.read(tempDiff);
            assertTrue(reloadedDiff != null, "Difference image must reload from disk");
            assertEquals(60, reloadedDiff.getWidth(), "Diff width");
            assertEquals(60, reloadedDiff.getHeight(), "Diff height");

            // Save histogram chart
            HistogramAnalyzer.ComparisonResult compResult = HistogramAnalyzer.compare(img1, img2);
            HistogramAnalyzer.saveChart(compResult, tempHist);
            BufferedImage reloadedHist = ImageIO.read(tempHist);
            assertTrue(reloadedHist != null, "Histogram chart must reload from disk");
            assertTrue(reloadedHist.getWidth() > 0 && reloadedHist.getHeight() > 0, "Histogram chart dimensions");

            // Save bit plane comparison
            BitPlaneVisualizer.saveComparison(img1, img2, BitPlaneVisualizer.Channel.BLUE, 0, tempPlane);
            BufferedImage reloadedPlane = ImageIO.read(tempPlane);
            assertTrue(reloadedPlane != null, "Bit-plane comparison must reload from disk");
            assertTrue(reloadedPlane.getWidth() > 60, "Bit-plane comparison width");
        } finally {
            tempDiff.delete();
            tempHist.delete();
            tempPlane.delete();
        }
    }

    /**
     * 17. Phase 1 steganography test suite still passes.
     */
    private static void testPhase1RegressionSuite() {
        // Runs all 15 Phase 1 tests to ensure zero regressions
        SteganographyEngineTest.main(new String[0]);
    }

    /**
     * 18. End-to-end integration with actual project workflow.
     */
    private static void testActualProjectWorkflow() throws Exception {
        BufferedImage cover = createSampleImage(100, 100);
        String secret = "End-to-end Phase 2 testing message!";
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        BufferedImage stego = EmbedLSB.embed(cover, secretBytes);

        ImageAnalysisFacade.AnalysisReport report = ImageAnalysisFacade.analyze(cover, stego);

        // Validate metrics in expected range
        assertTrue(report.mse > 0.0, "MSE should be nonzero for stego image");
        assertTrue(report.psnr > 50.0, "PSNR should be > 50 dB for 1-bit LSB stego");
        assertTrue(report.mae > 0.0, "MAE should be nonzero for stego image");
        assertTrue(report.lsbResult.getChangedBits() > 0, "Blue LSB changed bits must be > 0");
        assertTrue(report.capacityResult.hasVerifiedPayload(), "Stego payload should be verified");
        assertEquals(secretBytes.length, report.capacityResult.getUserPayloadBytes(), "Verified user payload length");

        String summary = report.formatSummary();
        assertTrue(summary.contains("Image Quality & Steganography Analysis"), "Summary header");
        assertTrue(summary.contains("MSE"), "Summary contains MSE");
        assertTrue(summary.contains("PSNR"), "Summary contains PSNR");
    }
}
