import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

/**
 * Automated test suite covering all 14 Phase 1 requirements and end-to-end integration.
 * Self-contained Java test runner executable with {@code java SteganographyEngineTest}.
 */
public class SteganographyEngineTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Starting Phase 1 Steganography Engine Test Suite");
        System.out.println("=================================================");

        runTest("1. Embed and extract normal text payload", SteganographyEngineTest::testEmbedExtractNormalText);
        runTest("2. Embed and extract empty payload", SteganographyEngineTest::testEmbedExtractEmptyPayload);
        runTest("3. Embed and extract payload containing 0x00 bytes", SteganographyEngineTest::testEmbedExtractPayloadWithNullBytes);
        runTest("4. Embed and extract non-ASCII / arbitrary binary bytes", SteganographyEngineTest::testEmbedExtractArbitraryBinaryBytes);
        runTest("5. Reject payload exceeding image capacity", SteganographyEngineTest::testRejectPayloadExceedingCapacity);
        runTest("6. Reject invalid magic header", SteganographyEngineTest::testRejectInvalidMagicHeader);
        runTest("7. Reject invalid or excessive declared payload length", SteganographyEngineTest::testRejectInvalidOrExcessiveDeclaredLength);
        runTest("8. Safely reject ordinary image with no valid payload frame", SteganographyEngineTest::testSafelyRejectOrdinaryImage);
        runTest("9. Confirm image dimensions remain unchanged", SteganographyEngineTest::testImageDimensionsUnchanged);
        runTest("10. Confirm alpha/transparency is preserved for ARGB images", SteganographyEngineTest::testAlphaTransparencyPreserved);
        runTest("11. Save and reopen PNG, verify payload intact", SteganographyEngineTest::testSaveAndReopenPngPayloadIntact);
        runTest("12. Verify insufficient capacity does not produce or alter output file", SteganographyEngineTest::testInsufficientCapacityDoesNotProduceOutputFile);
        runTest("13. Verify encoder and decoder agree on bit ordering and framing", SteganographyEngineTest::testBitOrderingAndFramingAgreement);
        runTest("14. Verify maximum payload calculation and sanity check (100x100 -> 1242 bytes)", SteganographyEngineTest::testMaximumPayloadCalculation);
        runTest("15. End-to-end AES-256 + Base64 embedding and extraction workflow", SteganographyEngineTest::testEndToEndAesBase64Integration);

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

    // --- Helper Assertion Methods ---

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(String.format("Assertion failed: %s (expected: <%s>, actual: <%s>)", message, expected, actual));
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(String.format("Assertion failed: %s (expected: %d, actual: %d)", message, expected, actual));
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(String.format("Assertion failed: %s (expected length %d, actual length %d)",
                    message, expected != null ? expected.length : -1, actual != null ? actual.length : -1));
        }
    }

    private static BufferedImage createTestImage(int width, int height, int type) {
        BufferedImage img = new BufferedImage(width, height, type);
        Random rng = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rng.nextInt(256);
                int g = rng.nextInt(256);
                int b = rng.nextInt(256);
                int a = type == BufferedImage.TYPE_INT_ARGB ? rng.nextInt(256) : 255;
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    // --- Test Implementations ---

    /**
     * 1. Embed and extract a normal text payload.
     */
    private static void testEmbedExtractNormalText() {
        BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
        String secretText = "Hello, Steganography World! 1234567890 !@#$%^&*()_+~`";
        byte[] payload = secretText.getBytes(StandardCharsets.UTF_8);

        BufferedImage stego = EmbedLSB.embed(cover, payload);
        byte[] extracted = ExtractLSB.extract(stego);

        assertArrayEquals(payload, extracted, "Extracted payload must match original byte-for-byte");
        String extractedText = new String(extracted, StandardCharsets.UTF_8);
        assertEquals(secretText, extractedText, "Extracted string must match original text");
    }

    /**
     * 2. Embed and extract an empty payload.
     */
    private static void testEmbedExtractEmptyPayload() {
        BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
        byte[] emptyPayload = new byte[0];

        BufferedImage stego = EmbedLSB.embed(cover, emptyPayload);
        byte[] extracted = ExtractLSB.extract(stego);

        assertEquals(0, extracted.length, "Extracted empty payload must have length 0");
    }

    /**
     * 3. Embed and extract payload bytes containing "0x00".
     */
    private static void testEmbedExtractPayloadWithNullBytes() {
        BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
        byte[] payloadWithZeros = new byte[] {
            0x00, 0x00, 0x01, 0x00, 0x02, (byte) 0xFF, 0x00, 0x00,
            0x41, 0x00, 0x42, 0x00, 0x00, 0x00, (byte) 0xAA, 0x00
        };

        BufferedImage stego = EmbedLSB.embed(cover, payloadWithZeros);
        byte[] extracted = ExtractLSB.extract(stego);

        assertArrayEquals(payloadWithZeros, extracted, "Null bytes (0x00) must be preserved exactly without being stripped");
        for (int i = 0; i < payloadWithZeros.length; i++) {
            assertEquals(payloadWithZeros[i], extracted[i], "Byte at index " + i + " must match");
        }
    }

    /**
     * 4. Embed and extract non-ASCII or arbitrary binary bytes.
     */
    private static void testEmbedExtractArbitraryBinaryBytes() {
        BufferedImage cover = createTestImage(120, 120, BufferedImage.TYPE_INT_RGB);
        byte[] allBytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            allBytes[i] = (byte) i;
        }

        BufferedImage stego = EmbedLSB.embed(cover, allBytes);
        byte[] extracted = ExtractLSB.extract(stego);

        assertArrayEquals(allBytes, extracted, "All 256 arbitrary binary byte values must be extracted intact");
    }

    /**
     * 5. Reject a payload that exceeds image capacity.
     */
    private static void testRejectPayloadExceedingCapacity() {
        // 100x100 has 10,000 pixels = 1250 bytes. Minus 8 bytes framing = 1242 max usable bytes.
        BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
        long maxCapacity = EmbedLSB.calculateMaxPayloadBytes(cover);
        assertEquals(1242L, maxCapacity, "Max capacity of 100x100 must be 1242 bytes");

        byte[] oversizedPayload = new byte[(int) maxCapacity + 1]; // 1243 bytes
        boolean exceptionThrown = false;
        try {
            EmbedLSB.embed(cover, oversizedPayload);
        } catch (InsufficientCapacityException e) {
            exceptionThrown = true;
            assertEquals(1243L, e.getRequestedBytes(), "Requested bytes reported must be 1243");
            assertEquals(1242L, e.getMaxUsableBytes(), "Max usable bytes reported must be 1242");
        }
        assertTrue(exceptionThrown, "Embedding oversized payload must throw InsufficientCapacityException");
    }

    /**
     * 6. Reject an invalid magic header.
     */
    private static void testRejectInvalidMagicHeader() {
        BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
        byte[] payload = "Test".getBytes(StandardCharsets.UTF_8);
        BufferedImage stego = EmbedLSB.embed(cover, payload);

        // Corrupt the first pixel's blue LSB (flips first bit of magic header)
        int rgb = stego.getRGB(0, 0);
        stego.setRGB(0, 0, rgb ^ 1);

        boolean rejected = false;
        try {
            ExtractLSB.extract(stego);
        } catch (InvalidStegoHeaderException e) {
            rejected = true;
            assertTrue(e.getMessage().contains("Expected 'STEG'"), "Exception must mention expected magic header 'STEG'");
        }
        assertTrue(rejected, "Extraction with corrupted magic header must throw InvalidStegoHeaderException");
    }

    /**
     * 7. Reject an invalid or excessive declared payload length.
     */
    private static void testRejectInvalidOrExcessiveDeclaredLength() {
        BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);
        BufferedImage stego = EmbedLSB.embed(cover, payload);

        // Corrupt the length field bytes (bytes 4..7 in framing = pixels 32..63)
        // Set declared length to 999999 (far beyond capacity of 1242)
        int excessiveLength = 999999;
        int width = stego.getWidth();
        for (int bit = 31; bit >= 0; bit--) {
            int bitVal = (excessiveLength >>> bit) & 1;
            int pixelIndex = 32 + (31 - bit);
            int x = pixelIndex % width;
            int y = pixelIndex / width;
            int rgb = stego.getRGB(x, y);
            stego.setRGB(x, y, (rgb & ~1) | bitVal);
        }

        boolean rejectedExcessive = false;
        try {
            ExtractLSB.extract(stego);
        } catch (InvalidStegoHeaderException e) {
            rejectedExcessive = true;
            assertTrue(e.getMessage().contains("exceeds maximum image capacity"),
                    "Exception must mention length exceeding capacity");
        }
        assertTrue(rejectedExcessive, "Extraction with excessive declared length must throw InvalidStegoHeaderException");

        // Now test negative length (-1)
        int negativeLength = -1;
        for (int bit = 31; bit >= 0; bit--) {
            int bitVal = (negativeLength >>> bit) & 1;
            int pixelIndex = 32 + (31 - bit);
            int x = pixelIndex % width;
            int y = pixelIndex / width;
            int rgb = stego.getRGB(x, y);
            stego.setRGB(x, y, (rgb & ~1) | bitVal);
        }

        boolean rejectedNegative = false;
        try {
            ExtractLSB.extract(stego);
        } catch (InvalidStegoHeaderException e) {
            rejectedNegative = true;
            assertTrue(e.getMessage().contains("negative length"), "Exception must mention negative length");
        }
        assertTrue(rejectedNegative, "Extraction with negative declared length must throw InvalidStegoHeaderException");
    }

    /**
     * 8. Safely reject an ordinary image with no valid payload frame.
     */
    private static void testSafelyRejectOrdinaryImage() throws IOException {
        // Test with unencoded synthetic image
        BufferedImage ordinaryImage = createTestImage(80, 80, BufferedImage.TYPE_INT_RGB);
        boolean rejectedSynthetic = false;
        try {
            ExtractLSB.extract(ordinaryImage);
        } catch (InvalidStegoHeaderException e) {
            rejectedSynthetic = true;
        }
        assertTrue(rejectedSynthetic, "Ordinary image without stego frame must be safely rejected with InvalidStegoHeaderException");

        // Test with real committed input image if available
        File cuteDog = new File("pictures/input/cute_dog.png");
        if (cuteDog.exists()) {
            boolean rejectedDog = false;
            try {
                ExtractLSB.extract(cuteDog);
            } catch (InvalidStegoHeaderException e) {
                rejectedDog = true;
            }
            assertTrue(rejectedDog, "Cover image cute_dog.png must be safely rejected without crashing");
        }
    }

    /**
     * 9. Confirm that image dimensions remain unchanged.
     */
    private static void testImageDimensionsUnchanged() {
        int expectedWidth = 137;
        int expectedHeight = 93;
        BufferedImage cover = createTestImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_RGB);
        byte[] payload = "Dimension Test".getBytes(StandardCharsets.UTF_8);

        BufferedImage stego = EmbedLSB.embed(cover, payload);

        assertEquals(expectedWidth, stego.getWidth(), "Stego image width must remain exactly unchanged");
        assertEquals(expectedHeight, stego.getHeight(), "Stego image height must remain exactly unchanged");
    }

    /**
     * 10. Confirm that alpha/transparency is preserved for supported transparent images.
     */
    private static void testAlphaTransparencyPreserved() throws IOException {
        int width = 80;
        int height = 80;
        BufferedImage transparentCover = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Fill with distinct alpha values at specific coordinates
        // Pixel (0, 0): Fully transparent (alpha = 0)
        transparentCover.setRGB(0, 0, (0 << 24) | (100 << 16) | (150 << 8) | 200);
        // Pixel (1, 0): Quarter transparent (alpha = 64)
        transparentCover.setRGB(1, 0, (64 << 24) | (110 << 16) | (160 << 8) | 210);
        // Pixel (2, 0): Semi-transparent (alpha = 128)
        transparentCover.setRGB(2, 0, (128 << 24) | (120 << 16) | (170 << 8) | 220);
        // Pixel (3, 0): Fully opaque (alpha = 255)
        transparentCover.setRGB(3, 0, (255 << 24) | (130 << 16) | (180 << 8) | 230);

        byte[] payload = "Transparency preservation test".getBytes(StandardCharsets.UTF_8);
        BufferedImage stego = EmbedLSB.embed(transparentCover, payload);

        // Verify in-memory alpha preservation
        assertEquals(0, (stego.getRGB(0, 0) >>> 24) & 0xFF, "Alpha at (0,0) must remain 0");
        assertEquals(64, (stego.getRGB(1, 0) >>> 24) & 0xFF, "Alpha at (1,0) must remain 64");
        assertEquals(128, (stego.getRGB(2, 0) >>> 24) & 0xFF, "Alpha at (2,0) must remain 128");
        assertEquals(255, (stego.getRGB(3, 0) >>> 24) & 0xFF, "Alpha at (3,0) must remain 255");

        // Verify disk-persisted PNG reload preserves alpha
        File tempFile = File.createTempFile("transparency_test_", ".png");
        try {
            ImageIO.write(stego, "png", tempFile);
            BufferedImage reloaded = ImageIO.read(tempFile);

            assertEquals(0, (reloaded.getRGB(0, 0) >>> 24) & 0xFF, "Reloaded alpha at (0,0) must remain 0");
            assertEquals(64, (reloaded.getRGB(1, 0) >>> 24) & 0xFF, "Reloaded alpha at (1,0) must remain 64");
            assertEquals(128, (reloaded.getRGB(2, 0) >>> 24) & 0xFF, "Reloaded alpha at (2,0) must remain 128");
            assertEquals(255, (reloaded.getRGB(3, 0) >>> 24) & 0xFF, "Reloaded alpha at (3,0) must remain 255");

            // Also verify payload extracts cleanly from reloaded transparent PNG
            byte[] extracted = ExtractLSB.extract(reloaded);
            assertArrayEquals(payload, extracted, "Payload extracted from transparent PNG must match");
        } finally {
            tempFile.delete();
        }
    }

    /**
     * 11. Save and reopen the PNG, then verify that the payload remains intact.
     */
    private static void testSaveAndReopenPngPayloadIntact() throws IOException {
        File tempCover = File.createTempFile("cover_test_", ".png");
        File tempStego = File.createTempFile("stego_test_", ".png");
        try {
            BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(cover, "png", tempCover);

            byte[] originalPayload = "PNG lossless disk round-trip verification payload.".getBytes(StandardCharsets.UTF_8);
            EmbedLSB.embed(tempCover, originalPayload, tempStego);

            assertTrue(tempStego.exists(), "Output stego file must exist on disk");
            assertTrue(tempStego.length() > 0, "Output stego file must not be empty");

            byte[] extractedPayload = ExtractLSB.extract(tempStego);
            assertArrayEquals(originalPayload, extractedPayload, "Extracted payload from reopened PNG must match original exactly");
        } finally {
            tempCover.delete();
            tempStego.delete();
        }
    }

    /**
     * 12. Verify that insufficient capacity does not produce a misleading or partially valid output file.
     */
    private static void testInsufficientCapacityDoesNotProduceOutputFile() throws IOException {
        File tempCover = File.createTempFile("cover_cap_", ".png");
        File nonExistentOutput = new File(tempCover.getParentFile(), "should_not_exist_" + System.currentTimeMillis() + ".png");

        try {
            BufferedImage cover = createTestImage(50, 50, BufferedImage.TYPE_INT_RGB); // 2500 pixels = 312 bytes = 304 max payload
            ImageIO.write(cover, "png", tempCover);

            byte[] tooBig = new byte[500]; // 500 > 304 bytes

            boolean thrown = false;
            try {
                EmbedLSB.embed(tempCover, tooBig, nonExistentOutput);
            } catch (InsufficientCapacityException e) {
                thrown = true;
            }
            assertTrue(thrown, "Insufficient capacity must throw InsufficientCapacityException");
            assertTrue(!nonExistentOutput.exists(), "Failed embedding must NEVER create an output file on disk");
        } finally {
            tempCover.delete();
            if (nonExistentOutput.exists()) {
                nonExistentOutput.delete();
            }
        }
    }

    /**
     * 13. Verify that the encoder and decoder agree on bit ordering and framing.
     */
    private static void testBitOrderingAndFramingAgreement() {
        BufferedImage cover = createTestImage(60, 60, BufferedImage.TYPE_INT_RGB);
        byte[] payload = new byte[] { (byte) 0b10101010, (byte) 0b11001100, (byte) 0b11110000 };

        BufferedImage stego = EmbedLSB.embed(cover, payload);

        // Verify framing manually:
        // First 32 pixels (indices 0..31) must correspond to "STEG"
        int width = stego.getWidth();
        byte[] manualMagic = new byte[4];
        for (int i = 0; i < 4; i++) {
            int b = 0;
            for (int bit = 7; bit >= 0; bit--) {
                int pixelIndex = i * 8 + (7 - bit);
                int x = pixelIndex % width;
                int y = pixelIndex / width;
                int lsb = stego.getRGB(x, y) & 1;
                b = (b << 1) | lsb;
            }
            manualMagic[i] = (byte) b;
        }
        assertArrayEquals(new byte[] { 'S', 'T', 'E', 'G' }, manualMagic, "First 4 bytes in pixels must be 'STEG'");

        // Next 4 bytes (indices 32..63) must correspond to length 3
        int manualLength = 0;
        for (int bit = 31; bit >= 0; bit--) {
            int pixelIndex = 32 + (31 - bit);
            int x = pixelIndex % width;
            int y = pixelIndex / width;
            int lsb = stego.getRGB(x, y) & 1;
            manualLength = (manualLength << 1) | lsb;
        }
        assertEquals(3, manualLength, "Length encoded in pixels 32..63 must be 3");

        // Extractor output must match
        byte[] extracted = ExtractLSB.extract(stego);
        assertArrayEquals(payload, extracted, "Extractor must produce exact matching payload");
    }

    /**
     * 14. Verify that the maximum payload calculation is correct.
     */
    private static void testMaximumPayloadCalculation() {
        // Sanity check from specification:
        // 100 x 100 image has 10,000 usable blue-channel bits = 1,250 bytes before overhead.
        // With 8-byte framing overhead, maximum payload is 1,242 bytes.
        long cap100x100 = EmbedLSB.calculateMaxPayloadBytes(100, 100);
        assertEquals(1242L, cap100x100, "100x100 capacity must equal exactly 1242 bytes");

        // Exact boundary: 64 pixels = 8 bytes = 0 bytes payload (just enough for header)
        assertEquals(0L, EmbedLSB.calculateMaxPayloadBytes(8, 8), "64 pixels must yield 0 bytes payload");

        // Below header threshold: 63 pixels < 64
        assertEquals(0L, EmbedLSB.calculateMaxPayloadBytes(9, 7), "63 pixels must yield 0 bytes payload");
        assertEquals(0L, EmbedLSB.calculateMaxPayloadBytes(1, 1), "1 pixel must yield 0 bytes payload");
        assertEquals(0L, EmbedLSB.calculateMaxPayloadBytes(0, 100), "0 dimension must yield 0 bytes");

        // Verify arithmetic prevents 32-bit signed integer overflow on very large dimensions:
        // 50,000 x 50,000 = 2,500,000,000 pixels (exceeds Integer.MAX_VALUE = 2,147,483,647)
        // 2,500,000,000 / 8 = 312,500,000 bytes. Minus 8 = 312,499,992 bytes.
        long largeCap = EmbedLSB.calculateMaxPayloadBytes(50000, 50000);
        assertEquals(312499992L, largeCap, "Large dimension capacity must not overflow 32-bit int");
    }

    /**
     * 15. End-to-end AES-256 + Base64 embedding and extraction workflow.
     */
    private static void testEndToEndAesBase64Integration() throws Exception {
        File tempCover = File.createTempFile("aes_cover_", ".png");
        File tempStego = File.createTempFile("aes_stego_", ".png");

        try {
            BufferedImage cover = createTestImage(100, 100, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(cover, "png", tempCover);

            String originalSecretMessage = "This is a top-secret message encrypted with AES-256 and embedded into a PNG!";

            // 1. Encrypt with TextEncryptor (AES-256 -> Base64)
            TextEncryptor encryptor = new TextEncryptor();
            SecretKey secretKey = encryptor.generateAESKey();
            Cipher cipher = encryptor.getCipher();
            String encryptedBase64 = encryptor.encrypt(originalSecretMessage, secretKey, cipher);

            // 2. Embed into image file using EmbedLSB.Embed (existing signature)
            EmbedLSB.Embed(tempCover, encryptedBase64, tempStego.getAbsolutePath());

            // 3. Extract and decrypt using ExtractLSB.extractAndDecrypt
            String decryptedMessage = ExtractLSB.extractAndDecrypt(tempStego, secretKey, cipher);

            assertEquals(originalSecretMessage, decryptedMessage,
                    "End-to-end decrypted message must match original secret message exactly");
        } finally {
            tempCover.delete();
            tempStego.delete();
        }
    }
}
