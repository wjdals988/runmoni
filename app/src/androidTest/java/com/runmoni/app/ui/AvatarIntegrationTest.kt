package com.runmoni.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.data.LocalWallet
import com.runmoni.app.data.PilotClient
import com.runmoni.app.domain.*
import com.runmoni.app.avatar.AvatarArtwork
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline fixtures on the dedicated AVD, not proof of remote server/device GPS. */
class AvatarIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private fun save(image: Bitmap, name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("avatar-verification")!!
        dir.mkdirs(); File(dir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun readyOutfitAndAvailableGearProduceEightDistinctRenderedCaptures() {
        val frame = mutableIntStateOf(0)
        val rendered = AtomicInteger(-1)
        var avatar = AvatarStyle(top = 1, bottom = 1, shoes = 1)
        for (slot in AvatarCatalogue.slots.filter { it.key in AvatarCatalogue.accessoryKeys || it.key == "headwear" }) {
            val variant = slot.variants.indices.firstOrNull { it > 0 && AvatarArtwork.itemReady(slot.key, it, avatar.gender) }
            if (variant != null) avatar = avatar.withItem(slot.key, variant)
        }
        for (slot in listOf("top", "bottom", "shoes")) assertTrue(AvatarArtwork.itemReady(slot, avatar.variantFor(slot), avatar.gender))
        compose.setContent { RunmoniTheme { MiniMi(avatar, Modifier.size(192.dp, 224.dp).testTag("avatar_fixture"),
            running = true, frameOverride = frame.intValue, onFrameRendered = { rendered.set(it) }) } }
        val hashes = mutableSetOf<Int>()
        for (f in 0..7) {
            if (f != 0) compose.runOnIdle { rendered.set(-1); frame.intValue = f }
            compose.waitUntil(30_000) { rendered.get() == f }
            val bitmap = compose.onNodeWithTag("avatar_fixture").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            hashes += pixels.contentHashCode(); save(bitmap, "native-run-$f.png")
        }
        assertEquals(8, hashes.size)
    }

    @Test fun nineTabsExposeReadyItemsAndPreserveUnreadyStoredChoicesAndInventory() {
        val owned = AvatarCatalogue.slots.flatMap { slot -> slot.variants.indices.map { "${slot.key}:$it" } }.toSet()
        val original = Profile(mileage = 4242, ownedItems = owned, avatar = AvatarStyle(hair = 2, face = 1, socks = 2, costume = 1))
        val state = mutableStateOf(AppState(profile = original))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, actions) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        fun pick(slot: String, variant: Int) {
            if (slot in AvatarCatalogue.accessoryKeys) compose.onNodeWithTag("closet_slot_accessories").performScrollTo().performClick()
            compose.onNodeWithTag("closet_slot_$slot").performScrollTo().performClick()
            compose.onNodeWithTag("closet_${slot}_$variant").performScrollTo().assertIsEnabled().performClick()
        }
        compose.onNodeWithTag("closet_slot_costume").performScrollTo().performClick()
        compose.onNodeWithTag("closet_costume_1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(original, state.value.profile) }
        pick("costume", 0)
        for ((slot, variant) in listOf("top" to 1, "bottom" to 1, "shoes" to 1)) pick(slot, variant)
        val availableGear = mutableMapOf<String, Int>()
        for (slot in AvatarCatalogue.slots.filter { it.key in AvatarCatalogue.accessoryKeys || it.key == "headwear" }) {
            val variant = slot.variants.indices.firstOrNull { it > 0 && AvatarArtwork.itemReady(slot.key, it, original.avatar.gender) }
            if (variant != null) { pick(slot.key, variant); availableGear[slot.key] = variant }
        }
        compose.runOnIdle {
            val current = state.value.profile
            assertEquals(0, current.avatar.costume)
            assertEquals(1, current.avatar.top); assertEquals(1, current.avatar.bottom); assertEquals(1, current.avatar.shoes)
            assertEquals(original.avatar.hair, current.avatar.hair)
            assertEquals(original.avatar.face, current.avatar.face)
            assertEquals(original.avatar.socks, current.avatar.socks)
            availableGear.forEach { (slot, variant) -> assertEquals(variant, current.avatar.variantFor(slot)) }
            assertEquals(original.mileage, current.mileage); assertEquals(original.ownedItems, current.ownedItems)
            assertEquals(9, AvatarCatalogue.tabs.size)
        }
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("나의 러닝 미니미").fetchSemanticsNodes().isNotEmpty() }
        save(compose.onNodeWithTag("form_dialog").captureToImage().asAndroidBitmap(), "closet-ready-outfit-gear.png")
        compose.onNodeWithTag("open_avatar_reference").assertDoesNotExist()
    }

    @Test fun oldWalletMigratesAndEveryNewSlotSurvivesReloadWithoutChangingPoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("runmoni",Context.MODE_PRIVATE)
        val original = prefs.getString("localProfile",null)
        try {
            prefs.edit().putString("localProfile", """{"name":"기존 러너","mileage":123,"owned":["accessory:2","top:1"],"avatar":{"accessory":2,"top":1}}""").commit()
            val wallet = LocalWallet(context)
            val legacy = wallet.read()
            assertEquals(1,legacy.avatar.variantFor("headwear")); assertTrue("headwear:1" in legacy.ownedItems)
            val a = AvatarStyle(hair=2,face=3,top=4,bottom=4,socks=2,shoes=1,headwear=1,ear=1,eyewear=1,wrist=1,waist=1,hydration=1,costume=3)
            wallet.update { it.copy(avatar=a) }
            assertEquals(a,LocalWallet(context).read().avatar)
            assertEquals(123,LocalWallet(context).read().mileage)
            assertEquals(a,with(PilotClient) { JSONObject(PilotClient.encodeAvatar(a).toString()).toAvatar() })
        } finally {
            prefs.edit().apply { if(original==null)remove("localProfile") else putString("localProfile",original) }.commit()
        }
    }
}
