package com.runmoni.hostpreview;

import android.graphics.Bitmap;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public final class PreviewImages {
    public static void png(Bitmap bitmap, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(bitmap.getImage(), "png", path.toFile())) throw new IOException("PNG writer unavailable");
    }
    public static void sheet(List<Bitmap> frames, List<String> labels, int columns, Path path) throws IOException {
        int width = 256, height = 412;
        BufferedImage sheet = new BufferedImage(columns * width, ((frames.size() + columns - 1) / columns) * height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(0xf3eee5)); graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            for (int index = 0; index < frames.size(); index++) {
                int x = (index % columns) * width, y = (index / columns) * height;
                graphics.drawImage(frames.get(index).getImage(), x, y, null);
                graphics.setColor(new Color(0x172238));
                graphics.drawString(labels.get(index), x + 6, y + 399);
                graphics.setColor(new Color(0xd5cbbd)); graphics.drawRect(x, y, width - 1, height - 1);
            }
        } finally { graphics.dispose(); }
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(sheet, "png", path.toFile())) throw new IOException("PNG sheet writer unavailable");
    }
    /** GIF is a convenience preview; lossless PNGs remain the comparison evidence. */
    public static void gif(List<Bitmap> frames, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output); writer.prepareWriteSequence(null);
            for (int index = 0; index < frames.size(); index++) {
                BufferedImage image = new BufferedImage(256, 384, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.setColor(new Color(0xf3eee5)); graphics.fillRect(0, 0, 256, 384);
                    graphics.drawImage(frames.get(index).getImage(), 0, 0, null);
                } finally { graphics.dispose(); }
                IIOMetadata metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode tree = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = node(tree, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none"); control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE"); control.setAttribute("delayTime", "9");
                control.setAttribute("transparentColorIndex", "0");
                if (index == 0) {
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE"); loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[] {1, 0, 0}); node(tree, "ApplicationExtensions").appendChild(loop);
                }
                metadata.setFromTree(format, tree);
                writer.writeToSequence(new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }
    private static IIOMetadataNode node(IIOMetadataNode parent, String name) {
        for (int index = 0; index < parent.getLength(); index++)
            if (parent.item(index).getNodeName().equals(name)) return (IIOMetadataNode) parent.item(index);
        IIOMetadataNode result = new IIOMetadataNode(name); parent.appendChild(result); return result;
    }
}
