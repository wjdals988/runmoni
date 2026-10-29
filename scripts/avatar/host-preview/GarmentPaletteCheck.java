package com.runmoni.hostpreview;

import com.runmoni.app.avatar.GarmentPalette;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Executes the production pixel engine on actual assets; never an Android capture. */
public final class GarmentPaletteCheck {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static int[] pixels(BufferedImage im) { return im.getRGB(0, 0, im.getWidth(), im.getHeight(), null, 0, im.getWidth()); }
    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private static BufferedImage image(int[] data, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, w, h, data, 0, w); return im;
    }
    private static void reject(Runnable f) {
        try { f.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed mask was accepted");
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), out = Path.of(args[1]); Files.createDirectories(out);
        Path assets = root.resolve("app/src/main/assets/avatars/reference-review");
        Path approved = assets.resolve("approved-running.png"), proposal = assets.resolve("swimwear-proposal.png");
        check(hash(approved).equals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"), "Approved source changed");
        check(hash(proposal).equals("bc95bd8fcfb7e0dbddf302ee0fee5828990c3c251c0bbd59d45be64fbb89fb34"), "Proposal source changed");
        check(Files.mismatch(approved, root.resolve("docs/design/avatars/v0.3.0/running-motion-art-source.png")) == -1, "Bundled source differs");
        BufferedImage sourceImage = ImageIO.read(proposal.toFile());
        BufferedImage maskImage = ImageIO.read(assets.resolve("swimwear-mask.png").toFile());
        check(sourceImage.getWidth() == maskImage.getWidth() && sourceImage.getHeight() == maskImage.getHeight(), "Mask dimensions");
        int[] source = pixels(sourceImage), mask = pixels(maskImage), before = source.clone();
        check(Arrays.equals(source, GarmentPalette.recolor(source, mask, 0, 0, 0)), "Original mode must be exact");
        int[] counts = new int[4];
        for (int i = 0; i < mask.length; i++) if ((mask[i] >>> 24) != 0) {
            int region = (mask[i] >>> 16) & 255; check(region >= 1 && region <= 3, "Region bounds"); counts[region]++;
            int y = i / sourceImage.getWidth(), x = i % sourceImage.getWidth();
            check((source[i] >>> 24) >= 240, "Mask reaches transparency");
            check(region == 1 ? x >= 221 && x <= 506 && y >= 602 && y <= 817 :
                x >= 1017 && x <= 1240 && y >= 475 && y <= 741, "Mask outside explicit garment bounds");
        }
        int combinations = 0;
        for (int shorts = 0; shorts < 6; shorts++) for (int top = 0; top < 6; top++) for (int bottom = 0; bottom < 6; bottom++) {
            int[] selected = {0, shorts, top, bottom};
            int[] result = GarmentPalette.recolor(source, mask, shorts, top, bottom);
            for (int i = 0; i < result.length; i++) {
                check((source[i] >>> 24) == (result[i] >>> 24), "Alpha changed");
                int region = (mask[i] >>> 16) & 255;
                if ((mask[i] >>> 24) == 0 || selected[region] == 0) check(result[i] == source[i], "Protected pixel changed");
            }
            combinations++;
        }
        for (int region = 1; region <= 3; region++) {
            int[] result = GarmentPalette.recolor(source, mask, region == 1 ? 3 : 0, region == 2 ? 3 : 0, region == 3 ? 3 : 0);
            int changed = 0;
            for (int i = 0; i < result.length; i++) if (result[i] != source[i]) {
                check(((mask[i] >>> 16) & 255) == region, "Color leaked to another garment"); changed++;
            }
            check(changed == counts[region], "Masked fabric did not change");
        }
        check(Arrays.equals(before, source), "Source array mutated");
        check(Arrays.equals(source, GarmentPalette.recolor(source, mask, -1, 999, Integer.MIN_VALUE)), "Invalid saved palette not normalized");
        reject(() -> GarmentPalette.recolor(new int[1], new int[0], 1, 1, 1));
        for (int invalid : new int[]{0xff040000, 0xff000000, 0xff010001, 0x80010000})
            reject(() -> GarmentPalette.recolor(new int[]{0xffffffff}, new int[]{invalid}, 1, 1, 1));

        BufferedImage sheet = ImageIO.read(approved.toFile()); int[] all = pixels(sheet);
        var frames = new ArrayList<android.graphics.Bitmap>();
        var unique = new HashSet<Integer>();
        for (int frame = 0; frame < 8; frame++) {
            int[] pair = GarmentPalette.referencePair(all, 2048, 768, frame); unique.add(Arrays.hashCode(pair));
            for (int y = 0; y < 384; y++) for (int x = 0; x < 256; x++) {
                check(pair[y * 512 + x] == all[y * 2048 + frame * 256 + x], "Male crop changed");
                check(pair[y * 512 + x + 256] == all[(y + 384) * 2048 + frame * 256 + x], "Female crop changed");
            }
            frames.add(new android.graphics.Bitmap(image(pair, 512, 384)));
        }
        check(unique.size() == 8, "Eight distinct crops missing");
        check(Arrays.equals(GarmentPalette.referencePair(all, 2048, 768, -1), GarmentPalette.referencePair(all, 2048, 768, 7)), "Negative wrap");
        check(Arrays.equals(GarmentPalette.referencePair(all, 2048, 768, 8), GarmentPalette.referencePair(all, 2048, 768, 0)), "Loop wrap");
        reject(() -> GarmentPalette.referencePair(all, 1024, 768, 0));
        PreviewImages.gif(frames, out.resolve("approved-original-motion.gif"));
        var gifReader = ImageIO.getImageReadersByFormatName("gif").next();
        try (var input = ImageIO.createImageInputStream(out.resolve("approved-original-motion.gif").toFile())) {
            gifReader.setInput(input);
            check(gifReader.getNumImages(true) == 8, "GIF frame count");
            for (int frame = 0; frame < 8; frame++) {
                check(gifReader.getWidth(frame) == 512 && gifReader.getHeight(frame) == 384, "GIF cropped the pair");
                BufferedImage decoded = gifReader.read(frame);
                int foregroundRight = 0;
                for (int y = 0; y < 384; y++) for (int x = 256; x < 512; x++) {
                    int p = decoded.getRGB(x,y);
                    if (Math.max(Math.abs(((p >>> 16) & 255) - 243),
                        Math.max(Math.abs(((p >>> 8) & 255) - 238), Math.abs((p & 255) - 229))) > 30) foregroundRight++;
                }
                check(foregroundRight > 10000, "Female missing from GIF");
            }
        } finally { gifReader.dispose(); }
        var single = new android.graphics.Bitmap(frames.get(0).getImage().getSubimage(0,0,256,384));
        PreviewImages.gif(java.util.List.of(single), out.resolve("single-helper-regression.gif"));
        BufferedImage singleDecoded = ImageIO.read(out.resolve("single-helper-regression.gif").toFile());
        check(singleDecoded.getWidth() == 256 && singleDecoded.getHeight() == 384, "Existing single GIF dimensions changed");
        try {
            PreviewImages.gif(java.util.List.of(frames.get(0),single), out.resolve("invalid.gif"));
            throw new AssertionError("Unequal GIF frame sizes accepted");
        } catch (IllegalArgumentException expected) { }

        int[][] choices = {{0,0,0},{3,4,4},{1,2,4},{4,1,2},{5,3,1},{2,5,3}};
        BufferedImage grid = new BufferedImage(1536, 860, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = grid.createGraphics(); g.setColor(new Color(0xe6f0e8)); g.fillRect(0,0,1536,860);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        for (int n = 0; n < choices.length; n++) {
            int[] c = choices[n]; int x = (n % 3) * 512, y = (n / 3) * 430;
            BufferedImage im = image(GarmentPalette.recolor(source, mask, c[0], c[1], c[2]), 1448,1086);
            ImageIO.write(im,"png",out.resolve("colorway-" + n + ".png").toFile());
            g.setColor(new Color(0x182a23)); g.drawString("HOST proposal / colors " + Arrays.toString(c), x+12, y+26);
            g.drawImage(im,x,y+38,512,384,null);
        }
        g.dispose(); ImageIO.write(grid,"png",out.resolve("color-combinations.png").toFile());
        BufferedImage compare = new BufferedImage(1024,430,BufferedImage.TYPE_INT_RGB);
        g = compare.createGraphics(); g.setColor(new Color(0xe6f0e8)); g.fillRect(0,0,1024,430);
        g.setFont(new Font("SansSerif",Font.BOLD,18)); g.setColor(new Color(0x182a23));
        g.drawString("APPROVED reference / original frame 0",12,28);
        g.drawString("UNAPPROVED swimsuit / static proposal",524,28);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frames.get(0).getImage(),0,40,512,384,null); g.drawImage(sourceImage,512,40,512,384,null);
        g.dispose(); ImageIO.write(compare,"png",out.resolve("reference-comparison.png").toFile());
        String report = "{\n  \"surface\":\"HOST Java pixel engine; NOT Android capture\",\n  \"paletteCombinationsPassed\":" + combinations +
            ",\n  \"maskedPixels\":["+counts[1]+","+counts[2]+","+counts[3]+"],\n  \"outsideMaskChanges\":0,\n  \"referenceFramesComparedExactly\":8,\n  \"gifSize\":[512,384],\n  \"gifFrames\":8,\n  \"humanApproved\":false,\n  \"androidValidated\":false\n}\n";
        Files.writeString(out.resolve("verification.json"),report); System.out.println(report);
    }
}
