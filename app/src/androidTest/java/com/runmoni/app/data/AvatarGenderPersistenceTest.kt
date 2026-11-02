package com.runmoni.app.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.variantFor
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real SharedPreferences/JSON compatibility. No server, account or artwork generation. */
class AvatarGenderPersistenceTest {
    @Test fun oldWalletAndGenderRoundTripPreserveMileageEquipmentAndOwnership() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
        val original = preferences.getString("localProfile", null)
        try {
            assertTrue(preferences.edit().putString("localProfile",
                """{"name":"기존 러너","mileage":777,"owned":["top:2","accessory:2"],"avatar":{"top":2,"accessory":2}}""").commit())
            val wallet = LocalWallet(context)
            val legacy = wallet.read()
            assertEquals(AvatarGender.MALE, legacy.avatar.gender)
            assertEquals(2, legacy.avatar.top)
            assertEquals(1, legacy.avatar.variantFor("headwear"))
            val female = legacy.avatar.copy(gender = AvatarGender.FEMALE,
                headwear = legacy.avatar.variantFor("headwear"), ear = legacy.avatar.variantFor("ear"))
            wallet.update { it.copy(avatar = female) }
            val restored = LocalWallet(context).read()
            assertEquals(female, restored.avatar)
            assertEquals(777, restored.mileage)
            assertEquals(legacy.ownedItems, restored.ownedItems)
            val encoded = PilotClient.encodeAvatar(female)
            assertEquals("female", encoded.getString("gender"))
            assertEquals(female, with(PilotClient) { encoded.toAvatar() })
            val maleAgain = restored.avatar.copy(gender = AvatarGender.MALE)
            wallet.update { it.copy(avatar = maleAgain) }
            assertEquals(maleAgain, LocalWallet(context).read().avatar)
            assertEquals(legacy.ownedItems, LocalWallet(context).read().ownedItems)
        } finally {
            assertTrue(preferences.edit().apply {
                if (original == null) remove("localProfile") else putString("localProfile", original)
            }.commit())
        }
    }

    @Test fun unknownNullAndMissingGenderDoNotLoseOtherSavedEquipment() {
        for (encoded in listOf(
            """{"top":2,"wrist":1}""",
            """{"top":2,"wrist":1,"gender":null}""",
            """{"top":2,"wrist":1,"gender":"unknown"}""",
            """{"top":2,"wrist":1,"gender":17}""",
        )) {
            val avatar = with(PilotClient) { JSONObject(encoded).toAvatar() }
            assertEquals(AvatarGender.MALE, avatar.gender)
            assertEquals(2, avatar.top)
            assertEquals(1, avatar.wrist)
        }
    }
}
