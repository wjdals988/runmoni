package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.AvatarCatalogue
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Source-pixel checks on the real immutable PNG/masks, without Android or a new art generator. */
class AvatarCostumeLayersTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/src/main/assets/avatars/v0.3.0/running-motion.png").isFile }
    private val assets = File(root, "app/src/main/assets/avatars")
    // The Android compilation API excludes java.desktop. This adapter calls the
    // standard JDK ImageIO decoder at host JUnit runtime, without adding a product
    // dependency or replacing the actual immutable PNGs with synthetic pixels.
    private val imageRead = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java)
    private val getRgb = Class.forName("java.awt.image.BufferedImage").getMethod("getRGB",
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val sheets = mutableMapOf<String, Any>()
    private fun cell(file: String, row: Int, frame: Int): IntArray {
        val image = sheets.getOrPut(file) { requireNotNull(imageRead.invoke(null, File(assets, file))) }
        return getRgb.invoke(image, frame * 256, row * 384, 256, 384, null, 0, 256) as IntArray
    }

    @Test fun allCostumePosesKeepOriginalHeadHandsBodyBoundsAndImmutableInputs() {
        val sourceFile = File(assets, "v0.3.0/running-motion.png")
        assertEquals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65",
            MessageDigest.getInstance("SHA-256").digest(sourceFile.readBytes()).joinToString("") { "%02x".format(it) })
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val source = cell("v0.3.0/running-motion.png", gender.spriteRow, frame)
            val fabric = cell("running-kit-v1/fabric-mask.png", gender.spriteRow, frame)
            val coverage = cell("wardrobe-v1/coverage.png", gender.spriteRow, frame)
            val hands = cell("wardrobe-v1/hand-protection.png", gender.spriteRow, frame)
            val identity = cell("running-kit-v1/protected-mask.png", gender.spriteRow, frame)
            val unchanged = arrayOf(source.copyOf(), fabric.copyOf(), coverage.copyOf(), hands.copyOf())
            for (costume in listOf(1, 4, 5)) {
                val result = AvatarCostumeLayers.render(source, fabric, coverage, hands, frame, gender, costume)
                val extensions = AvatarCostumeLayers.clothingExtensionMask(source, frame, gender, costume)
                var changedBody = 0; var addedClothing = 0
                val headEnd = if (gender == AvatarGender.MALE) 182 else 176
                for (i in source.indices) {
                    val x = i % 256; val y = i / 256; val alpha = source[i] ushr 24
                    val region = (fabric[i] ushr 16) and 255
                    val bodyGarment = region in 1..3 || (((coverage[i] ushr 16) and 6) != 0)
                    if (extensions[i] != 0) {
                        assertEquals(-1, extensions[i])
                        assertTrue("Clothing exception expanded into body/frame margin", x in 8..247 && y in 14 until headEnd)
                        assertTrue("Hat/hood exception replaced original hair or skin", alpha <= 16)
                        assertEquals(253, result[i] ushr 24)
                        addedClothing++
                    } else {
                        assertEquals("Costume changed original body alpha $gender/$frame/$costume/$i", alpha, result[i] ushr 24)
                    }
                    val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
                    val headOrNeck = y < headEnd || x in eye.x - 14..eye.x + 10 && y in eye.y + 20..eye.y + 45
                    if (headOrNeck && identity[i] == -1 && alpha > 16 || hands[i] == -1)
                        assertEquals("Original face/hair/hand changed $gender/$frame/$costume/$i", source[i], result[i])
                    if (!bodyGarment && extensions[i] == 0)
                        assertEquals("Costume invented clothing or skin outside its fixed labels", source[i], result[i])
                    if (bodyGarment && source[i] != result[i]) changedBody++
                }
                assertTrue("Missing sewn costume body $gender/$frame/$costume", changedBody > 300)
                assertTrue("Missing distinct hat/hood silhouette $gender/$frame/$costume", addedClothing > 20)
                assertArrayEquals(unchanged[0], source)
                assertArrayEquals(unchanged[1], fabric)
                assertArrayEquals(unchanged[2], coverage)
                assertArrayEquals(unchanged[3], hands)
            }
        }
    }

    @Test fun costumesHaveDistinctAuthoredShapesAndKeepAllEightOriginalPoses() {
        assertFalse(AvatarCostumeLayers.supports(2))
        assertFalse(AvatarCostumeLayers.supports(3))
        assertEquals(58, AvatarCatalogue.slots.sumOf { it.variants.size })
        assertEquals(13, AvatarCatalogue.slots.size)
        assertEquals(9, AvatarCatalogue.tabs.size)
        for (gender in AvatarGender.entries) {
            assertEquals(if (gender == AvatarGender.MALE) 43 else 41,
                AvatarCatalogue.slots.sumOf { slot -> slot.variants.indices.count { AvatarArtwork.itemReady(slot.key, it, gender) } })
            for (costume in listOf(1, 4, 5)) assertTrue(AvatarArtwork.itemReady("costume", costume, gender))
            for (pending in listOf(2, 3)) assertFalse(AvatarArtwork.itemReady("costume", pending, gender))
            val clips = listOf(1, 4, 5).associateWith { mutableSetOf<Int>() }
            for (frame in 0..7) {
                val source = cell("v0.3.0/running-motion.png", gender.spriteRow, frame)
                val fabric = cell("running-kit-v1/fabric-mask.png", gender.spriteRow, frame)
                val coverage = cell("wardrobe-v1/coverage.png", gender.spriteRow, frame)
                val hands = cell("wardrobe-v1/hand-protection.png", gender.spriteRow, frame)
                val bearShape = AvatarCostumeLayers.clothingExtensionMask(source, frame, gender, 4)
                val goldenShape = AvatarCostumeLayers.clothingExtensionMask(source, frame, gender, 5)
                assertFalse("Golden suit is only a recolored bear silhouette", bearShape.contentEquals(goldenShape))
                for ((costume, hashes) in clips) {
                    val result = AvatarCostumeLayers.render(source, fabric, coverage, hands, frame, gender, costume)
                    hashes += result.contentHashCode()
                    assertArrayEquals("Wrapped frame indexing changed the garment",
                        result, AvatarCostumeLayers.render(source, fabric, coverage, hands, frame + 8, gender, costume))
                }
                for (pending in listOf(0, 2, 3)) assertArrayEquals("Unimplemented swimwear changed the source",
                    source, AvatarCostumeLayers.render(source, fabric, coverage, hands, frame, gender, pending))
            }
            for ((costume, hashes) in clips) assertEquals("A costume discarded original poses $gender/$costume", 8, hashes.size)
        }
    }

    @Test fun gearDoesNotPunchHolesIntoNewHoodsOrAttachToSourceBackgroundFringe() {
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val source = cell("v0.3.0/running-motion.png", gender.spriteRow, frame)
            val fabric = cell("running-kit-v1/fabric-mask.png", gender.spriteRow, frame)
            val coverage = cell("wardrobe-v1/coverage.png", gender.spriteRow, frame)
            val hands = cell("wardrobe-v1/hand-protection.png", gender.spriteRow, frame)
            for (costume in listOf(1, 4, 5)) {
                val clothed = AvatarCostumeLayers.render(source, fabric, coverage, hands, frame, gender, costume)
                val equipped = AvatarEquipmentLayers.render(clothed, source, fabric, frame, gender,
                    AvatarStyle(gender = gender, costume = costume, ear = 1, eyewear = 1, wrist = 1, waist = 1, hydration = 1))
                for (i in source.indices) {
                    assertEquals("Equipment punched a hole in a clothing layer", clothed[i] ushr 24, equipped[i] ushr 24)
                    if ((source[i] ushr 24) <= 16)
                        assertEquals("Equipment floated on the source background", clothed[i], equipped[i])
                }
            }
        }
    }
}
