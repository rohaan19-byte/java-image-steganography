import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner myObj = new Scanner(System.in);
        System.out.println("What would you like to do?(A/B/C/D/E)\n"
                + "A:Write a hidden message to an image.\n"
                + "B:Read a hidden message from an image\n"
                + "C:Analyze / compare cover and stego images\n"
                + "D:Statistical steganalysis (LSB statistics & Chi-Square test)\n"
                + "E:Advanced steganalysis (RS Analysis & 8x8 DCT Frequency Analysis)");

        String action = myObj.nextLine();

        if (action.equalsIgnoreCase("A")) {
            System.out.println("What message would you like to hide?");
            String message = myObj.nextLine();

            System.out.println("Where is your image located? (eg. pictures\\image.png)");
            String input_path = myObj.nextLine();

            System.out.println("Where would you like to save the output image?");
            String output_path = myObj.nextLine();

            Generate_image(message, input_path, output_path);
        } else if (action.equalsIgnoreCase("B")) {

            System.out.println("Enter the secret keys with [].");
            String input = myObj.nextLine();
            String[] byteStrings = input.substring(1, input.length() - 1).split(", ");
            byte[] secretKeyBytes = new byte[byteStrings.length];
            for (int i = 0; i < byteStrings.length; i++) {
                secretKeyBytes[i] = Byte.parseByte(byteStrings[i]);
            }

            System.out.println("Enter the location of the image.");
            String output_path = myObj.nextLine();

            System.out.println("Enter the cipher algorithm. (e.g., \"AES\")");
            String cipher_algorithm = myObj.nextLine();

            Extract_text(secretKeyBytes, output_path, cipher_algorithm);
        } else if (action.equalsIgnoreCase("C")) {

            System.out.println("Enter the location of the original cover image:");
            String coverPath = myObj.nextLine();

            System.out.println("Enter the location of the stego image:");
            String stegoPath = myObj.nextLine();

            Analyze_images(coverPath, stegoPath, myObj);
        } else if (action.equalsIgnoreCase("D")) {

            System.out.println("Select Steganalysis Mode (1/2):");
            System.out.println("1: Analyze a single candidate image");
            System.out.println("2: Compare original cover image vs candidate image");
            String subChoice = myObj.nextLine().trim();

            if ("2".equals(subChoice)) {
                System.out.println("Enter location of original cover image:");
                String coverPath = myObj.nextLine();
                System.out.println("Enter location of candidate stego image:");
                String stegoPath = myObj.nextLine();

                SteganalysisFacade.ComparativeReport compReport = SteganalysisFacade.compare(new File(coverPath), new File(stegoPath));
                System.out.println(compReport.formatSummary());
            } else {
                System.out.println("Enter location of candidate image to analyze:");
                String imagePath = myObj.nextLine();

                SteganalysisFacade.SingleImageReport report = SteganalysisFacade.analyze(new File(imagePath));
                System.out.println(report.formatSummary());
            }
        } else if (action.equalsIgnoreCase("E")) {

            System.out.println("Select Advanced Steganalysis Mode (1/2):");
            System.out.println("1: Analyze a single candidate image (RS + DCT)");
            System.out.println("2: Compare original cover image vs candidate image");
            String subChoice = myObj.nextLine().trim();

            if ("2".equals(subChoice)) {
                System.out.println("Enter location of original cover image:");
                String coverPath = myObj.nextLine();
                System.out.println("Enter location of candidate stego image:");
                String stegoPath = myObj.nextLine();

                AdvancedSteganalysisFacade.ComparativeReport compReport = AdvancedSteganalysisFacade.compare(new File(coverPath), new File(stegoPath));
                System.out.println(compReport.formatSummary());
            } else {
                System.out.println("Enter location of candidate image to analyze:");
                String imagePath = myObj.nextLine();

                AdvancedSteganalysisFacade.SingleReport report = AdvancedSteganalysisFacade.analyze(new File(imagePath));
                System.out.println(report.formatSummary());
            }
        } else {
            System.out.println("Invalid input!");
        }
    }

    public static void Generate_image(String message, String input_path, String output_path) throws Exception {
        File imageFile = new File(input_path);

        TextEncryptor textEncryptor = new TextEncryptor();

        SecretKey key = textEncryptor.generateAESKey();
        Cipher cipher = textEncryptor.getCipher();

        byte[] secretKeyBytes = key.getEncoded();

        // Encrypting message and embedding it to an image
        String encryptedText = textEncryptor.encrypt(message, key, cipher);
        if (imageFile != null) {
            EmbedLSB.Embed(imageFile, encryptedText, output_path);
            System.out.println("Message is hidden successfully");
        }
        System.out.println("Your recipient need to know the secret key and the cipher algorithm name (e.g., \"AES\") to read you message.");
        System.out.println("SecretKey: " + Arrays.toString(secretKeyBytes));
        System.out.println("Cipher: " + cipher);
    }

    public static void Extract_text(byte[] secretKeyBytes, String output_path, String cipher_algorithm) throws Exception {
        SecretKey receivedSecretKey = new SecretKeySpec(secretKeyBytes, cipher_algorithm);
        Cipher receivedCipher = Cipher.getInstance(cipher_algorithm);
        receivedCipher.init(Cipher.DECRYPT_MODE, receivedSecretKey);
        // Extracting hidden and encrypted text and decrypting it
        ExtractLSB.Extract(output_path, receivedSecretKey, receivedCipher);
    }

    public static void Analyze_images(String coverPath, String stegoPath, Scanner scanner) throws Exception {
        File coverFile = new File(coverPath);
        File stegoFile = new File(stegoPath);

        ImageAnalysisFacade.AnalysisReport report = ImageAnalysisFacade.analyze(coverFile, stegoFile);
        System.out.println(report.formatSummary());

        System.out.println("Visual export options:");
        System.out.println("1: Generate amplified difference image");
        System.out.println("2: Generate RGB histogram comparison chart");
        System.out.println("3: Generate bit-plane visualization");
        System.out.println("Press Enter or any other key to skip visual exports.");

        if (scanner != null && scanner.hasNextLine()) {
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                System.out.println("Enter destination file for difference image (e.g. pictures\\diff.png):");
                String diffPath = scanner.nextLine();
                System.out.println("Enter amplification scale factor (default 255):");
                String scaleStr = scanner.nextLine().trim();
                int scale = scaleStr.isEmpty() ? DifferenceImageGenerator.DEFAULT_SCALE_FACTOR : Integer.parseInt(scaleStr);
                BufferedImage cover = ImageIO.read(coverFile);
                BufferedImage stego = ImageIO.read(stegoFile);
                DifferenceImageGenerator.saveDifferenceImage(cover, stego, new File(diffPath), scale);
                System.out.println("Difference image saved successfully to: " + diffPath);
            } else if ("2".equals(choice)) {
                System.out.println("Enter destination file for histogram chart (e.g. pictures\\histogram.png):");
                String histPath = scanner.nextLine();
                HistogramAnalyzer.saveChart(report.histogramResult, new File(histPath));
                System.out.println("Histogram chart saved successfully to: " + histPath);
            } else if ("3".equals(choice)) {
                System.out.println("Select channel (RED/GREEN/BLUE, default BLUE):");
                String chStr = scanner.nextLine().trim().toUpperCase();
                BitPlaneVisualizer.Channel channel = "RED".equals(chStr) ? BitPlaneVisualizer.Channel.RED :
                                                    "GREEN".equals(chStr) ? BitPlaneVisualizer.Channel.GREEN :
                                                    BitPlaneVisualizer.Channel.BLUE;
                System.out.println("Enter plane index (0 for LSB, 7 for MSB, default 0):");
                String planeStr = scanner.nextLine().trim();
                int plane = planeStr.isEmpty() ? 0 : Integer.parseInt(planeStr);
                System.out.println("Enter destination file for bit-plane comparison (e.g. pictures\\bitplane_comp.png):");
                String bpPath = scanner.nextLine();
                BufferedImage cover = ImageIO.read(coverFile);
                BufferedImage stego = ImageIO.read(stegoFile);
                BitPlaneVisualizer.saveComparison(cover, stego, channel, plane, new File(bpPath));
                System.out.println("Bit-plane comparison image saved successfully to: " + bpPath);
            }
        }
    }
}
