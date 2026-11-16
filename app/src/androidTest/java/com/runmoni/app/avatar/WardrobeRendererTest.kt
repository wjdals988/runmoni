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
            val fabric=RunningKitArtwork.frameMask(context,gender.spriteRow,frame)
            for(slot in AvatarCatalogue.slots) for(variant in 1 until slot.variants.size) {
                if(!AvatarArtwork.itemReady(slot.key,variant,gender)) continue
                val style=base.withItem(slot.key,variant)
                val actual=pixels(renderer.render(frame,true,style))
                assertTrue("Unchanged selectable ${gender.wireValue}/$frame/${slot.key}:$variant", original.indices.count { original[it]!=actual[it] }>20)
                val costume=slot.key=="costume" && AvatarCostumeLayers.supports(variant)
                val headKind=if(slot.key=="headwear") when(variant) {
                    1 -> AvatarHeadwearLayers.Kind.CAP
                    2 -> AvatarHeadwearLayers.Kind.VISOR
                    3 -> AvatarHeadwearLayers.Kind.HEADBAND
                    else -> null
                } else null
                val extension=if(costume) AvatarCostumeLayers.clothingExtensionMask(original,frame,gender,variant)
                    else if(headKind!=null) AvatarHeadwearLayers.extensionMask(original,frame,gender,headKind) else IntArray(original.size)
                val occlusion=if(costume) AvatarCostumeLayers.hairOcclusionMask(original,frame,gender,variant)
                    else if(headKind!=null) AvatarHeadwearLayers.occlusionMask(original,frame,gender,headKind) else IntArray(original.size)
                val headEnd=if(gender==AvatarGender.MALE)182 else 176
                for(i in actual.indices) {
                    if((original[i] ushr 24)!=(actual[i] ushr 24) && extension[i]==0 && occlusion[i]==0)
                        fail("Body alpha changed ${slot.key}:$variant at $i")
                    if(extension[i]!=0) {
                        assertEquals(-1,extension[i])
                        assertTrue("Hat/hood allowance reaches the body",i/256 in 14 until headEnd)
                        assertTrue("Hat/hood allowance reaches a frame edge",i%256 in 8..247)
                        assertTrue("Hat/hood replaced original hair/face",(original[i] ushr 24)<=16)
                    }
                    if(occlusion[i]!=0) {
                        val alpha=original[i] ushr 24
                        assertEquals(-1,occlusion[i])
                        assertTrue("Headwear occlusion escaped existing hair",alpha>16 && i/256 in 14 until 174)
                        assertEquals("Head fringe alpha patch differs",if(alpha<192)253 else alpha,actual[i] ushr 24)
                    }
                    // Shirt shoulders can be above this row boundary. Only the
                    // existing pinned fabric1 label may change there; the
                    // loader independently rejects labels on protected identity.
                    if(costume && occlusion[i]==0 && i/256 < headEnd && ((fabric[i] ushr 16)and 255)!=1 && (original[i] ushr 24)>16)
                        assertEquals("Costume changed original face/hair at $i",original[i],actual[i])
                }
                assertArrayEquals("App/map entry point ignores equipped item",pixels(renderer.renderForDisplay(frame,true,style)),pixels(avatarBitmap(context,style,frame,true)))
            }
        }
        assertTrue(renderer.cachedColorBytes()<=ApprovedAvatarRenderer.COLOR_CACHE_BYTES)
    }

    @Test fun capAndVisorKeepTheirClosedTipsAndOriginalFaceOnActualAndroidBitmaps() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val base=AvatarStyle(gender=gender)
            val original=pixels(renderer.render(frame,true,base))
            val eye=AvatarEquipmentLayers.anchors(frame,gender).eye
            for(variant in 1..2) {
                val style=base.withItem("headwear",variant)
                assertTrue(AvatarArtwork.styleReady(style))
                val actual=pixels(renderer.render(frame,true,style))
                var wornHair=0
                for(i in actual.indices) {
                    val x=i%256;val y=i/256
                    if(y>=174 || x>=247)
                        assertEquals("Cap/visor changed body or cut at the right crop boundary",original[i],actual[i])
                    if(x in eye.x-12..eye.x+66 && y in eye.y-10..eye.y+28)
                        assertEquals("Cap/visor changed original eyes or face",original[i],actual[i])
                    if(x in eye.x-58..eye.x-28 && y in eye.y-5..eye.y+23)
                        assertEquals("Cap/visor changed the source ear",original[i],actual[i])
                    if(y in 14 until 174 && (original[i] ushr 24)>16 && original[i]!=actual[i])wornHair++
                }
                assertTrue("Cap/visor stayed behind the hair instead of being worn",wornHair>100)
                assertArrayEquals("Shared app/map entry point lost the bounded headwear",
                    pixels(renderer.renderForDisplay(frame,true,style)),pixels(avatarBitmap(context,style,frame,true)))
            }
        }
    }

    @Test fun torsoEquipmentPreservesTheOriginalForegroundArmsAndHands() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val base=AvatarStyle(gender=gender)
            val original=pixels(renderer.render(frame,true,base))
            val mask=RunningKitArtwork.frameMask(context,gender.spriteRow,frame)
            val hands=WardrobeCoverage.handProtection(context,gender.spriteRow,frame)
            val style=base.copy(waist=2,hydration=3)
            val equipped=pixels(renderer.render(frame,true,style))
            assertFalse(original.contentEquals(equipped))
            for(i in original.indices) if(mask[i]==0 || hands[i]!=0) assertEquals("Torso equipment covered a foreground limb at $i",original[i],equipped[i])
        }
    }

    @Test fun savedPendingCostumeKeepsOccupancyAndUnrelatedEquipmentWithoutMigratingIds() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val saved=AvatarStyle(gender=gender,costume=2,top=3,bottom=1,shoes=2,headwear=3,wrist=2)
            assertFalse(AvatarArtwork.styleReady(saved))
            assertArrayEquals(pixels(renderer.render(frame,true,saved.copy(top=0,bottom=0))),pixels(renderer.render(frame,true,saved)))
            val normal=saved.withItem("top",1)
            assertEquals(0,normal.costume); assertEquals(2,normal.wrist)
            assertFalse(pixels(renderer.render(frame,true,saved)).contentEquals(pixels(renderer.render(frame,true,normal))))
            assertEquals(2,saved.costume);assertEquals(3,saved.top)
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
