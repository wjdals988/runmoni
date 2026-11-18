package com.runmoni.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.avatar.*
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual Android production renderer. Contact sheets are renderer evidence, not MainActivity screenshots. */
class AtelierExpansionRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    }
    private fun save(bitmap: Bitmap,name: String) {
        val dir=File(context.cacheDir,"design-review").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    private fun inputs(original: IntArray,gender: AvatarGender,frame: Int) = listOf(original,
        RunningKitArtwork.frameMask(context,gender.spriteRow,frame),
        WardrobeCoverage.frame(context,gender.spriteRow,frame),
        SockArtwork.frameMask(context,gender.spriteRow,frame),
        WardrobeCoverage.handProtection(context,gender.spriteRow,frame))

    @Test fun allThirtyNineItemsRenderForBothOriginalIdentities() {
        val renderer=ApprovedAvatarRenderer(context)
        val contact=Bitmap.createBitmap(74*6,112*13,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(contact).apply {drawColor(0xfff1f6f2.toInt())}
        val paint=Paint().apply {isFilterBitmap=false}
        var count=0
        for ((index,spec) in ExpandedAtelierLayers.specs.withIndex()) for(gender in AvatarGender.entries) {
            val base=AvatarStyle(gender=gender)
            val selected=base.withSelectedItem(spec.slot,spec.variant)
            assertTrue("Not ready ${spec.key}/$gender",AvatarArtwork.styleReady(selected))
            val original=pixels(renderer.render(0,true,base))
            val rendered=renderer.render(0,true,selected)
            val actual=pixels(rendered)
            assertTrue("Selected asset must change more than twenty original pixels ${spec.key}/$gender",
                original.indices.count { original[it]!=actual[it] }>20)
            assertEquals(base,base.copy())
            val display=renderer.renderForDisplay(0,true,selected)
            val left=(index%3*2+gender.spriteRow)*74;val top=(index/3)*112
            canvas.drawBitmap(display,null,Rect(left,top,left+74,top+112),paint)
            assertArrayEquals("Original cache changed",original,pixels(renderer.render(0,true,base)))
            count++
        }
        assertEquals(78,count)
        save(contact,"atelier-expansion-112")
        contact.recycle()
    }

    @Test fun fullEquipmentAndNewSetsPreserveTheOriginalPoseAcrossEightFrames() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val original=pixels(renderer.render(frame,true,AvatarStyle(gender=gender)))
            val data=inputs(original,gender,frame)
            val base=AvatarStyle(gender=gender,hair=4,face=5,headwear=4,top=7,bottom=7,socks=6,shoes=6,
                ear=4,eyewear=5,wrist=6,waist=4,hydration=6)
            for(style in listOf(base,base.copy(costume=6),base.copy(costume=7),base.copy(costume=8))) {
                val actual=pixels(renderer.render(frame,true,style))
                val allow=IntArray(original.size)
                val core=ExpandedAtelierLayers.foundation(style)
                // Base coverage as well as newly authored ink is intentional cloth.
                for(i in allow.indices) {
                    val region=(data[1][i] ushr 16) and 255
                    val flags=(data[2][i] ushr 16) and 255
                    val sleeve=if(core.top==1) 1 else if(core.top in 2..3) 2 else 0
                    if((region in 1..3 || (flags and sleeve)!=0 || core.bottom==1 && (flags and 4)!=0) && data[4][i]==0)
                        allow[i]=-1
                    if(core.socks>0 && ((data[3][i] ushr 16) and core.socks)!=0) allow[i]=-1
                }
                for(spec in ExpandedAtelierLayers.selected(style)) {
                    val a=ExpandedAtelierLayers.allowance(spec,data[0],data[1],data[2],data[3],data[4],frame,gender)
                    for(i in allow.indices) if(a[i]!=0) allow[i]=-1
                }
                val head=ExpandedAtelierLayers.spec("headwear",style.headwear ?: 0)!!
                val headMask=ExpandedAtelierLayers.allowance(head,data[0],data[1],data[2],data[3],data[4],frame,gender)
                for(i in actual.indices) {
                    if(allow[i]==0 && actual[i]!=original[i]) fail("Original pose/identity escaped $gender/$frame/${style.costume}/$i")
                    if(headMask[i]==0 && (actual[i] ushr 24)!=(original[i] ushr 24)) fail("Body alpha changed $gender/$frame/$i")
                }
                assertTrue(actual.indices.count {actual[it]!=original[it]}>500)
            }
        }
    }

    @Test fun mixedGenerationEquipmentKeepsBeltsAndGlassesInFrontOfVestsAndEarDevices() {
        val renderer=ApprovedAvatarRenderer(context)
        var vestBeltOverlaps=0;var earGlassOverlaps=0
        for(gender in AvatarGender.entries) for(frame in 0..7) {
            val original=pixels(renderer.render(frame,true,AvatarStyle(gender=gender)))
            val data=inputs(original,gender,frame)
            val belt=pixels(renderer.render(frame,true,AvatarStyle(gender=gender,waist=1)))
            val vest=ExpandedAtelierLayers.allowance(ExpandedAtelierLayers.spec("hydration",5)!!,
                data[0],data[1],data[2],data[3],data[4],frame,gender)
            val vestAndBelt=pixels(renderer.render(frame,true,AvatarStyle(gender=gender,waist=1,hydration=5)))
            for(i in original.indices) if(belt[i]!=original[i]) {
                if(vest[i]!=0) vestBeltOverlaps++
                if(vestAndBelt[i]!=belt[i]) fail("Authored vest covered the foreground legacy belt $gender/$frame/$i")
            }
            val glasses=pixels(renderer.render(frame,true,AvatarStyle(gender=gender,eyewear=1)))
            val ear=ExpandedAtelierLayers.allowance(ExpandedAtelierLayers.spec("ear",6)!!,
                data[0],data[1],data[2],data[3],data[4],frame,gender)
            val earAndGlasses=pixels(renderer.render(frame,true,AvatarStyle(gender=gender,eyewear=1,ear=6)))
            for(i in original.indices) if(glasses[i]!=original[i]) {
                if(ear[i]!=0) earGlassOverlaps++
                if(earAndGlasses[i]!=glasses[i]) fail("Authored ear device covered foreground legacy glasses $gender/$frame/$i")
            }
        }
        assertTrue("Fixture must exercise actual belt/vest overlap",vestBeltOverlaps>10)
        assertTrue("Fixture must exercise actual glasses/ear overlap",earGlassOverlaps>10)
    }

    @Test fun garmentPaletteChangesCannotTintHairSkinOrSoles() {
        val renderer=ApprovedAvatarRenderer(context)
        for(gender in AvatarGender.entries) for(frame in listOf(0,5)) {
            val original=pixels(renderer.render(frame,true,AvatarStyle(gender=gender)))
            val data=inputs(original,gender,frame)
            for ((slot,variant,region) in listOf(Triple("top",8,RunningKitRegion.TOP),Triple("bottom",7,RunningKitRegion.BOTTOM),Triple("shoes",6,RunningKitRegion.SHOES))) {
                val selected=AvatarStyle(gender=gender).withSelectedItem(slot,variant)
                val before=pixels(renderer.render(frame,true,selected))
                val spec=ExpandedAtelierLayers.spec(slot,variant)!!
                val allowed=ExpandedAtelierLayers.allowance(spec,data[0],data[1],data[2],data[3],data[4],frame,gender)
                for(color in 1..5) {
                    val after=pixels(renderer.render(frame,true,selected.copy(runningKitColors=selected.runningKitColors.withColor(region,color))))
                    for(i in after.indices) if(allowed[i]==0 && before[i]!=after[i]) fail("Palette affected identity/sole $gender/$slot/$color/$i")
                }
            }
        }
    }
}
