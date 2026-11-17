package com.runmoni.app.avatar

import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Authored assets are checked against independent pinned source labels, not a mirrored drawing recipe. */
class ExpandedAtelierLayersTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it,"app/src/main/assets/avatars/v0.3.0/running-motion.png").isFile }
    private val assets = File(root,"app/src/main/assets/avatars")
    private val read = Class.forName("javax.imageio.ImageIO").getMethod("read",File::class.java)
    private val imageClass = Class.forName("java.awt.image.BufferedImage")
    private val getRgb = imageClass.getMethod("getRGB",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,IntArray::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
    private val sheets = mutableMapOf<String,Any>()
    private fun cell(path: String, gender: AvatarGender, frame: Int): IntArray = getRgb.invoke(
        sheets.getOrPut(path) { requireNotNull(read.invoke(null,File(assets,path))) },
        frame*256,gender.spriteRow*384,256,384,null,0,256) as IntArray
    private fun cel(path: String): IntArray {
        val image = requireNotNull(read.invoke(null,File(assets,"atelier-v2/$path")))
        assertEquals(256,imageClass.getMethod("getWidth").invoke(image))
        assertEquals(384,imageClass.getMethod("getHeight").invoke(image))
        return getRgb.invoke(image,0,0,256,384,null,0,256) as IntArray
    }

    @Test fun allThirteenSlotsAppendThreeWithoutChangingAnOldItemIdOrGivingAwayOwnership() {
        val counts = mapOf("hair" to 4,"headwear" to 4,"face" to 4,"top" to 7,"bottom" to 6,"costume" to 6,
            "socks" to 5,"shoes" to 5,"ear" to 4,"eyewear" to 4,"wrist" to 4,"waist" to 4,"hydration" to 4)
        // Pinned from the published0.8 catalogue, not generated from the new renderer.
        val legacy = AvatarCatalogue.slots.flatMap { slot -> slot.variants.take(counts.getValue(slot.key)).mapIndexed { index, name -> "${slot.key}:$index:$name" } }.joinToString("\n")
        assertEquals("424ec98526279b2e5442d951958d2ee7d26cd4901234b799729408cade564754",MessageDigest.getInstance("SHA-256").digest(legacy.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)})
        assertEquals(39,ExpandedAtelierLayers.specs.size)
        assertEquals(100,AvatarCatalogue.slots.sumOf { it.variants.size })
        assertEquals(9,AvatarCatalogue.tabs.size)
        for (slot in AvatarCatalogue.slots) {
            assertEquals(3,ExpandedAtelierLayers.specs.count { it.slot==slot.key })
            val old = counts.getValue(slot.key)
            assertEquals(old+3,slot.variants.size)
            assertEquals((old..old+2).toList(),ExpandedAtelierLayers.specs.filter { it.slot==slot.key }.map { it.variant })
            for (id in old..old+2) {
                assertTrue(AvatarCatalogue.valid(slot.key,id));assertEquals(0,AvatarCatalogue.price(id))
                assertFalse("New test-free access is not a saved purchase", "${slot.key}:$id" in AvatarCatalogue.defaultOwned)
            }
        }
        assertTrue(AvatarCatalogue.valid("accessory",3));assertFalse(AvatarCatalogue.valid("accessory",4))
    }

    @Test fun newSetsKeepTheSameIdentityAndAllowAnIndependentHatAndEveryAccessory() {
        val wearing=AvatarStyle(gender=AvatarGender.FEMALE,headwear=5,ear=6,eyewear=4,wrist=6,waist=5,hydration=4,
            top=8,bottom=7,socks=6,shoes=5,hair=5,face=4,runningKitColors=RunningKitColors(2,4,5))
        for (set in 6..8) {
            val selected=wearing.withItem("costume",set)
            assertEquals(setOf("top","bottom","socks","shoes"),AvatarCatalogue.coveredSlots(set))
            assertEquals(5,selected.variantFor("headwear"));assertEquals(6,selected.variantFor("ear"))
            val drawing=ExpandedAtelierLayers.foundation(selected)
            assertEquals(AvatarGender.FEMALE,drawing.gender)
            assertEquals(RunningKitColors(2,4,5),drawing.runningKitColors)
            assertEquals(2,ExpandedAtelierLayers.selected(selected).count { it.slot in setOf("hair","face") })
            assertFalse(ExpandedAtelierLayers.selected(selected).any { it.slot in AvatarCatalogue.coveredSlots(set) })
            val separate=selected.withSelectedItem("top",9)
            assertEquals(0,separate.costume);assertEquals(9,separate.top);assertEquals(5,separate.headwear)
            assertEquals(4,separate.runningKitColors.bottom);assertEquals(5,separate.runningKitColors.shoes)
        }
    }

    @Test fun exportedCelsAreNonemptyPoseBoundAndKeepOriginalAlphaAndEveryPixelOutsideTheirAllowance() {
        val originalFile=File(assets,"v0.3.0/running-motion.png")
        assertEquals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65",
            MessageDigest.getInstance("SHA-256").digest(originalFile.readBytes()).joinToString(""){"%02x".format(it)})
        var cels=0;var tintCels=0
        for (gender in AvatarGender.entries) for (frame in 0..7) {
            val original=cell("v0.3.0/running-motion.png",gender,frame)
            val fabric=cell("running-kit-v1/fabric-mask.png",gender,frame)
            val coverage=cell("wardrobe-v1/coverage.png",gender,frame)
            val socks=cell("socks-v1/sock-mask.png",gender,frame)
            val hands=cell("wardrobe-v1/hand-protection.png",gender,frame)
            val inputs=listOf(original,fabric,coverage,socks,hands);val copies=inputs.map {it.clone()}
            for (spec in ExpandedAtelierLayers.specs) {
                val path="${spec.key}/${gender.wireValue}-$frame"
                val detail=cel("$path.png");val tint=if(spec.tinted) cel("$path-tint.png").also {tintCels++} else null
                val allowed=ExpandedAtelierLayers.allowance(spec,original,fabric,coverage,socks,hands,frame,gender)
                val incoming=if(spec.slot=="headwear") {
                    val kind=when(spec.base){1->AvatarHeadwearLayers.Kind.CAP;2->AvatarHeadwearLayers.Kind.VISOR;else->AvatarHeadwearLayers.Kind.HEADBAND}
                    AvatarHeadwearLayers.render(original,original,frame,gender,kind)
                } else original
                val actual=ExpandedAtelierLayers.apply(incoming,detail,tint,allowed,RunningKitColors(1,2,3))
                assertTrue("Empty authored cel $path",detail.any {it ushr 24!=0})
                assertTrue("Authored cel lacks a visible construction $path",actual.indices.count { actual[it]!=incoming[it] }>20)
                for(i in actual.indices) {
                    if ((incoming[i] ushr 24) != (actual[i] ushr 24)) fail("Alpha $path/$i")
                    if (allowed[i]==0 && incoming[i]!=actual[i]) fail("Escaped $path/$i")
                    if (spec.slot in setOf("top","bottom","costume","shoes") && hands[i]!=0 && incoming[i]!=actual[i])
                        fail("Foreground hand $path/$i")
                }
                cels++
            }
            inputs.zip(copies).forEach {(a,b)->assertArrayEquals(b,a)}
        }
        assertEquals(624,cels);assertEquals(192,tintCels)
    }

    @Test fun malformedTintAndEscapedInkFailClosedWithoutMutatingAnInput() {
        val pixels=IntArray(ExpandedAtelierLayers.SIZE) {-1};val before=pixels.clone()
        val detail=IntArray(pixels.size);detail[4]=0xffc0d0d0.toInt()
        val mask=IntArray(pixels.size);mask[4]=-1
        val tint=IntArray(pixels.size);tint[4]=0xff040000.toInt()
        assertThrows(IllegalArgumentException::class.java) { ExpandedAtelierLayers.apply(pixels,detail,tint,mask,RunningKitColors()) }
        tint[4]=0xff010001.toInt()
        assertThrows(IllegalArgumentException::class.java) { ExpandedAtelierLayers.apply(pixels,detail,tint,mask,RunningKitColors()) }
        detail[4]=0x80c0d0d0.toInt()
        assertThrows(IllegalArgumentException::class.java) { ExpandedAtelierLayers.apply(pixels,detail,null,mask,RunningKitColors()) }
        detail[4]=-1;mask[4]=0
        assertThrows(IllegalArgumentException::class.java) { ExpandedAtelierLayers.apply(pixels,detail,null,mask,RunningKitColors()) }
        assertArrayEquals(before,pixels)
    }
}
