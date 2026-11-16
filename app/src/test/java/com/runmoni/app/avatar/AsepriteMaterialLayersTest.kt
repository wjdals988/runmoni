package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Actual authored cels and source pixels; numerical safety is not visual approval. */
class AsepriteMaterialLayersTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/src/main/assets/avatars/v0.3.0/running-motion.png").isFile }
    private val assets = File(root, "app/src/main/assets/avatars")
    private val read = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java)
    private val rgb = Class.forName("java.awt.image.BufferedImage").getMethod("getRGB",
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private fun image(path: String) = requireNotNull(read.invoke(null, File(assets, path)))
    private fun cell(image: Any, row: Int, frame: Int) =
        rgb.invoke(image, frame * 256, row * 384, 256, 384, null, 0, 256) as IntArray
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    @Test fun authoredCelsPreserveFaceHandsAlphaAndEveryPixelOutsideClothesInAllPoses() {
        val sourceFile = File(assets, "v0.3.0/running-motion.png")
        val before = digest(sourceFile)
        assertEquals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65", before)
        val source = image("v0.3.0/running-motion.png")
        val fabric = image("running-kit-v1/fabric-mask.png")
        val coverage = image("wardrobe-v1/coverage.png")
        val hands = image("wardrobe-v1/hand-protection.png")
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val original = cell(source, gender.spriteRow, frame)
            val originalCopy = original.clone()
            val fm = cell(fabric, gender.spriteRow, frame)
            val cm = cell(coverage, gender.spriteRow, frame)
            val hm = cell(hands, gender.spriteRow, frame)
            val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
            for (style in listOf("headwear-1", "headwear-2", "costume-1", "costume-4", "costume-5")) {
                val variant = style.last().digitToInt()
                val costume = style.startsWith("costume")
                val base = if (costume) AvatarCostumeLayers.render(original, fm, cm, hm, frame, gender, variant)
                    else AvatarHeadwearLayers.render(original, original, frame, gender,
                        if (variant == 1) AvatarHeadwearLayers.Kind.CAP else AvatarHeadwearLayers.Kind.VISOR)
                val allowance = if (costume) AsepriteMaterialLayers.costumeMask(base, original, fm, cm, hm, frame, gender, variant)
                    else AsepriteMaterialLayers.headwearMask(base, original, frame, gender, variant)
                val detail = cell(image("atelier-v1/$style/${gender.wireValue}-$frame.png"), 0, 0)
                val baseCopy = base.clone(); val detailCopy = detail.clone()
                val result = AsepriteMaterialLayers.apply(base, detail, allowance)
                var changed = 0
                for (i in result.indices) {
                    assertEquals("Material expanded alpha: $style/$gender/$frame/$i", base[i] ushr 24, result[i] ushr 24)
                    if (allowance[i] == 0) assertEquals(base[i], result[i])
                    if (hm[i] != 0) assertEquals("Material painted a hand", original[i], result[i])
                    if (result[i] != base[i]) changed++
                }
                for (y in eye.y - 12..eye.y + 30) for (x in eye.x - 3..eye.x + 35)
                    assertEquals("Material replaced the original face", original[y * 256 + x], result[y * 256 + x])
                assertTrue("An authored cel must visibly change its garment: $style/$gender/$frame", changed > 0)
                assertArrayEquals(originalCopy, original); assertArrayEquals(baseCopy, base); assertArrayEquals(detailCopy, detail)
            }
        }
        assertEquals(before, digest(sourceFile))
    }

    @Test fun allEightyExportedCelsMatchTheRuntimeIntegrityManifest() {
        val manifest = File(assets, "atelier-v1/manifest.json").readText()
        val rows = Regex("\"([^\"]+\\.png)\"\\s*:\\s*\"([0-9a-f]{64})\"").findAll(manifest).toList()
        assertEquals(80, rows.size)
        assertEquals(80, rows.map { it.groupValues[1] }.toSet().size)
        rows.forEach { assertEquals(it.groupValues[2], digest(File(assets, "atelier-v1/${it.groupValues[1]}"))) }
        assertTrue(manifest.contains("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"))
    }

    @Test fun invalidInkCannotExpandTheBodyOrBypassTheMask() {
        val base = IntArray(256 * 384)
        val detail = IntArray(base.size).also { it[100] = 0xffffffff.toInt() }
        val mask = IntArray(base.size)
        assertThrows(IllegalArgumentException::class.java) { AsepriteMaterialLayers.apply(base, detail, mask) }
        base[100] = 0xfd123456.toInt(); mask[100] = -1
        assertEquals(0xfdffffff.toInt(), AsepriteMaterialLayers.apply(base, detail, mask)[100])
        detail[100] = 0x80123456.toInt()
        assertThrows(IllegalArgumentException::class.java) { AsepriteMaterialLayers.apply(base, detail, mask) }
    }
}
