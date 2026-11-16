package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.data.LocalWallet
import com.runmoni.app.data.PilotClient
import com.runmoni.app.domain.*
import com.runmoni.app.ui.avatarBitmap
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** The actual Android renderer/persistence entry points; no host preview substitutes. */
class AtelierWardrobeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(image: Bitmap) = IntArray(image.width * image.height).also {
        image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
    }

    @Test fun appendedMaterialsReachTheAndroidAndMapOutputsWithoutChangingIdentityOrSourceCache() {
        val renderer = ApprovedAvatarRenderer(context)
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val base = AvatarStyle(gender = gender)
            val original = pixels(renderer.render(frame, true, base))
            val fabric = RunningKitArtwork.frameMask(context, gender.spriteRow, frame)
            val sleeves = WardrobeCoverage.frame(context, gender.spriteRow, frame)
            val hands = WardrobeCoverage.handProtection(context, gender.spriteRow, frame)
            val socks = SockArtwork.frameMask(context, gender.spriteRow, frame)
            val shoeLocks = SockArtwork.shoeProtection(context, gender.spriteRow, frame)
            val style = base.copy(top = 6, socks = 4, shoes = 4)
            assertTrue(AvatarArtwork.styleReady(style))
            val actual = pixels(renderer.render(frame, true, style))
            for (i in actual.indices) {
                val region = (fabric[i] ushr 16) and 255
                val garment = (region == 1 || ((sleeves[i] ushr 16) and 1) != 0) && hands[i] == 0
                val sock = ((socks[i] ushr 16) and 1) != 0
                if (!garment && !sock && region != 3)
                    assertEquals("New clothes escaped the pinned source labels $gender/$frame/$i", original[i], actual[i])
                if (i / 256 < 174 && !garment || hands[i] != 0 || !garment && shoeLocks[i] != 0 && region != 3)
                    assertEquals("New clothes changed identity, a foreground hand or a sole", original[i], actual[i])
                assertEquals("Material changed the original silhouette alpha", original[i] ushr 24, actual[i] ushr 24)
            }
            assertTrue("Combined construction is missing", original.indices.count { original[it] != actual[it] } > 500)
            assertArrayEquals("Shared map/app renderer omitted new material", pixels(renderer.renderForDisplay(frame, true, style)),
                pixels(avatarBitmap(context, style, frame, true)))
            assertArrayEquals("New colored cache polluted the original", original, pixels(renderer.render(frame, true, base)))
            if (frame == 0) {
                val custom = style.copy(runningKitColors = RunningKitColors(4, 0, 2))
                assertFalse(actual.contentEquals(pixels(renderer.render(frame, true, custom))))
                assertArrayEquals(actual, pixels(renderer.render(frame, true, custom.copy(runningKitColors = RunningKitColors()))))
            }
        }
        assertTrue(renderer.cachedColorBytes() <= ApprovedAvatarRenderer.COLOR_CACHE_BYTES)
    }

    @Test fun appendedIdsRoundTripWithoutChargingOrErasingOldOwnedAndPendingChoices() {
        val name = "atelier_wallet_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            assertTrue(preferences.edit().putString("localProfile",
                """{"name":"기존 러너","mileage":777,"owned":["top:4","socks:3","accessory:2","wrist:2"],"avatar":{"top":4,"socks":3,"wrist":2,"costume":2}}""").commit())
            val wallet = LocalWallet(preferences)
            val old = wallet.read()
            assertEquals(4, old.avatar.top)
            assertEquals(3, old.avatar.socks)
            assertEquals(2, old.avatar.costume)
            for (gender in AvatarGender.entries) {
                val selected = old.avatar.copy(gender = gender, runningKitColors = RunningKitColors(4, 2, 3))
                    .withSelectedItem("top", 6).withSelectedItem("socks", 4).withSelectedItem("shoes", 4)
                assertEquals(0, selected.costume)
                wallet.update { it.copy(avatar = selected) }
                val restored = LocalWallet(preferences).read()
                val canonical = selected.copy(headwear = selected.variantFor("headwear"), ear = selected.variantFor("ear"))
                assertEquals(canonical, restored.avatar)
                assertEquals(old.name, restored.name)
                assertEquals(old.mileage, restored.mileage)
                assertEquals(old.ownedItems, restored.ownedItems)
                assertEquals(RunningKitColors(0, 2, 0), restored.avatar.runningKitColors)
                assertEquals(2, restored.avatar.wrist)
                val wire = JSONObject(PilotClient.encodeAvatar(restored.avatar).toString())
                assertEquals(canonical, with(PilotClient) { wire.toAvatar() })
                for ((slot, id) in listOf("top" to 6, "socks" to 4, "shoes" to 4)) {
                    assertEquals(0, AvatarCatalogue.price(id))
                    assertFalse("Free testing fabricated an owned item", "${slot}:$id" in restored.ownedItems)
                }
            }
        } finally { assertTrue(context.deleteSharedPreferences(name)) }
    }
}
