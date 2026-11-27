package com.runmoni.app.avatar

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AvatarGender
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunningKitColors
import com.runmoni.app.domain.StudioCollection
import com.runmoni.app.domain.withSelectedItem
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudioWardrobeRendererTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    @Test fun everyAuthoredPoseRendersWithIndependentMaterialRolesAndUnchangedSourcePixels() {
        val renderer = ApprovedAvatarRenderer(context)
        val directory = StudioArtwork.DIRECTORY
        val files = context.assets.open("$directory/manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("files")
        }
        fun ink(name: String) = readAtelierCel(context, directory, name, files.getString(name))
        var renderedCels = 0
        var tintedCels = 0
        for (gender in AvatarGender.entries) for (frame in 0 until ApprovedAvatarRenderer.FRAMES) {
            val base = AvatarStyle(gender = gender)
            val source = pixels(renderer.render(frame, true, base))
            for (item in StudioCollection.items) {
                val style = base.withSelectedItem(item.slot, item.variant)
                assertTrue(AvatarArtwork.styleReady(style))
                val actual = pixels(renderer.render(frame, true, style))
                val stem = "${item.assetKey}/${gender.wireValue}-$frame"
                val authored = ink("$stem.png")
                assertTrue("Missing visible artwork $stem", actual.indices.count { actual[it] != source[it] } > 20)
                for (i in actual.indices) {
                    if (authored[i] ushr 24 == 0 && actual[i] != source[i]) fail("Unauthored pixel $stem/$i")
                    if (item.slot != "headwear" && item.slot != "hydration" && actual[i] ushr 24 != source[i] ushr 24)
                        fail("Changed silhouette $stem/$i")
                }
                if (item.tintRole != 0) {
                    val tint = ink("$stem-tint.png")
                    val recolored = pixels(renderer.render(frame, true, style.copy(runningKitColors = RunningKitColors(3, 4, 5))))
                    // Other regions also have tints in this style, so compare only the selected item's ink.
                    assertTrue("Missing material tint $stem", actual.indices.count { tint[it] != 0 && recolored[it] != actual[it] } > 20)
                    for (i in actual.indices) if (authored[i] != 0 && tint[i] == 0 && actual[i] != recolored[i])
                        fail("Changed fixed trim $stem/$i")
                    tintedCels++
                }
                renderedCels++
            }
            assertArrayEquals(source, pixels(renderer.render(frame, true, base)))
        }
        assertEquals(240, renderedCels)
        assertEquals(144, tintedCels)
        assertTrue(renderer.cachedColorBytes() <= ApprovedAvatarRenderer.COLOR_CACHE_BYTES)
    }
}
