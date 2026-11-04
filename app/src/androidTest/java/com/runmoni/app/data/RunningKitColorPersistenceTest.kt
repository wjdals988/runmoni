package com.runmoni.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.RunningKitRegion
import com.runmoni.app.domain.variantFor
import com.runmoni.app.domain.withItem
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real preferences and JSON: new color fields cannot erase an older wallet. */
class RunningKitColorPersistenceTest {
    private fun preservingProfile(block: (SharedPreferences, LocalWallet) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
        val original = preferences.getString("localProfile", null)
        try { block(preferences, LocalWallet(context)) }
        finally {
            assertTrue(preferences.edit().apply {
                if (original == null) remove("localProfile") else putString("localProfile", original)
            }.commit())
        }
    }

    @Test fun legacyWalletColorsAndGenderPersistWithoutLosingMileageOrOwnership() = preservingProfile { preferences, wallet ->
        assertTrue(preferences.edit().putString("localProfile",
            """{"name":"기존 러너","mileage":777,"owned":["top:2","accessory:2","wrist:1"],"avatar":{"top":2,"accessory":2}}""").commit())
        val old = wallet.read()
        assertEquals(RunningKitColors(), old.avatar.runningKitColors)
        wallet.update { it.copy(avatar = it.avatar.copy(runningKitColors = RunningKitColors(1, 2, 5))) }
        wallet.update { it.copy(avatar = it.avatar.copy(gender = AvatarGender.FEMALE).withItem("wrist", 1)) }
        val restored = LocalWallet(InstrumentationRegistry.getInstrumentation().targetContext).read()
        assertEquals("기존 러너", restored.name)
        assertEquals(777, restored.mileage)
        assertEquals(old.ownedItems, restored.ownedItems)
        assertEquals(2, restored.avatar.top)
        assertEquals(1, restored.avatar.variantFor("headwear"))
        assertEquals(1, restored.avatar.wrist)
        assertEquals(AvatarGender.FEMALE, restored.avatar.gender)
        assertEquals(RunningKitColors(1, 2, 5), restored.avatar.runningKitColors)
        wallet.update { it.copy(avatar = it.avatar.copy(runningKitColors = it.avatar.runningKitColors.withColor(RunningKitRegion.TOP, 0))) }
        val reset = LocalWallet(InstrumentationRegistry.getInstrumentation().targetContext).read()
        assertEquals(RunningKitColors(0, 2, 5), reset.avatar.runningKitColors)
        assertEquals(777, reset.mileage)
        assertEquals(old.ownedItems, reset.ownedItems)
    }

    @Test fun damagedColorDataFallsBackPerRegionInsteadOfResettingTheWholeProfile() = preservingProfile { preferences, wallet ->
        val cases = listOf(
            "null" to RunningKitColors(),
            "\"unexpected\"" to RunningKitColors(),
            "[]" to RunningKitColors(),
            """{"top":"3","bottom":false,"shoes":5.0}""" to RunningKitColors(),
            """{"top":-1,"bottom":4,"shoes":9000000000}""" to RunningKitColors(0, 4, 0),
            """{"top":true,"bottom":2,"shoes":5}""" to RunningKitColors(0, 2, 5),
        )
        for ((encoded, expected) in cases) {
            assertTrue(preferences.edit().putString("localProfile",
                """{"name":"색상 복구 러너","mileage":777,"owned":["top:2","accessory:2"],"avatar":{"top":2,"wrist":1,"gender":"female","runningKitColors":$encoded}}""").commit())
            val profile = wallet.read()
            assertEquals(encoded, "색상 복구 러너", profile.name)
            assertEquals(encoded, 777, profile.mileage)
            assertEquals(encoded, 2, profile.avatar.top)
            assertEquals(encoded, 1, profile.avatar.wrist)
            assertEquals(encoded, AvatarGender.FEMALE, profile.avatar.gender)
            assertTrue(encoded, profile.ownedItems.containsAll(listOf("top:2", "accessory:2", "headwear:1")))
            assertEquals(encoded, expected, profile.avatar.runningKitColors)
        }
    }

    @Test fun paletteIdsRoundTripIndependentlyAndEncodingNormalizesInvalidDomainValues() {
        val base = AvatarStyle(top = 4, bottom = 4, costume = 1, gender = AvatarGender.FEMALE,
            headwear = 1, ear = 1, runningKitColors = RunningKitColors(1, 2, 3))
        for (region in RunningKitRegion.entries) for (color in 0 until RunningKitColors.COLOR_COUNT) {
            val original = base.copy(runningKitColors = base.runningKitColors.withColor(region, color))
            val encoded = PilotClient.encodeAvatar(original)
            assertEquals(original, with(PilotClient) { JSONObject(encoded.toString()).toAvatar() })
        }
        val invalid = base.copy(runningKitColors = RunningKitColors(-1, 4, Int.MAX_VALUE))
        val decoded = with(PilotClient) { PilotClient.encodeAvatar(invalid).toAvatar() }
        assertEquals(base.copy(runningKitColors = RunningKitColors(0, 4, 0)), decoded)
        assertEquals(RunningKitColors(-1, 4, Int.MAX_VALUE), invalid.runningKitColors)
    }

    @Test fun originalColorResetRequiresAnExplicitValidServerAcknowledgement() {
        for (response in listOf(
            "{}", """{"avatar":{}}""", """{"avatar":{"runningKitColors":{}}}""",
            """{"avatar":{"runningKitColors":{"top":null}}}""",
            """{"avatar":{"runningKitColors":{"top":"0"}}}""",
            """{"avatar":{"runningKitColors":{"top":false}}}""",
            """{"avatar":{"runningKitColors":{"top":0.0}}}""",
            """{"avatar":{"runningKitColors":{"top":1}}}""",
        )) {
            assertThrows(IllegalStateException::class.java) {
                with(PilotClient) { JSONObject(response).confirmedRunningKitColor(RunningKitRegion.TOP, 0) }
            }
        }
        for (color in 0 until RunningKitColors.COLOR_COUNT) {
            val response = JSONObject().put("avatar", JSONObject().put("runningKitColors", JSONObject().put("top", color)))
            assertEquals(color, with(PilotClient) { response.confirmedRunningKitColor(RunningKitRegion.TOP, color) })
        }
    }
}
