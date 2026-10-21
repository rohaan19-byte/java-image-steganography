import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Computes and compares separate 256-bin Red, Green, and Blue intensity histograms
 * for original cover and steganographic images.
 *
 * <p><b>Important Security Notice:</b>
 * Histogram comparison is a basic exploratory and diagnostic tool. Similar or visually
 * indistinguishable histograms do <b>NOT</b> prove that an image is secure or that
 * steganography is undetectable.
 */
public class HistogramAnalyzer {

    /**
     * Holds 256-bin histograms for Red, Green, and Blue channels of an image.
     */
    public static class ChannelHistogram {
        private final int[] red = new int[256];
        private final int[] green = new int[256];
        private final int[] blue = new int[256];
        private final long totalPixels;

        public ChannelHistogram(BufferedImage image) {
            if (image == null) {
                throw new IllegalArgumentException("Image cannot be null.");
            }
            int w = image.getWidth();
            int h = image.getHeight();
            this.totalPixels = (long) w * h;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    red[r]++;
                    green[g]++;
                    blue[b]++;
                }
            }
        }

        public int[] getRed() { return red.clone(); }
        public int[] getGreen() { return green.clone(); }
        public int[] getBlue() { return blue.clone(); }
        public long getTotalPixels() { return totalPixels; }
    }

    /**
     * Holds comparative analysis between original and stego histograms.
     */
    public static class ComparisonResult {
        private final ChannelHistogram original;
        private final ChannelHistogram stego;

        private final int[] redDiff = new int[256];
        private final int[] greenDiff = new int[256];
        private final int[] blueDiff = new int[256];

        private int maxRedDiff = 0;
        private int maxGreenDiff = 0;
        private int maxBlueDiff = 0;

        private long totalAbsRedDiff = 0;
        private long totalAbsGreenDiff = 0;
        private long totalAbsBlueDiff = 0;

        public ComparisonResult(ChannelHistogram original, ChannelHistogram stego) {
            this.original = original;
            this.stego = stego;

            for (int i = 0; i < 256; i++) {
                redDiff[i] = stego.red[i] - original.red[i];
                greenDiff[i] = stego.green[i] - original.green[i];
                blueDiff[i] = stego.blue[i] - original.blue[i];

                int absR = Math.abs(redDiff[i]);
                int absG = Math.abs(greenDiff[i]);
                int absB = Math.abs(blueDiff[i]);

                if (absR > maxRedDiff) maxRedDiff = absR;
                if (absG > maxGreenDiff) maxGreenDiff = absG;
                if (absB > maxBlueDiff) maxBlueDiff = absB;

                totalAbsRedDiff += absR;
                totalAbsGreenDiff += absG;
                totalAbsBlueDiff += absB;
            }
        }

        public ChannelHistogram getOriginal() { return original; }
        public ChannelHistogram getStego() { return stego; }
        public int[] getRedDiff() { return redDiff.clone(); }
        public int[] getGreenDiff() { return greenDiff.clone(); }
        public int[] getBlueDiff() { return blueDiff.clone(); }

        public int getMaxRedDiff() { return maxRedDiff; }
        public int getMaxGreenDiff() { return maxGreenDiff; }
        public int getMaxBlueDiff() { return maxBlueDiff; }

        public long getTotalAbsRedDiff() { return totalAbsRedDiff; }
        public long getTotalAbsGreenDiff() { return totalAbsGreenDiff; }
        public long getTotalAbsBlueDiff() { return totalAbsBlueDiff; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== RGB Histogram Comparison Summary ===\n");
            sb.append(String.format("Total Pixels Evaluated: %d\n", original.totalPixels));
            sb.append(String.format("Red Channel   : Max bin diff = %d, Total absolute bin diff = %d\n", maxRedDiff, totalAbsRedDiff));
            sb.append(String.format("Green Channel : Max bin diff = %d, Total absolute bin diff = %d\n", maxGreenDiff, totalAbsGreenDiff));
            sb.append(String.format("Blue Channel  : Max bin diff = %d, Total absolute bin diff = %d\n", maxBlueDiff, totalAbsBlueDiff));
            sb.append("Note: Preserved histograms do not prove an image is secure or that steganography is undetectable.");
            return sb.toString();
        }
    }

    /**
     * Compares original and stego image histograms.
     *
     * @param original original cover image
     * @param stego    stego image
     * @return ComparisonResult containing bin-by-bin counts and differential statistics
     */
    public static ComparisonResult compare(BufferedImage original, BufferedImage stego) {
        if (original == null || stego == null) {
            throw new IllegalArgumentException("Images cannot be null.");
        }
        if (original.getWidth() != stego.getWidth() || original.getHeight() != stego.getHeight()) {
            throw new DimensionMismatchException(original.getWidth(), original.getHeight(),
                    stego.getWidth(), stego.getHeight());
        }
        return new ComparisonResult(new ChannelHistogram(original), new ChannelHistogram(stego));
    }

    /**
     * Generates a lightweight visual PNG chart rendering the 3-channel RGB histograms
     * for both original and stego images using built-in Java2D Graphics.
     *
     * @param result ComparisonResult containing histogram data
     * @return BufferedImage containing visual histogram comparison chart
     */
    public static BufferedImage renderChart(ComparisonResult result) {
        int width = 800;
        int height = 500;
        BufferedImage chart = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = chart.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dark background
            g.setColor(new Color(25, 27, 34));
            g.fillRect(0, 0, width, height);

            // Title
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString("RGB Histogram Comparison: Original vs Stego", 20, 30);

            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(180, 180, 190));
            g.drawString("Top: Original Cover | Bottom: Stego Image | Blue channel changes indicate LSB embedding", 20, 50);

            // Find global max bin count for scaling
            int maxCount = 1;
            for (int i = 0; i < 256; i++) {
                maxCount = Math.max(maxCount, result.original.red[i]);
                maxCount = Math.max(maxCount, result.original.green[i]);
                maxCount = Math.max(maxCount, result.original.blue[i]);
                maxCount = Math.max(maxCount, result.stego.red[i]);
                maxCount = Math.max(maxCount, result.stego.green[i]);
                maxCount = Math.max(maxCount, result.stego.blue[i]);
            }

            int plotWidth = 720;
            int plotHeight = 160;
            int xOffset = 40;

            // Plot Original (y: 80 to 240)
            int yOrig = 80;
            drawHistogramSubplot(g, result.original, xOffset, yOrig, plotWidth, plotHeight, maxCount, "Original Cover Image");

            // Plot Stego (y: 280 to 440)
            int yStego = 280;
            drawHistogramSubplot(g, result.stego, xOffset, yStego, plotWidth, plotHeight, maxCount, "Stego Image");

            // Disclaimer footer
            g.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g.setColor(new Color(150, 150, 160));
            g.drawString("Note: Histogram similarity does not prove security or absence of steganography.", 40, 480);

        } finally {
            g.dispose();
        }

        return chart;
    }

    private static void drawHistogramSubplot(Graphics2D g, ChannelHistogram hist, int x, int y, int w, int h, int maxVal, String title) {
        g.setColor(new Color(40, 44, 52));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(70, 75, 85));
        g.drawRect(x, y, w, h);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(title, x + 10, y + 20);

        // Draw curves for Red, Green, Blue
        drawChannelCurve(g, hist.red, x, y, w, h, maxVal, new Color(255, 85, 85, 180));
        drawChannelCurve(g, hist.green, x, y, w, h, maxVal, new Color(85, 255, 85, 180));
        drawChannelCurve(g, hist.blue, x, y, w, h, maxVal, new Color(85, 130, 255, 220));
    }

    private static void drawChannelCurve(Graphics2D g, int[] bins, int x, int y, int w, int h, int maxVal, Color color) {
        g.setColor(color);
        for (int i = 0; i < 255; i++) {
            int x1 = x + (int) ((i / 255.0) * w);
            int y1 = y + h - (int) ((bins[i] / (double) maxVal) * (h - 30));
            int x2 = x + (int) (((i + 1) / 255.0) * w);
            int y2 = y + h - (int) ((bins[i + 1] / (double) maxVal) * (h - 30));
            g.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * Saves the histogram comparison chart as a PNG file.
     *
     * @param result     ComparisonResult
     * @param outputFile destination PNG file
     * @throws IOException if saving fails
     */
    public static void saveChart(ComparisonResult result, File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null.");
        }
        BufferedImage chart = renderChart(result);

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        boolean written = ImageIO.write(chart, "png", outputFile);
        if (!written) {
            throw new IOException("Failed to save histogram chart: No PNG ImageIO writer found.");
        }
    }
}
