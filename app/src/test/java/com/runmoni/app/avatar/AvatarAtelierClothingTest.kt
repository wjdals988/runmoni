package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.withSelectedItem
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Real original/independent labels, rather than expected pixels drawn by the renderer under test. */
class AvatarAtelierClothingTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/src/main/assets/avatars/v0.3.0/running-motion.png").isFile }
    private val assets = File(root, "app/src/main/assets/avatars")
    private val read = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java)
    private val rgb = Class.forName("java.awt.image.BufferedImage").getMethod("getRGB",
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val sheets = mutableMapOf<String, Any>()
    private fun cell(path: String, gender: AvatarGender, frame: Int): IntArray = rgb.invoke(
        sheets.getOrPut(path) { requireNotNull(read.invoke(null, File(assets, path))) },
        frame * 256, gender.spriteRow * 384, 256, 384, null, 0, 256) as IntArray

    private data class Inputs(val source: IntArray, val fabric: IntArray, val coverage: IntArray,
        val socks: IntArray, val hands: IntArray, val shoeLock: IntArray)
    private fun inputs(gender: AvatarGender, frame: Int) = Inputs(
        cell("v0.3.0/running-motion.png", gender, frame),
        cell("running-kit-v1/fabric-mask.png", gender, frame),
        cell("wardrobe-v1/coverage.png", gender, frame),
        cell("socks-v1/sock-mask.png", gender, frame),
        cell("wardrobe-v1/hand-protection.png", gender, frame),
        cell("socks-v1/shoe-protection.png", gender, frame))

    private fun render(input: Inputs, frame: Int, style: AvatarStyle): IntArray {
        val colors = style.runningKitColors.normalized()
        val clothes = WardrobeClothing.render(input.source, input.fabric, input.coverage,
            AvatarAtelierClothing.baseTop(style.top), style.bottom, AvatarAtelierClothing.baseShoes(style.shoes),
            AvatarAtelierClothing.topColor(style.top, colors.top), colors.bottom, colors.shoes,
            style.gender.spriteRow, frame)
        val socks = SockClothing.render(clothes, input.socks, AvatarAtelierClothing.baseSocks(style.socks))
        return AvatarAtelierClothing.render(socks, input.source, input.fabric, input.coverage,
            input.socks, input.hands, frame, style)
    }

    @Test fun allThreeConstructionsKeepRealIdentityAlphaSolesForegroundHandsAndEveryInput() {
        assertEquals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65",
            MessageDigest.getInstance("SHA-256").digest(File(assets, "v0.3.0/running-motion.png").readBytes())
                .joinToString("") { "%02x".format(it) })
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val input = inputs(gender, frame)
            val arrays = listOf(input.source, input.fabric, input.coverage, input.socks, input.hands, input.shoeLock)
            val before = arrays.map { it.copyOf() }
            for (style in listOf(AvatarStyle(gender = gender, top = 6), AvatarStyle(gender = gender, socks = 4),
                AvatarStyle(gender = gender, shoes = 4), AvatarStyle(gender = gender, top = 6, socks = 4, shoes = 4))) {
                val actual = render(input, frame, style)
                var changed = 0
                for (i in actual.indices) {
                    val region = (input.fabric[i] ushr 16) and 255
                    val topAllowed = style.top == 6 && input.hands[i] == 0 &&
                        (region == 1 || ((input.coverage[i] ushr 16) and 1) != 0)
                    val sockAllowed = style.socks == 4 && ((input.socks[i] ushr 16) and 1) != 0
                    val shoeAllowed = style.shoes == 4 && region == 3
                    // This plate protects all non-sock ink, including the source
                    // shirt. A shirt's own explicit mask may recolor that cloth;
                    // the rib-sock-only case still checks the entire plate.
                    assertEquals("Material invented body/background alpha $gender/$frame/$i", input.source[i] ushr 24, actual[i] ushr 24)
                    if (!topAllowed && !sockAllowed && !shoeAllowed)
                        assertEquals("Material escaped the original garment labels $gender/$frame/$i", input.source[i], actual[i])
                    if (i / 256 < 174 && !topAllowed || input.hands[i] != 0)
                        assertEquals("A garment covered the original head/foreground hand $gender/$frame/$i", input.source[i], actual[i])
                    if (!topAllowed && style.shoes == 0 && input.shoeLock[i] != 0)
                        assertEquals("Rib socks recolored a pinned shoe/sole pixel $gender/$frame/$i", input.source[i], actual[i])
                    if (!topAllowed && region != 3 && input.shoeLock[i] != 0)
                        assertEquals("Panel shoes escaped the upper and recolored their sole $gender/$frame/$i", input.source[i], actual[i])
                    if (input.source[i] != actual[i]) changed++
                }
                val eye = AvatarEquipmentLayers.anchors(frame, gender).eye
                for (y in eye.y - 12..eye.y + 30) for (x in eye.x - 3..eye.x + 35)
                    assertEquals("New clothes changed original face pixels", input.source[y * 256 + x], actual[y * 256 + x])
                assertTrue("New construction has no visible material $gender/$frame/$style", changed > 100)
                assertArrayEquals("Frame wrap discarded authored attachment positions", actual, render(input, frame + 8, style))
            }
            arrays.forEachIndexed { index, values -> assertArrayEquals("Renderer wrote an original/mask input", before[index], values) }
        }
    }

    @Test fun constructionsAreMoreThanTheirBasePaletteAndExplicitTintsKeepTheirPanels() {
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val input = inputs(gender, frame)
            val mesh = render(input, frame, AvatarStyle(gender = gender, top = 6))
            val blueTee = WardrobeClothing.render(input.source, input.fabric, input.coverage, 1, 0, 0,
                3, 0, 0, gender.spriteRow, frame)
            val rib = render(input, frame, AvatarStyle(gender = gender, socks = 4))
            val creamCrew = SockClothing.render(input.source, input.socks, 1)
            val panels = render(input, frame, AvatarStyle(gender = gender, shoes = 4))
            val blueShoe = WardrobeClothing.render(input.source, input.fabric, input.coverage, 0, 0, 2,
                0, 0, 0, gender.spriteRow, frame)
            for ((new, base) in listOf(mesh to blueTee, rib to creamCrew, panels to blueShoe))
                assertTrue("New garment is just its old base palette $gender/$frame", new.indices.count { new[it] != base[it] } > 40)
            val style = AvatarStyle(gender = gender, top = 6, socks = 4, shoes = 4)
            val original = render(input, frame, style)
            val tinted = style.copy(runningKitColors = RunningKitColors(4, 0, 2))
            assertFalse(original.contentEquals(render(input, frame, tinted)))
            assertArrayEquals(original, render(input, frame, tinted.copy(runningKitColors = RunningKitColors())))
            val oldTeeTinted = WardrobeClothing.render(input.source, input.fabric, input.coverage, 1, 0, 0,
                4, 0, 0, gender.spriteRow, frame)
            val meshTinted = render(input, frame, AvatarStyle(gender = gender, top = 6, runningKitColors = RunningKitColors(top = 4)))
            assertTrue(meshTinted.indices.count { meshTinted[it] != oldTeeTinted[it] } > 40)
        }
    }

    @Test fun originalFiftyEightIdsStayAsPrefixesAndNewSelectionsKeepGenderEquipmentAndFreePolicy() {
        val old = listOf(
            "hair" to listOf("텍스처 쇼트", "가르마 쇼트", "포니테일", "단발"),
            "headwear" to listOf("모자 없음", "러닝 캡", "선바이저", "스웨트 헤드밴드"),
            "face" to listOf("편안한 표정", "활짝 웃음", "집중하는 표정", "윙크"),
            "top" to listOf("코랄 싱글렛", "민트 러닝 반팔", "블루 긴팔", "옐로 바람막이", "스포츠 브라탑", "크롭 기능성 티"),
            "bottom" to listOf("네이비 쇼츠", "러닝 타이츠", "그린 쇼츠", "코랄 쇼츠", "하이웨이스트 바이커쇼츠", "스플릿 레이싱 쇼츠"),
            "costume" to listOf("일반 러닝복", "산타 러너", "스포츠 원피스 수영복", "스포츠 비키니", "곰인형 코스튬", "골든 마스코트"),
            "socks" to listOf("발목 양말", "크루 양말", "니삭스", "양말 없음"),
            "shoes" to listOf("민트 러닝화", "코랄 러닝화", "블루 러닝화", "골드 러닝화"),
            "ear" to listOf("귀 장비 없음", "오픈형 이어폰", "스포츠 이어버드", "스포츠 헤드셋"),
            "eyewear" to listOf("선글라스 없음", "민트 스포츠 선글라스", "코랄 스포츠 선글라스", "블루 스포츠 선글라스"),
            "wrist" to listOf("워치 없음", "민트 스포츠 워치", "코랄 스포츠 워치", "블루 스포츠 워치"),
            "waist" to listOf("벨트 없음", "민트 러닝 벨트", "코랄 러닝 벨트", "블루 러닝 벨트"),
            "hydration" to listOf("조끼 없음", "민트 수분 보급 조끼", "코랄 수분 보급 조끼", "블루 수분 보급 조끼"))
        assertEquals(58, old.sumOf { it.second.size })
        assertEquals(old.map { it.first }, AvatarCatalogue.slots.map { it.key })
        old.zip(AvatarCatalogue.slots).forEach { (original, current) ->
            assertEquals("Saved ${current.key} IDs were renumbered", original.second, current.variants.take(original.second.size))
        }
        assertEquals(100, AvatarCatalogue.slots.sumOf { it.variants.size })
        assertEquals(13, AvatarCatalogue.slots.size)
        assertEquals(9, AvatarCatalogue.tabs.size)
        val owned = setOf("top:4", "socks:3", "accessory:2", "wrist:2")
        val migrated = AvatarCatalogue.migrateOwned(owned)
        assertTrue(migrated.containsAll(owned))
        assertFalse("New free items must not fabricate purchase ownership", migrated.contains("top:6"))
        for (gender in AvatarGender.entries) {
            assertEquals(if (gender == AvatarGender.MALE) 87 else 85, AvatarCatalogue.slots.sumOf { slot ->
                slot.variants.indices.count { AvatarArtwork.itemReady(slot.key, it, gender) } })
            for ((slot, id) in listOf("top" to 6, "socks" to 4, "shoes" to 4)) {
                assertTrue(AvatarCatalogue.valid(slot, id))
                assertTrue(AvatarArtwork.itemReady(slot, id, gender))
                assertEquals(0, AvatarCatalogue.price(id))
                val style = AvatarStyle(gender = gender, costume = 4, wrist = 2, runningKitColors = RunningKitColors(4, 2, 3))
                val selected = style.withSelectedItem(slot, id)
                assertEquals(gender, selected.gender)
                assertEquals(0, selected.costume)
                assertEquals(2, selected.wrist)
                assertEquals(2, selected.runningKitColors.bottom)
            }
            for ((slot, id) in listOf("top" to 4, "top" to 5, "socks" to 3, "costume" to 2, "costume" to 3))
                assertFalse("Unapproved skin/swimwear accidentally became selectable", AvatarArtwork.itemReady(slot, id, gender))
        }
        assertThrows(IllegalArgumentException::class.java) { AvatarCatalogue.price(-1) }
        assertThrows(IllegalArgumentException::class.java) { AvatarCatalogue.price(10) }
    }

    @Test fun missingIndependentLabelsAreRejectedWithoutWritingTheOriginal() {
        val source = IntArray(256 * 384)
        val mask = source.copyOf()
        assertThrows(IllegalArgumentException::class.java) {
            AvatarAtelierClothing.render(source, source, mask, null, null, null, 0, AvatarStyle(top = 6))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvatarAtelierClothing.render(source, source, mask, null, null, null, 0, AvatarStyle(socks = 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvatarAtelierClothing.render(IntArray(3), source, mask, null, null, null, 0, AvatarStyle(shoes = 4))
        }
        assertArrayEquals(IntArray(source.size), source)
    }
}
