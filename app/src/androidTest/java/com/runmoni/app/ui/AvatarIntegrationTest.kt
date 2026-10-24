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

    @Test fun nativeEightFrameRendererAndAllGearProduceEightDistinctCaptures() {
        val frame = mutableIntStateOf(0)
        val avatar = AvatarStyle(hair = 2, face = 1, top = 4, bottom = 4, socks = 2, shoes = 1,
            headwear = 1, ear = 1, eyewear = 1, wrist = 1, waist = 1, hydration = 1)
        compose.setContent { RunmoniTheme { MiniMi(avatar, Modifier.size(192.dp, 224.dp).testTag("avatar_fixture"), running = true, frameOverride = frame.intValue) } }
        val hashes = mutableSetOf<Int>()
        for (f in 0..7) {
            compose.runOnIdle { frame.intValue = f }
            val bitmap = compose.onNodeWithTag("avatar_fixture").captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            hashes += pixels.contentHashCode(); save(bitmap, "native-run-$f.png")
        }
        assertEquals(8, hashes.size)
    }

    @Test fun nineTabsSelectClothesAndSimultaneousEquipmentThenExitSpecialCostume() {
        val owned = AvatarCatalogue.slots.flatMap { s -> s.variants.indices.map { "${s.key}:$it" } }.toSet()
        val state = mutableStateOf(AppState(profile = Profile(ownedItems = owned)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile, actions) {} } }
        compose.onNodeWithTag("closet_animation").performClick()
        fun pick(slot: String, v: Int) {
            if (slot in AvatarCatalogue.accessoryKeys) compose.onNodeWithTag("closet_slot_accessories").performScrollTo().performClick()
            compose.onNodeWithTag("closet_slot_$slot").performScrollTo().performClick()
            compose.onNodeWithTag("closet_${slot}_$v").performScrollTo().performClick()
        }
        for ((slot,v) in listOf("hair" to 2, "face" to 1, "top" to 4, "bottom" to 4, "socks" to 2, "shoes" to 1,
            "headwear" to 1, "ear" to 1, "eyewear" to 1, "wrist" to 1, "waist" to 1, "hydration" to 1)) pick(slot,v)
        compose.runOnIdle {
            val a = state.value.profile.avatar
            assertEquals(4,a.top); assertEquals(4,a.bottom); assertEquals(2,a.socks)
            for (slot in listOf("headwear","ear","eyewear","wrist","waist","hydration")) assertEquals(1,a.variantFor(slot))
        }
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        save(compose.onNodeWithTag("form_dialog").captureToImage().asAndroidBitmap(), "closet-running-gear.png")
        pick("costume",1)
        compose.onNodeWithTag("closet_avatar_preview").assertIsDisplayed()
        save(compose.onNodeWithTag("form_dialog").captureToImage().asAndroidBitmap(), "closet-santa.png")
        pick("top",1)
        compose.runOnIdle { assertEquals(0,state.value.profile.avatar.costume); assertEquals(1,state.value.profile.avatar.wrist) }
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
