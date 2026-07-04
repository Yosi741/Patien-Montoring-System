package pages.certificate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

public class PdfCertificateWriter {

    public static void writeCertificate(File file, String title, List<String> lines) throws Exception {
        writeCertificate(file, title, lines, "");
    }

    public static void writeCertificate(File file, String title, List<String> lines, String signatureImagePath) throws Exception {
        file.getParentFile().mkdirs();
        BufferedImage signature = loadSignature(signatureImagePath);

        StringBuilder content = new StringBuilder();
        content.append("BT\n");
        content.append("/F1 18 Tf\n");
        content.append("70 770 Td\n");
        content.append("(").append(escape(title)).append(") Tj\n");
        content.append("/F1 11 Tf\n");
        content.append("0 -30 Td\n");

        int lineCount = 0;
        for (String line : lines) {
            if (lineCount > 0) {
                content.append("0 -18 Td\n");
            }
            content.append("(").append(escape(line)).append(") Tj\n");
            lineCount++;
            if (lineCount >= 36) {
                content.append("0 -18 Td\n");
                content.append("(Additional details stored in patient record.) Tj\n");
                break;
            }
        }
        content.append("ET\n");
        if (signature != null) {
            content.append("q\n");
            content.append("130 45 170 55 re S\n");
            content.append("170 58 95 35 cm\n");
            content.append("/Im1 Do\n");
            content.append("Q\n");
        }

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII));
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.US_ASCII));
        String resources = signature == null
                ? "<< /Font << /F1 4 0 R >> >>"
                : "<< /Font << /F1 4 0 R >> /XObject << /Im1 6 0 R >> >>";
        objects.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources " + resources + " /Contents 5 0 R >>").getBytes(StandardCharsets.US_ASCII));
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>".getBytes(StandardCharsets.US_ASCII));
        objects.add(("<< /Length " + contentBytes.length + " >>\nstream\n" + content + "endstream").getBytes(StandardCharsets.US_ASCII));
        if (signature != null) {
            ImageData imageData = imageData(signature);
            objects.add(("<< /Type /XObject /Subtype /Image /Width " + imageData.width
                    + " /Height " + imageData.height
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                    + imageData.bytes.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));

        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(output.size());
            output.write(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
            if (signature != null && i == 5) {
                ImageData imageData = imageData(signature);
                output.write(objects.get(i));
                output.write(imageData.bytes);
                output.write("\nendstream".getBytes(StandardCharsets.US_ASCII));
            } else {
                output.write(objects.get(i));
            }
            output.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }

        int xrefOffset = output.size();
        output.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes(StandardCharsets.US_ASCII));
        output.write("0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (Integer offset : offsets) {
            output.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n").getBytes(StandardCharsets.US_ASCII));
        output.write(("startxref\n" + xrefOffset + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));

        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(output.toByteArray());
        fileOutputStream.close();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private static BufferedImage loadSignature(String path) {
        try {
            if (path == null || path.isBlank()) {
                return null;
            }
            return ImageIO.read(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    private static ImageData imageData(BufferedImage image) throws Exception {
        int maxWidth = 300;
        int width = Math.min(image.getWidth(), maxWidth);
        int height = Math.max(1, (int) ((double) image.getHeight() * width / image.getWidth()));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        scaled.getGraphics().drawImage(image, 0, 0, width, height, null);

        byte[] raw = new byte[width * height * 3];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = scaled.getRGB(x, y);
                raw[index++] = (byte) ((rgb >> 16) & 0xFF);
                raw[index++] = (byte) ((rgb >> 8) & 0xFF);
                raw[index++] = (byte) (rgb & 0xFF);
            }
        }

        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[raw.length + 512];
        int length = deflater.deflate(buffer);
        byte[] compressed = new byte[length];
        System.arraycopy(buffer, 0, compressed, 0, length);
        return new ImageData(width, height, compressed);
    }

    private static class ImageData {
        int width;
        int height;
        byte[] bytes;

        ImageData(int width, int height, byte[] bytes) {
            this.width = width;
            this.height = height;
            this.bytes = bytes;
        }
    }
}
