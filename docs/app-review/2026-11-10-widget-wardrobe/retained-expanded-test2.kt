package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.data.LocalWallet
import com.runmoni.app.data.PilotClient
import com.runmoni.app.domain.*
import com.runmoni.app.ui.ClosetDialog
import com.runmoni.app.ui.RunmoniTheme
import com.runmoni.app.ui.RunmoniUiTest
import com.runmoni.app.ui.avatarBitmap
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native Android bitmap checks and an offline Compose fixture. Exported sheets
 * are renderer output, not MainActivity screenshots or user visual approval.
 * Persistence uses a dedicated SharedPreferences file, never the user's wallet.
 */
class ExpandedWardrobeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(image: Bitmap) = IntArray(image.width * image.height).also {
        image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
    }

    @Test fun bothSockLengthsChangeAllPosesWhileIdentityShoesAndOtherMaterialsStayIntact() {
        val renderer = ApprovedAvatarRenderer(context)
        val sheets = Array(2) { Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888) }
        try {
            for (gender in AvatarGender.entries) for (frame in 0..7) {
                val approved = pixels(renderer.render(frame, true, AvatarStyle(gender = gender)))
                val fabric = RunningKitArtwork.frameMask(context, gender.spriteRow, frame)
                val legacyCoverage = WardrobeCoverage.frame(context, gender.spriteRow, frame)
                val sockMask = SockArtwork.frameMask(context, gender.spriteRow, frame)
                val visibleAnkles = SockArtwork.visibleAnkleMask(context, gender.spriteRow, frame)
                val protectedShoes = SockArtwork.shoeProtection(context, gender.spriteRow, frame)
                val untouchedMask = sockMask.copyOf()
                val untouchedAnkles = visibleAnkles.copyOf()
                val untouchedShoes = protectedShoes.copyOf()
                // Fixed regression seeds on this approved source image: host
                // review found sock patches on these visible thigh pixels.
                // These are neither inferred anatomy nor a complete leg mask.
                val thighLocks = if (gender == AvatarGender.FEMALE) when (frame) {
                    1, 5 -> listOf(150 to 265)
                    2 -> listOf(145 to 260, 150 to 260)
                    6 -> listOf(145 to 265)
                    else -> emptyList()
                } else emptyList()
                for ((x, y) in thighLocks) assertEquals("Sock label reached source-fixed thigh $frame/$x/$y",
                    0, sockMask[y * 256 + x])
                assertTrue("Missing independent shoe/sole protection", protectedShoes.any { it == -1 })
                for (i in sockMask.indices) {
                    assertTrue("Invalid visible ankle label", visibleAnkles[i] == 0 || visibleAnkles[i] == -1)
                    assertTrue("Invalid shoe protection label", protectedShoes[i] == 0 || protectedShoes[i] == -1)
                    if (gender == AvatarGender.MALE) assertEquals("Men's mask invented an ankle underlay", 0, visibleAnkles[i])
                    if (protectedShoes[i] != 0) assertEquals("Sock mask enters a shoe or sole", 0, sockMask[i])
                    if (visibleAnkles[i] != 0) {
                        assertEquals(AvatarGender.FEMALE, gender)
                        assertEquals("Pinned ankle overlaps a shoe or sole", 0, protectedShoes[i])
                        assertTrue("Pinned ankle enters the upper body", i / 256 >= 250)
                        assertTrue("Pinned ankle is not visible original artwork", (approved[i] ushr 24) >= 200)
                        assertEquals("Existing ankle remains uncovered by one sock length", 3, (sockMask[i] ushr 16) and 255)
                        assertEquals("Ankle has an unexpected cuff seam", 0, sockMask[i] and 255)
                    }
                    if (sockMask[i] == 0) continue
                    // Existing leg/fabric data plus the source-pinned visible
                    // ankle label reject hidden legs, shoes and upper-body skin.
                    val material = (fabric[i] ushr 16) and 255
                    assertTrue("Sock identity bound ${gender.name}/$frame/$i", i / 256 >= 250)
                    assertNotEquals("Shoe fabric in sock mask", 3, material)
                    assertTrue("Sock mask must stay on visible approved leg material",
                        if (gender == AvatarGender.MALE) ((legacyCoverage[i] ushr 16) and 4) != 0
                        else material == 2 || visibleAnkles[i] == -1)
                }
                val outfits = listOf(
                    AvatarStyle(gender = gender),
                    AvatarStyle(gender = gender, top = 3, bottom = 1, shoes = 2,
                        headwear = 3, ear = 2, eyewear = 1, wrist = 2, waist = 1, hydration = 3,
                        runningKitColors = RunningKitColors(3, 4, 5)),
                )
                for ((outfitIndex, outfit) in outfits.withIndex()) {
                    val originalBitmap = renderer.render(frame, true, outfit)
                    val before = pixels(originalBitmap)
                    val results = (1..2).map { variant ->
                        val style = outfit.copy(socks = variant)
                        val after = pixels(renderer.render(frame, true, style))
                        assertTrue("Invisible sock ${gender.name}/$frame/$variant/$outfitIndex",
                            before.indices.count { before[it] != after[it] } > 20)
                        for ((x, y) in thighLocks) assertEquals("Sock changed source-fixed thigh $frame/$variant/$x/$y",
                            before[y * 256 + x], after[y * 256 + x])
                        for (i in before.indices) {
                            if ((before[i] ushr 24) != (after[i] ushr 24))
                                fail("Sock altered alpha ${gender.name}/$frame/$variant/$i")
                            val selected = ((sockMask[i] ushr 16) and variant) != 0
                            if (!selected || i / 256 < 250 || protectedShoes[i] != 0 ||
                                ((fabric[i] ushr 16) and 255) == 3) {
                                if (before[i] != after[i]) fail("Other material changed ${gender.name}/$frame/$variant/$i")
                            }
                        }
                        assertArrayEquals("App/map entry point dropped socks",
                            pixels(renderer.renderForDisplay(frame, true, style)),
                            pixels(avatarBitmap(context, style, frame, true)))
                        assertArrayEquals("Input bitmap mutated", before, pixels(originalBitmap))
                        if (outfitIndex == 0) sheets[variant - 1].setPixels(after, 0, 256,
                            frame * 256, gender.spriteRow * 384, 256, 384)
                        after
                    }
                    assertFalse("Crew and knee socks are indistinguishable", results[0].contentEquals(results[1]))
                    // Revisit both variants after interleaving with the base.
                    assertArrayEquals(before, pixels(renderer.render(frame, true, outfit)))
                    for (variant in 1..2) assertArrayEquals("Sock cache selection leaked",
                        results[variant - 1], pixels(renderer.render(frame, true, outfit.copy(socks = variant))))
                }
                assertArrayEquals("Authored sock mask mutated", untouchedMask, sockMask)
                assertArrayEquals("Pinned visible ankle mask mutated", untouchedAnkles, visibleAnkles)
                assertArrayEquals("Independent shoe protection mutated", untouchedShoes, protectedShoes)
            }
            assertTrue(renderer.cachedColorBytes() <= ApprovedAvatarRenderer.COLOR_CACHE_BYTES)
            val directory = context.getExternalFilesDir("expanded-wardrobe-renderer")!!
            check(directory.isDirectory || directory.mkdirs())
            sheets.forEachIndexed { index, bitmap ->
                File(directory, "socks-${index + 1}-android-renderer.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        } finally { sheets.forEach(Bitmap::recycle) }
    }

    @Test fun newMensShortsUseDistinctDefaultsAndExplicitColorWithoutInventingWomensLegs() {
        val renderer = ApprovedAvatarRenderer(context)
        for (frame in 0..7) {
            val male = AvatarStyle()
            val before = pixels(renderer.render(frame, true, male))
            val fabric = RunningKitArtwork.frameMask(context, AvatarGender.MALE.spriteRow, frame)
            val outputs = (2..3).map { variant ->
                assertTrue(AvatarArtwork.itemReady("bottom", variant, AvatarGender.MALE))
                assertFalse(AvatarArtwork.itemReady("bottom", variant, AvatarGender.FEMALE))
                val selected = male.copy(bottom = variant)
                val output = pixels(renderer.render(frame, true, selected))
                assertTrue(before.indices.count { before[it] != output[it] } > 20)
                for (i in before.indices) {
                    if ((before[i] ushr 24) != (output[i] ushr 24)) fail("Shorts altered alpha $frame/$variant/$i")
                    if (((fabric[i] ushr 16) and 255) != 2 && before[i] != output[i])
                        fail("Shorts recolored a leg/other material at $frame/$variant/$i")
                }
                val override = RunningKitColors(bottom = 4)
                assertArrayEquals("Explicit bottom color lost to an item default",
                    pixels(renderer.render(frame, true, male.copy(runningKitColors = override))),
                    pixels(renderer.render(frame, true, selected.copy(runningKitColors = override))))
                assertArrayEquals("Reset to zero did not restore item palette", output,
                    pixels(renderer.render(frame, true, selected.copy(runningKitColors = RunningKitColors()))))
                val femaleSaved = selected.copy(gender = AvatarGender.FEMALE,
                    socks = 2, shoes = 3, runningKitColors = RunningKitColors(3, 4, 5))
                assertArrayEquals("Pending women's shorts invented a shorter body",
                    pixels(renderer.render(frame, true, femaleSaved.copy(bottom = 0))),
                    pixels(renderer.render(frame, true, femaleSaved)))
                assertEquals("Rendering migrated the saved ID", variant, femaleSaved.bottom)
                output
            }
            assertFalse("Two named shorts share the same default palette", outputs[0].contentEquals(outputs[1]))
            assertArrayEquals("Default source polluted after shorts cache", before, pixels(renderer.render(frame, true, male)))
        }
    }

    @Test fun newIdsAndPendingGenderChoiceRoundTripInARealIsolatedWalletWithoutSpending() {
        val name = "expanded_wardrobe_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            assertTrue(preferences.edit().putString("localProfile",
                """{"name":"기존 러너","mileage":777,"owned":["bottom:2","bottom:3","socks:1","socks:2","wrist:2"],"avatar":{"top":3,"wrist":2,"headwear":3,"shoes":2}}""").commit())
            val wallet = LocalWallet(preferences)
            val old = wallet.read()
            for ((gender, bottom, socks) in listOf(
                Triple(AvatarGender.MALE, 2, 1), Triple(AvatarGender.MALE, 3, 2),
                Triple(AvatarGender.FEMALE, 2, 1), Triple(AvatarGender.FEMALE, 3, 2),
            )) {
                val saved = old.avatar.copy(gender = gender, bottom = bottom, socks = socks,
                    runningKitColors = RunningKitColors(3, 4, 5))
                wallet.update { it.copy(avatar = saved) }
                val restored = LocalWallet(preferences).read()
                assertEquals(saved, restored.avatar)
                assertEquals(777, restored.mileage)
                assertEquals(old.name, restored.name)
                assertEquals(old.ownedItems, restored.ownedItems)
                val wire = JSONObject(PilotClient.encodeAvatar(restored.avatar).toString())
                assertEquals(saved, with(PilotClient) { wire.toAvatar() })
                assertEquals(bottom, restored.avatar.bottom)
                assertEquals(socks, restored.avatar.socks)
            }
        } finally { assertTrue(context.deleteSharedPreferences(name)) }
    }

    @Test fun closetSelectsNewFreeItemsAndGenderSwitchPreservesTheHiddenSavedShortsId() {
        val original = Profile(name = "검토 러너", mileage = 4242,
            ownedItems = AvatarCatalogue.defaultOwned + setOf("top:3", "shoes:2", "wrist:2", "headwear:3"),
            avatar = AvatarStyle(top = 3, bottom = 1, shoes = 2, wrist = 2, headwear = 3,
                runningKitColors = RunningKitColors(3, 0, 5)))
        val state = mutableStateOf(AppState(profile = original))
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, RunmoniUiTest.FakeActions(state)) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        fun preview(): IntArray {
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasContentDescription("나의 러닝 미니미") and
                    hasAnyAncestor(hasTestTag("closet_avatar_preview")), useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }
            return pixels(compose.onNodeWithTag("closet_avatar_preview").captureToImage().asAndroidBitmap())
        }
        fun pick(slot: String, variant: Int) {
            compose.onNodeWithTag("closet_slot_$slot").performScrollTo().performClick()
            compose.onNodeWithTag("closet_${slot}_$variant").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag("closet_${slot}_$variant").assertIsSelected()
        }
        var last = preview()
        for ((slot, variant) in listOf("socks" to 1, "socks" to 2, "bottom" to 2, "bottom" to 3)) {
            pick(slot, variant)
            val image = preview()
            assertFalse("Visible closet preview ignored $slot:$variant", last.contentEquals(image))
            last = image
            compose.runOnIdle {
                assertEquals(variant, state.value.profile.avatar.variantFor(slot))
                assertEquals(4242, state.value.profile.mileage)
                assertEquals(0, AvatarCatalogue.price(variant))
                assertTrue("$slot:$variant" in state.value.profile.ownedItems)
                assertEquals(original.avatar.top, state.value.profile.avatar.top)
                assertEquals(original.avatar.shoes, state.value.profile.avatar.shoes)
                assertEquals(original.avatar.wrist, state.value.profile.avatar.wrist)
                assertEquals(original.avatar.headwear, state.value.profile.avatar.headwear)
                assertEquals(original.avatar.runningKitColors.top, state.value.profile.avatar.runningKitColors.top)
                assertEquals(original.avatar.runningKitColors.shoes, state.value.profile.avatar.runningKitColors.shoes)
            }
        }
        compose.onNodeWithTag("closet_gender_female").performClick()
        compose.onNodeWithTag("closet_bottom_2").assertDoesNotExist()
        compose.onNodeWithTag("closet_bottom_3").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(AvatarGender.FEMALE, state.value.profile.avatar.gender)
            assertEquals(3, state.value.profile.avatar.bottom)
            assertEquals(2, state.value.profile.avatar.socks)
            assertTrue(state.value.profile.ownedItems.containsAll(setOf("bottom:2", "bottom:3", "socks:1", "socks:2")))
        }
        compose.onNodeWithTag("closet_slot_socks").performScrollTo().performClick()
        compose.onNodeWithTag("closet_socks_1").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("closet_socks_2").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("closet_socks_3").assertDoesNotExist()
        compose.onNodeWithTag("closet_gender_male").performClick()
        compose.onNodeWithTag("closet_slot_bottom").performScrollTo().performClick()
        compose.onNodeWithTag("closet_bottom_3").performScrollTo().assertIsSelected()
    }
}
