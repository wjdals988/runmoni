package com.runmoni.app.avatar

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.*
import com.runmoni.app.ui.avatarBitmap
import org.junit.Assert.*
import org.junit.Test

/** Actual Android bitmap output, independently of thumbnail state/selected labels. */
class WardrobeRendererTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(image: Bitmap)=IntArray(image.width*image.height).also { image.getPixels(it,0,image.width,0,0,image.width,image.height) }

    @Test fun everySelectableNonDefaultItemActuallyChangesEveryApprovedPose() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val base=AvatarStyle(gender=gender)
            val original=pixels(renderer.render(frame,true,base))
            for(slot in AvatarCatalogue.slots) for(variant in 1 until slot.variants.size) {
                if(!AvatarArtwork.itemReady(slot.key,variant,gender)) continue
                val style=base.withItem(slot.key,variant)
                val actual=pixels(renderer.render(frame,true,style))
                assertTrue("Unchanged selectable ${gender.wireValue}/$frame/${slot.key}:$variant", original.indices.count { original[it]!=actual[it] }>20)
                for(i in actual.indices) if((original[i] ushr 24)!=(actual[i] ushr 24))
                    fail("Body alpha changed ${slot.key}:$variant at $i")
                assertArrayEquals("App/map entry point ignores equipped item",pixels(renderer.renderForDisplay(frame,true,style)),pixels(avatarBitmap(context,style,frame,true)))
            }
        }
        assertTrue(renderer.cachedColorBytes()<=ApprovedAvatarRenderer.COLOR_CACHE_BYTES)
    }

    @Test fun torsoEquipmentPreservesTheOriginalForegroundArmsAndHands() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val base=AvatarStyle(gender=gender)
            val original=pixels(renderer.render(frame,true,base))
            val mask=RunningKitArtwork.frameMask(context,gender.spriteRow,frame)
            val style=base.copy(waist=2,hydration=3)
            val equipped=pixels(renderer.render(frame,true,style))
            assertFalse(original.contentEquals(equipped))
            for(i in original.indices) if(mask[i]==0) assertEquals("Torso equipment covered a foreground limb at $i",original[i],equipped[i])
        }
    }

    @Test fun savedPendingCostumeKeepsOccupancyAndUnrelatedEquipmentWithoutMigratingIds() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val saved=AvatarStyle(gender=gender,costume=1,top=3,bottom=1,shoes=2,headwear=3,wrist=2)
            assertFalse(AvatarArtwork.styleReady(saved))
            assertArrayEquals(pixels(renderer.render(frame,true,saved.copy(top=0,bottom=0,shoes=0,headwear=0))),pixels(renderer.render(frame,true,saved)))
            val normal=saved.withItem("top",1)
            assertEquals(0,normal.costume); assertEquals(2,normal.wrist)
            assertFalse(pixels(renderer.render(frame,true,saved)).contentEquals(pixels(renderer.render(frame,true,normal))))
            assertEquals(1,saved.costume);assertEquals(3,saved.top)
        }
    }

    @Test fun explicitColorsOverrideItemDefaultsAndReturningToZeroRestoresTheItemPalette() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) {
            val item=AvatarStyle(gender=gender,top=2,bottom=1,shoes=3)
            val originalItem=pixels(renderer.render(0,true,item))
            val custom=item.copy(runningKitColors=RunningKitColors(4,2,3))
            assertFalse(originalItem.contentEquals(pixels(renderer.render(0,true,custom))))
            assertArrayEquals(originalItem,pixels(renderer.render(0,true,custom.copy(runningKitColors=RunningKitColors()))))
            assertFalse(originalItem.contentEquals(pixels(renderer.render(0,true,AvatarStyle(gender=gender)))))
        }
    }
}
