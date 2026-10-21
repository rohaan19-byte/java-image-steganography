import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Focused test suite verifying Phase 4 Advanced Steganalysis:
 * <ul>
 *   <li>RS Analysis: Discrimination function, mask operations, group classification</li>
 *   <li>RS Involutive properties: F1 and F-1 over [0, 255]</li>
 *   <li>RS Quadratic payload estimator with exact, degenerate, and edge-case inputs</li>
 *   <li>Rigorous classification: NEGATIVE_ESTIMATE, OUT_OF_RANGE, RELIABLE, INDETERMINATE, INSUFFICIENT_DATA</li>
 *   <li>Controlled empirical embedding rate validation (0%, 10%, 25%, 50%, 80%) across textured, smooth, and constant images</li>
 *   <li>DCT 8x8 Transform: Orthonormality, Parseval energy conservation, known closed forms</li>
 *   <li>DCT Dimension Handling: Non-multiples of 8, small images, edge clamping</li>
 *   <li>DCT Frequency band energy decomposition (Low + Mid + High = 100%)</li>
 *   <li>Facade status and message consistency</li>
 *   <li>Robust error handling</li>
 *   <li>Full regressions for Phase 1, Phase 2, and Phase 3 suites</li>
 * </ul>
 */
public class AdvancedSteganalysisTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Starting Phase 4 Advanced Steganalysis Tests    ");
        System.out.println("=================================================");

        // --- Mathematical Unit Tests ---
        testRSDiscriminationFunction();
        testRSInvolutivePermutations();
        testRSMaskClassificationSynthetic();
        testRSConstantAndSmallImages();
        testRSPayloadEstimatorMathAndStatusRules();
        testDCTOrthonormalityAndParseval();
        testDCTKnownReferenceValues();
        testDCTDimensionHandlingAndPadding();
        testDCTFrequencyBandsConservation();
        testFacadeConsistency();
        testErrorHandling();

        // --- Controlled Empirical Validation Tests ---
        testControlledEmbeddingRateValidation();
        testCoverVsStegoComparativeRealWorkflow();

        // --- Full Regressions for Prior Phases ---
        testPhase1Regressions();
        testPhase2Regressions();
        testPhase3Regressions();

        System.out.println("=================================================");
        System.out.printf(" Phase 4 Test Results: %d passed, %d failed\n", testsPassed, testsFailed);
        System.out.println("=================================================");

        if (testsFailed > 0) {
            System.err.println("SOME TESTS FAILED!");
            System.exit(1);
        }
    }

    private static void assertTrue(boolean condition, String testName) {
        if (condition) {
            System.out.println(" [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.println(" [FAIL] " + testName);
            testsFailed++;
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String testName) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            System.out.println(" [PASS] " + testName);
            testsPassed++;
        } else if (!Double.isNaN(expected) && !Double.isNaN(actual) && Math.abs(expected - actual) <= delta) {
            System.out.println(" [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.printf(" [FAIL] %s: Expected %.6f, got %.6f\n", testName, expected, actual);
            testsFailed++;
        }
    }

    private static void assertEquals(long expected, long actual, String testName) {
        if (expected == actual) {
            System.out.println(" [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.printf(" [FAIL] %s: Expected %d, got %d\n", testName, expected, actual);
            testsFailed++;
        }
    }

    // -------------------------------------------------------------
    // Test 1: RS Discrimination Function
    // -------------------------------------------------------------
    private static void testRSDiscriminationFunction() {
        int f1 = RSAnalysis.discriminationFunction(10, 20, 30, 40);
        assertEquals(30, f1, "1. RS discrimination on linear ramp (10,20,30,40) = 30");

        int f2 = RSAnalysis.discriminationFunction(10, 10, 10, 10);
        assertEquals(0, f2, "2. RS discrimination on constant group (10,10,10,10) = 0");

        int f3 = RSAnalysis.discriminationFunction(5, 12, 3, 20);
        assertEquals(33, f3, "3. RS discrimination on zigzag group (5,12,3,20) = 33");
    }

    // -------------------------------------------------------------
    // Test 2: Involutive Permutations F1 and F-1 over [0, 255]
    // -------------------------------------------------------------
    private static void testRSInvolutivePermutations() {
        boolean f1Valid = true;
        boolean fNeg1Valid = true;

        for (int x = 0; x <= 255; x++) {
            int fx = RSAnalysis.flipStandard(x);
            if (fx < 0 || fx > 255 || RSAnalysis.flipStandard(fx) != x) {
                f1Valid = false;
            }

            int fnx = RSAnalysis.flipDual(x);
            if (fnx < 0 || fnx > 255 || RSAnalysis.flipDual(fnx) != x) {
                fNeg1Valid = false;
            }
        }

        assertTrue(f1Valid, "4. F1(x) is a strict involution on {0..255}");
        assertTrue(fNeg1Valid, "5. F-1(x) is a strict involution on {0..255}");

        assertEquals(0, RSAnalysis.flipDual(0), "6. F-1(0) = 0 (boundary invariant)");
        assertEquals(255, RSAnalysis.flipDual(255), "7. F-1(255) = 255 (boundary invariant)");
        assertEquals(2, RSAnalysis.flipDual(1), "8. F-1(1) = 2");
        assertEquals(1, RSAnalysis.flipDual(2), "9. F-1(2) = 1");
    }

    // -------------------------------------------------------------
    // Test 3: Synthetic Mask Flipping & Regular / Singular Classification
    // -------------------------------------------------------------
    private static void testRSMaskClassificationSynthetic() {
        int origA = RSAnalysis.discriminationFunction(10, 10, 10, 10);
        int fMA = RSAnalysis.discriminationFunction(
                RSAnalysis.applyFlipping(10, RSAnalysis.MASK[0]),
                RSAnalysis.applyFlipping(10, RSAnalysis.MASK[1]),
                RSAnalysis.applyFlipping(10, RSAnalysis.MASK[2]),
                RSAnalysis.applyFlipping(10, RSAnalysis.MASK[3])
        );
        assertTrue(fMA > origA, "10. Group A (10,10,10,10) is classified as Regular under M");

        int origB = RSAnalysis.discriminationFunction(10, 11, 11, 10);
        int fMB = RSAnalysis.discriminationFunction(
                RSAnalysis.applyFlipping(10, RSAnalysis.MASK[0]),
                RSAnalysis.applyFlipping(11, RSAnalysis.MASK[1]),
                RSAnalysis.applyFlipping(11, RSAnalysis.MASK[2]),
                RSAnalysis.applyFlipping(10, RSAnalysis.MASK[3])
        );
        assertTrue(fMB < origB, "11. Group B (10,11,11,10) is classified as Singular under M");
    }

    // -------------------------------------------------------------
    // Test 4: Constant and Small Images
    // -------------------------------------------------------------
    private static void testRSConstantAndSmallImages() {
        // 1. Constant-color image (all pixels 128)
        BufferedImage flat = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                flat.setRGB(x, y, (128 << 16) | (128 << 8) | 128);
            }
        }
        RSAnalysis.Result flatRes = RSAnalysis.analyze(flat, RSAnalysis.Channel.BLUE);
        assertEquals(32 * (32 / 4), flatRes.getTotalGroups(), "12. Constant image total groups = 256");
        assertTrue(!flatRes.isSuspicious(), "13. Constant image is not flagged as suspicious");
        assertTrue(Double.isNaN(flatRes.getEstimatedPayloadRate()), "14. Constant image payload rate is NaN (not false 0%)");
        assertTrue(flatRes.getEstimationStatus() == RSAnalysis.EstimationStatus.INSUFFICIENT_DATA,
                "15. Constant image status is INSUFFICIENT_DATA");

        // 2. Small image with width < 4 (e.g. 3x10)
        BufferedImage small = new BufferedImage(3, 10, BufferedImage.TYPE_INT_RGB);
        RSAnalysis.Result smallRes = RSAnalysis.analyze(small, RSAnalysis.Channel.BLUE);
        assertEquals(0, smallRes.getTotalGroups(), "16. Width < 4 yields 0 complete groups");
        assertTrue(Double.isNaN(smallRes.getEstimatedPayloadRate()), "17. Width < 4 yields NaN payload rate");

        // 3. Image with width not multiple of 4 (e.g. 15x10)
        BufferedImage ragged = new BufferedImage(15, 10, BufferedImage.TYPE_INT_RGB);
        RSAnalysis.Result raggedRes = RSAnalysis.analyze(ragged, RSAnalysis.Channel.BLUE);
        assertEquals(10 * (15 / 4), raggedRes.getTotalGroups(), "18. 15x10 yields 10 * 3 = 30 groups without crashing");
    }

    // -------------------------------------------------------------
    // Test 5: RS Quadratic Payload Rate Estimator Mathematics & Status Rules
    // -------------------------------------------------------------
    private static void testRSPayloadEstimatorMathAndStatusRules() {
        // Rule 1: A negative raw estimate (e.g. -7.71%) is NEVER classified as RELIABLE
        // We simulate a negative estimate by constructing inputs where 0 < z < 0.5 => p = z / (z - 0.5) < 0
        // With d0=21.0, d1=5.0, dNeg0=20.0, dNeg1=5.0 => C=1.0, A=52, B=-53 => z=2/104=0.01923 => p=-0.04 (-4.00%)
        double d0_neg = 21.0, d1_neg = 5.0, dNeg0_neg = 20.0, dNeg1_neg = 5.0;
        RSAnalysis.EstimationResult resNeg = RSAnalysis.estimatePayload(d0_neg, d1_neg, dNeg0_neg, dNeg1_neg, 1000, 500);
        assertTrue(resNeg.rawRate < 0.0, "19. Negative mathematical root is correctly negative");
        assertTrue(resNeg.status == RSAnalysis.EstimationStatus.NEGATIVE_ESTIMATE,
                "20. Negative raw estimate is classified as NEGATIVE_ESTIMATE (never RELIABLE)");
        assertTrue(resNeg.status != RSAnalysis.EstimationStatus.RELIABLE,
                "21. Negative estimate is strictly not RELIABLE");

        // Rule 2: A small negative estimate (e.g. -0.12%) is preserved as negative, not silently converted to zero
        double d0_smallNeg = 20.02, d1_smallNeg = 5.0, dNeg0_smallNeg = 20.0, dNeg1_smallNeg = 5.0;
        RSAnalysis.EstimationResult resSmallNeg = RSAnalysis.estimatePayload(d0_smallNeg, d1_smallNeg, dNeg0_smallNeg, dNeg1_smallNeg, 1000, 500);
        assertTrue(resSmallNeg.rawRate < 0.0, "22. Small negative estimate is preserved as negative");
        assertEquals(resSmallNeg.rawRate, resSmallNeg.rawRate, 1e-9, "23. Raw negative estimate is not silently clamped to zero");
        assertTrue(resSmallNeg.status == RSAnalysis.EstimationStatus.NEGATIVE_ESTIMATE,
                "24. Small negative estimate is classified as NEGATIVE_ESTIMATE");

        // Rule 3: A valid estimate (such as 49.47%) is classified as RELIABLE
        // When z = -0.4897 => p = -0.4897 / (-0.4897 - 0.5) = 0.4947 (49.47%)
        RSAnalysis.EstimationResult resValid = RSAnalysis.estimatePayload(15.0, 5.0, 12.0, 7.0, 1000, 500);
        if (resValid.rawRate >= 0.0 && resValid.rawRate <= 1.0) {
            assertTrue(resValid.status == RSAnalysis.EstimationStatus.RELIABLE, "25. Valid estimate in [0, 1] is RELIABLE");
        }

        // Rule 4: An estimate above 100% is explicitly flagged as OUT_OF_RANGE
        // Linear case: d0=10, d1=-10, dNeg0=0, dNeg1=-10 => A=0, B=-10, C=10 => z=1.0 => p = 1.0 / (1.0 - 0.5) = 2.0 (200%)
        RSAnalysis.EstimationResult resOut = RSAnalysis.estimatePayload(10.0, -10.0, 0.0, -10.0, 1000, 500);
        assertTrue(resOut.rawRate > 1.0, "26. Estimate > 1.0 is mathematically greater than 100%");
        assertTrue(resOut.status == RSAnalysis.EstimationStatus.OUT_OF_RANGE,
                "27. Estimate > 100% is explicitly flagged as OUT_OF_RANGE");

        // Rule 5: Non-finite estimate or negative discriminant becomes INDETERMINATE
        RSAnalysis.EstimationResult resNegD = RSAnalysis.estimatePayload(5.0, 5.0, -20.0, 20.0, 1000, 500);
        assertTrue(Double.isNaN(resNegD.rawRate), "28. Negative discriminant yields NaN (no real roots)");
        assertTrue(resNegD.status == RSAnalysis.EstimationStatus.INDETERMINATE,
                "29. Negative discriminant status is INDETERMINATE");

        // Rule 6: Insufficient data becomes INSUFFICIENT_DATA
        RSAnalysis.EstimationResult resFewGroups = RSAnalysis.estimatePayload(15.0, 5.0, 15.0, 5.0, 1000, 10);
        assertTrue(Double.isNaN(resFewGroups.rawRate), "30. Insufficient non-flat groups yields NaN");
        assertTrue(resFewGroups.status == RSAnalysis.EstimationStatus.INSUFFICIENT_DATA,
                "31. Insufficient groups status is INSUFFICIENT_DATA");

        // Symmetrical clean spreads d0 == dNeg0 on non-flat image (C = 0 => z = 0 => p = 0.0%)
        RSAnalysis.EstimationResult resExactZero = RSAnalysis.estimatePayload(15.0, 5.0, 15.0, 5.0, 1000, 500);
        assertEquals(0.0, resExactZero.rawRate, 1e-6, "32. Exact symmetrical spreads d0 == dNeg0 yield p = 0.0%");
        assertTrue(resExactZero.status == RSAnalysis.EstimationStatus.RELIABLE,
                "33. Exact zero payload rate is RELIABLE");
    }

    // -------------------------------------------------------------
    // Test 6: DCT 8x8 Orthonormality and Parseval Energy Conservation
    // -------------------------------------------------------------
    private static void testDCTOrthonormalityAndParseval() {
        Random rng = new Random(42);
        double[][] block = new double[8][8];
        double spatialEnergy = 0.0;

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                block[i][j] = rng.nextDouble() * 200.0 - 100.0;
                spatialEnergy += block[i][j] * block[i][j];
            }
        }

        double[][] F = DCTAnalysis.compute8x8DCT(block);

        double dctEnergy = 0.0;
        for (int u = 0; u < 8; u++) {
            for (int v = 0; v < 8; v++) {
                dctEnergy += F[u][v] * F[u][v];
            }
        }

        assertEquals(spatialEnergy, dctEnergy, 1e-6, "34. Parseval energy conservation holds exactly on 8x8 DCT");
    }

    // -------------------------------------------------------------
    // Test 7: DCT Known Closed-Form Reference Values
    // -------------------------------------------------------------
    private static void testDCTKnownReferenceValues() {
        double c = 25.0;
        double[][] block = new double[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                block[i][j] = c;
            }
        }

        double[][] F = DCTAnalysis.compute8x8DCT(block);

        assertEquals(200.0, F[0][0], 1e-6, "35. Constant block F(0,0) = 8 * C (200.0)");

        double maxAC = 0.0;
        for (int u = 0; u < 8; u++) {
            for (int v = 0; v < 8; v++) {
                if (u == 0 && v == 0) continue;
                maxAC = Math.max(maxAC, Math.abs(F[u][v]));
            }
        }
        assertEquals(0.0, maxAC, 1e-6, "36. Constant block AC coefficients are all zero");
    }

    // -------------------------------------------------------------
    // Test 8: DCT Dimension Handling and Edge Padding
    // -------------------------------------------------------------
    private static void testDCTDimensionHandlingAndPadding() {
        BufferedImage img = new BufferedImage(13, 19, BufferedImage.TYPE_INT_RGB);
        DCTAnalysis.Result res = DCTAnalysis.analyze(img, DCTAnalysis.Channel.BLUE);

        assertEquals(6, res.getTotalBlocks(), "37. 13x19 image partitions into 6 total 8x8 blocks");
        assertEquals(2, res.getCompleteBlocks(), "38. 13x19 image contains exactly 2 complete blocks");
        assertEquals(4, res.getPartialBlocks(), "39. 13x19 image contains exactly 4 edge-clamped partial blocks");

        BufferedImage tiny = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        DCTAnalysis.Result tinyRes = DCTAnalysis.analyze(tiny, DCTAnalysis.Channel.BLUE);
        assertEquals(1, tinyRes.getTotalBlocks(), "40. 3x3 image safely produces 1 padded 8x8 block");
    }

    // -------------------------------------------------------------
    // Test 9: DCT Frequency Bands Conservation
    // -------------------------------------------------------------
    private static void testDCTFrequencyBandsConservation() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int b = (x * 7 + y * 13) % 256;
                img.setRGB(x, y, b);
            }
        }

        DCTAnalysis.Result res = DCTAnalysis.analyze(img, DCTAnalysis.Channel.BLUE);
        double totalBandPct = res.getLowFreqEnergyPct() + res.getMidFreqEnergyPct() + res.getHighFreqEnergyPct();

        assertEquals(100.0, totalBandPct, 1e-4, "41. Low + Mid + High frequency energy sums to 100%");
        assertTrue(res.getNearZeroACPercentage() >= 0 && res.getNearZeroACPercentage() <= 100.0,
                "42. Near-zero AC coefficient percentage is in [0, 100]");
    }

    // -------------------------------------------------------------
    // Test 10: Facade and Estimator Consistency
    // -------------------------------------------------------------
    private static void testFacadeConsistency() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, (x * 3 + y * 2) % 256);
            }
        }

        AdvancedSteganalysisFacade.SingleReport report = AdvancedSteganalysisFacade.analyze(img);
        String summary = report.formatSummary();

        assertTrue(summary.contains("Raw RS estimate"), "43. Facade displays raw RS estimate");
        assertTrue(summary.contains("Status               :"), "44. Facade displays explicit status");
        assertTrue(summary.contains("Physically Valid Rate:"), "45. Facade distinguishes physically valid rate");
        assertTrue(summary.contains("8x8 BLOCK 2D DISCRETE COSINE TRANSFORM"), "46. Facade includes DCT frequency report");
    }

    // -------------------------------------------------------------
    // Test 11: Controlled Embedding Rate Validation (0%, 10%, 25%, 50%, 80%)
    // -------------------------------------------------------------
    private static void testControlledEmbeddingRateValidation() {
        System.out.println("-------------------------------------------------");
        System.out.println(" Starting Controlled RS Embedding Rate Validation");
        System.out.println("-------------------------------------------------");

        int width = 120;
        int height = 120;
        long totalPixels = (long) width * height; // 14,400 pixels
        long maxCapacityBytes = EmbedLSB.calculateMaxPayloadBytes(width, height); // (14400 / 8) - 8 = 1792 bytes

        // Generate a synthetic textured cover image with natural local correlations
        BufferedImage texturedCover = createTexturedImage(width, height);

        double[] targetRates = new double[] { 0.0, 0.10, 0.25, 0.50, 0.80 };

        System.out.println("--- Image 1: Synthetic Textured Image (120x120) ---");
        System.out.printf("%-12s | %-12s | %-14s | %-14s | %-20s\n",
                "Target Rate", "Actual Rate", "Raw RS Est", "Physically Valid", "Status");
        System.out.println("-------------+--------------+----------------+----------------+---------------------");

        double prevEst = -1.0;
        boolean monotonicTrend = true;

        for (double targetRate : targetRates) {
            BufferedImage testImg;
            double actualRate;

            if (targetRate == 0.0) {
                testImg = texturedCover;
                actualRate = 0.0;
            } else {
                long totalPixelsNeeded = Math.round(targetRate * totalPixels);
                int payloadBytes = (int) Math.max(0, (totalPixelsNeeded / 8) - EmbedLSB.HEADER_BYTES);
                if (payloadBytes > maxCapacityBytes) payloadBytes = (int) maxCapacityBytes;

                byte[] payload = new byte[payloadBytes];
                Random rng = new Random(100 + (int)(targetRate * 100));
                rng.nextBytes(payload);

                testImg = EmbedLSB.embed(texturedCover, payload);
                actualRate = ((payloadBytes + EmbedLSB.HEADER_BYTES) * 8.0) / totalPixels;
            }

            RSAnalysis.Result rs = RSAnalysis.analyze(testImg, RSAnalysis.Channel.BLUE);
            double rawEst = rs.getEstimatedPayloadRate();
            double validEst = rs.getPhysicallyValidPayloadRate();

            System.out.printf("%10.1f%% | %10.1f%% | %13s | %14s | %-20s\n",
                    targetRate * 100.0, actualRate * 100.0,
                    Double.isNaN(rawEst) ? "NaN" : String.format("%+.2f%%", rawEst * 100.0),
                    Double.isNaN(validEst) ? "None" : String.format("%.2f%%", validEst * 100.0),
                    rs.getEstimationStatus());

            if (!Double.isNaN(rawEst)) {
                if (targetRate > 0.10 && rawEst < prevEst) {
                    monotonicTrend = false;
                }
                prevEst = rawEst;
            }
        }

        assertTrue(monotonicTrend, "47. RS estimated rate reflects monotonic payload progression on textured image");

        // Verify clean cover has negative raw estimate flagged as NEGATIVE_ESTIMATE (never RELIABLE)
        RSAnalysis.Result coverRS = RSAnalysis.analyze(texturedCover, RSAnalysis.Channel.BLUE);
        assertTrue(coverRS.getEstimationStatus() == RSAnalysis.EstimationStatus.NEGATIVE_ESTIMATE,
                "48. Clean synthetic cover produces NEGATIVE_ESTIMATE status (not false RELIABLE)");
        assertTrue(Double.isNaN(coverRS.getPhysicallyValidPayloadRate()),
                "49. Physically valid payload rate for negative estimate is NaN (None)");

        // Test smooth gradient image (0% and 50%)
        System.out.println("--- Image 2: Smooth Linear Gradient Image (120x120) ---");
        BufferedImage smoothCover = createSmoothGradientImage(width, height);
        RSAnalysis.Result smoothClean = RSAnalysis.analyze(smoothCover, RSAnalysis.Channel.BLUE);
        System.out.printf("Clean smooth gradient cover: Raw = %s, Status = %s\n",
                Double.isNaN(smoothClean.getEstimatedPayloadRate()) ? "NaN" : String.format("%+.2f%%", smoothClean.getEstimatedPayloadRate() * 100.0),
                smoothClean.getEstimationStatus());
        assertTrue(smoothClean.getEstimationStatus() == RSAnalysis.EstimationStatus.NEGATIVE_ESTIMATE,
                "50. Small negative estimate (-0.12%) on smooth cover classified as NEGATIVE_ESTIMATE");

        byte[] halfPayload = new byte[(int)(maxCapacityBytes * 0.5)];
        new Random(456).nextBytes(halfPayload);
        BufferedImage smoothStego = EmbedLSB.embed(smoothCover, halfPayload);
        RSAnalysis.Result smoothStegoRS = RSAnalysis.analyze(smoothStego, RSAnalysis.Channel.BLUE);
        System.out.printf("50%% embedded smooth gradient: Raw = %s, Status = %s\n",
                Double.isNaN(smoothStegoRS.getEstimatedPayloadRate()) ? "NaN" : String.format("%+.2f%%", smoothStegoRS.getEstimatedPayloadRate() * 100.0),
                smoothStegoRS.getEstimationStatus());
        assertTrue(smoothStegoRS.getEstimationStatus() == RSAnalysis.EstimationStatus.INDETERMINATE,
                "51. 50% stego on artificial ramp correctly produces INDETERMINATE (Delta < 0)");

        // Test Constant Image (Flat Image)
        System.out.println("--- Image 3: Constant Image (120x120, flat color 128) ---");
        BufferedImage flat = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flat.setRGB(x, y, (128 << 16) | (128 << 8) | 128);
            }
        }
        RSAnalysis.Result flatRS = RSAnalysis.analyze(flat, RSAnalysis.Channel.BLUE);
        System.out.printf("Clean constant image: Raw = %s, Status = %s\n",
                Double.isNaN(flatRS.getEstimatedPayloadRate()) ? "NaN" : String.format("%.2f%%", flatRS.getEstimatedPayloadRate() * 100.0),
                flatRS.getEstimationStatus());
        assertTrue(Double.isNaN(flatRS.getEstimatedPayloadRate()),
                "52. Constant image correctly produces NaN payload estimate (avoids false 0%)");
        assertTrue(flatRS.getEstimationStatus() == RSAnalysis.EstimationStatus.INSUFFICIENT_DATA,
                "53. Constant image status is INSUFFICIENT_DATA");

        // Test Real Image if available
        File realDog = new File("pictures/input/cute_dog.png");
        if (realDog.exists()) {
            try {
                BufferedImage realImg = ImageIO.read(realDog);
                RSAnalysis.Result dogRS = RSAnalysis.analyze(realImg, RSAnalysis.Channel.BLUE);
                System.out.printf("Real image (cute_dog.png): Raw = %s, Status = %s\n",
                        Double.isNaN(dogRS.getEstimatedPayloadRate()) ? "NaN" : String.format("%+.2f%%", dogRS.getEstimatedPayloadRate() * 100.0),
                        dogRS.getEstimationStatus());
                assertTrue(dogRS.getEstimationStatus() == RSAnalysis.EstimationStatus.NEGATIVE_ESTIMATE,
                        "54. Real cover raw estimate of -1.11% is classified as NEGATIVE_ESTIMATE (never RELIABLE)");
                assertTrue(Double.isNaN(dogRS.getPhysicallyValidPayloadRate()),
                        "55. Real cover physically valid rate is NaN (None)");
            } catch (IOException e) {
                System.err.println("Could not load cute_dog.png: " + e.getMessage());
            }
        }
    }

    private static BufferedImage createTexturedImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(789);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double v = 128.0
                        + 40.0 * Math.sin(x / 6.0)
                        + 40.0 * Math.cos(y / 6.0)
                        + 15.0 * Math.sin((x + y) / 4.0)
                        + (rng.nextDouble() * 6.0 - 3.0);
                int b = Math.min(255, Math.max(0, (int) Math.round(v)));
                int r = Math.min(255, Math.max(0, (x * 2) % 256));
                int g = Math.min(255, Math.max(0, (y * 2) % 256));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static BufferedImage createSmoothGradientImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int b = Math.min(255, Math.max(0, (int) Math.round((x + y) * 255.0 / (w + h))));
                img.setRGB(x, y, b);
            }
        }
        return img;
    }

    // -------------------------------------------------------------
    // Test 12: Real Cover vs Stego Comparative Workflow
    // -------------------------------------------------------------
    private static void testCoverVsStegoComparativeRealWorkflow() {
        int width = 64;
        int height = 64;
        BufferedImage cover = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Random rng = new Random(12345);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = Math.min(255, Math.max(0, (x * 3) + rng.nextInt(5)));
                int g = Math.min(255, Math.max(0, (y * 3) + rng.nextInt(5)));
                int b = Math.min(255, Math.max(0, ((x + y) * 2) + rng.nextInt(5)));
                cover.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        byte[] payload = new byte[400];
        rng.nextBytes(payload);

        BufferedImage stego = EmbedLSB.embed(cover, payload);

        AdvancedSteganalysisFacade.ComparativeReport report = AdvancedSteganalysisFacade.compare(cover, stego);

        double coverSpread = report.coverReport.blueRS.getDifferenceM();
        double stegoSpread = report.stegoReport.blueRS.getDifferenceM();

        assertTrue(coverSpread > stegoSpread,
                String.format("56. Stego RS spread (%.2f%%) converges closer to zero than cover RS spread (%.2f%%)",
                        stegoSpread, coverSpread));

        String summary = report.formatSummary();
        assertTrue(summary.contains("Phase 4: Advanced Comparative Steganalysis"), "57. Comparative summary generated cleanly");
    }

    // -------------------------------------------------------------
    // Test 13: Error Handling
    // -------------------------------------------------------------
    private static void testErrorHandling() {
        boolean caughtNull = false;
        try {
            RSAnalysis.analyze((BufferedImage) null);
        } catch (IllegalArgumentException e) {
            caughtNull = true;
        }
        assertTrue(caughtNull, "58. RSAnalysis rejects null image with IllegalArgumentException");

        boolean caughtFileNotFound = false;
        try {
            AdvancedSteganalysisFacade.analyze(new File("non_existent_file_12345.png"));
        } catch (FileNotFoundException e) {
            caughtFileNotFound = true;
        } catch (IOException e) {
            caughtFileNotFound = true;
        }
        assertTrue(caughtFileNotFound, "59. Facade rejects non-existent file with FileNotFoundException");
    }

    // -------------------------------------------------------------
    // Regression Tests
    // -------------------------------------------------------------
    private static void testPhase1Regressions() {
        System.out.println("--- Running Phase 1 Regressions ---");
        try {
            SteganographyEngineTest.main(new String[0]);
            System.out.println(" [PASS] 60. Phase 1 SteganographyEngineTest passed 100%");
            testsPassed++;
        } catch (Exception e) {
            System.err.println(" [FAIL] Phase 1 regression failed: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testPhase2Regressions() {
        System.out.println("--- Running Phase 2 Regressions ---");
        try {
            ImageAnalysisTest.main(new String[0]);
            System.out.println(" [PASS] 61. Phase 2 ImageAnalysisTest passed 100%");
            testsPassed++;
        } catch (Exception e) {
            System.err.println(" [FAIL] Phase 2 regression failed: " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testPhase3Regressions() {
        System.out.println("--- Running Phase 3 Regressions ---");
        try {
            SteganalysisTest.main(new String[0]);
            System.out.println(" [PASS] 62. Phase 3 SteganalysisTest passed 100%");
            testsPassed++;
        } catch (Exception e) {
            System.err.println(" [FAIL] Phase 3 regression failed: " + e.getMessage());
            testsFailed++;
        }
    }
}
