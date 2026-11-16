package com.runmoni.app.avatar

import com.runmoni.app.domain.AvatarGender
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Tests actual approved source pixels; mathematical preservation is not visual approval. */
class AvatarHeadwearLayersTest {
    private val root=generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it,"app/src/main/assets/avatars/v0.3.0/running-motion.png").isFile }
    private val sourceFile=File(root,"app/src/main/assets/avatars/v0.3.0/running-motion.png")
    private val imageRead=Class.forName("javax.imageio.ImageIO").getMethod("read",File::class.java)
    private val getRgb=Class.forName("java.awt.image.BufferedImage").getMethod("getRGB",
        Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,IntArray::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
    private val sheet by lazy { requireNotNull(imageRead.invoke(null,sourceFile)) }
    private fun source(gender: AvatarGender,frame: Int)=
        getRgb.invoke(sheet,frame*256,gender.spriteRow*384,256,384,null,0,256) as IntArray

    @Test fun everyHeadwearActuallyCoversHairWhileKeepingFaceEyesSkinAndBody() {
        val beforeHash=digest()
        assertEquals("f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65",beforeHash)
        for(gender in AvatarGender.entries)for(frame in 0..7) {
            val original=source(gender,frame);val originalCopy=original.clone()
            val eye=AvatarEquipmentLayers.anchors(frame,gender).eye
            for(kind in AvatarHeadwearLayers.Kind.entries) {
                // A pre-existing material below the head must remain intact too.
                val pixels=original.clone().also { it[270*256+140]=0xff716047.toInt() }
                val pixelsCopy=pixels.clone()
                val result=AvatarHeadwearLayers.render(pixels,original,frame,gender,kind)
                val hair=AvatarHeadwearLayers.occlusionMask(original,frame,gender,kind)
                val extension=AvatarHeadwearLayers.extensionMask(original,frame,gender,kind)
                var hairChanges=0
                for(i in original.indices) {
                    assertTrue(hair[i]==0 || hair[i]==-1)
                    assertTrue(extension[i]==0 || extension[i]==-1)
                    assertFalse("Alpha extension overlaps hair coverage",hair[i]!=0 && extension[i]!=0)
                    if(extension[i]!=0) {
                        assertTrue("Extension replaced existing head",original[i] ushr 24<=16)
                        assertEquals(253,result[i] ushr 24)
                    } else if(hair[i]!=0 && (original[i] ushr 24)<192) {
                        assertEquals("Authored cloth left a transparent hair-edge crack",253,result[i] ushr 24)
                    } else assertEquals("Alpha outside the authored fringe changed",pixels[i] ushr 24,result[i] ushr 24)
                    if(hair[i]!=0) {
                        assertTrue("Hair allowance included transparent background",original[i] ushr 24>16)
                        if(result[i]!=original[i])hairChanges++
                    }
                    if(hair[i]==0 && extension[i]==0)assertEquals("Headwear escaped its allowance",pixels[i],result[i])
                    val x=i%256;val y=i/256
                    // Independent source-landmark locks; not the output mask's complement.
                    val eyesAndCheeks=x in eye.x-12..eye.x+66 && y in eye.y-10..eye.y+30
                    val visibleEar=x in eye.x-58..eye.x-28 && y in eye.y-5..eye.y+23
                    val forehead=x in eye.x+23..eye.x+52 && y in eye.y-43..eye.y-11
                    if(eyesAndCheeks || visibleEar || forehead || y>=176)
                        assertEquals("Original face/ear/body changed $gender/$frame/$kind/$x/$y",pixels[i],result[i])
                }
                assertTrue("Headwear remained a background ornament $gender/$frame/$kind",hairChanges>100)
                assertArrayEquals(pixelsCopy,pixels)
                assertArrayEquals(originalCopy,original)
            }
        }
        assertEquals(beforeHash,digest())
    }

    @Test fun originalPosesAndWrappedFrameIndexStayDistinctWithoutFrameNoise() {
        for(gender in AvatarGender.entries) {
            val hashes=AvatarHeadwearLayers.Kind.entries.associateWith { mutableSetOf<Int>() }
            for(frame in 0..7) {
                val original=source(gender,frame)
                for((kind,clips) in hashes) {
                    val result=AvatarHeadwearLayers.render(original,original,frame,gender,kind)
                    clips+=result.contentHashCode()
                    assertArrayEquals(result,AvatarHeadwearLayers.render(original,original,frame+8,gender,kind))
                    assertArrayEquals(result,AvatarHeadwearLayers.render(original,original,frame-8,gender,kind))
                }
            }
            hashes.forEach { (kind,clips)->assertEquals("Original poses lost $gender/$kind",8,clips.size) }
            val original=source(gender,0)
            val cap=AvatarHeadwearLayers.occlusionMask(original,0,gender,AvatarHeadwearLayers.Kind.CAP)
            val visor=AvatarHeadwearLayers.occlusionMask(original,0,gender,AvatarHeadwearLayers.Kind.VISOR)
            assertTrue("A visor covered the full cap crown",cap.count { it!=0 }>visor.count { it!=0 }+300)
            val bear=AvatarHeadwearLayers.extensionMask(original,0,gender,AvatarHeadwearLayers.Kind.BEAR)
            val golden=AvatarHeadwearLayers.extensionMask(original,0,gender,AvatarHeadwearLayers.Kind.GOLDEN)
            assertFalse("Golden hood only recolored the bear silhouette",bear.contentEquals(golden))
        }
    }

    @Test fun capAndVisorCloseBeforeTheRightCropBoundaryOnEveryApprovedPose() {
        val sourceHash=digest()
        for(gender in AvatarGender.entries)for(frame in 0..7) {
            val original=source(gender,frame);val unchanged=original.clone()
            val eye=AvatarEquipmentLayers.anchors(frame,gender).eye
            for(kind in listOf(AvatarHeadwearLayers.Kind.CAP,AvatarHeadwearLayers.Kind.VISOR)) {
                val result=AvatarHeadwearLayers.render(original,original,frame,gender,kind)
                val cover=AvatarHeadwearLayers.occlusionMask(original,frame,gender,kind)
                val extension=AvatarHeadwearLayers.extensionMask(original,frame,gender,kind)
                for(y in 0 until 384) {
                    val boundary=y*256+247
                    assertEquals("Brim touched the right crop boundary $gender/$frame/$kind/$y",0,cover[boundary])
                    assertEquals("Brim alpha ended at the crop boundary $gender/$frame/$kind/$y",0,extension[boundary])
                    for(x in 247 until 256)assertEquals("Right margin source changed",original[y*256+x],result[y*256+x])
                }
                // Independent original face/ear landmarks remain locked while
                // shortening a brim. Existing source hair can still be worn over.
                for(y in eye.y-10..eye.y+28)for(x in eye.x-12..eye.x+66)
                    assertEquals("Bounded brim changed original face $gender/$frame/$kind",original[y*256+x],result[y*256+x])
                for(y in eye.y-5..eye.y+23)for(x in eye.x-58..eye.x-28)
                    assertEquals("Bounded brim changed original ear",original[y*256+x],result[y*256+x])
                assertArrayEquals("Bounded brim mutated the immutable input",unchanged,original)
            }
        }
        assertEquals(sourceHash,digest())
    }

    @Test fun wrongDimensionsFailBeforeAnySourceCanBeModified() {
        for(size in listOf(0,255*384,256*383)) {
            try {
                AvatarHeadwearLayers.render(IntArray(size),IntArray(size),0,AvatarGender.MALE,AvatarHeadwearLayers.Kind.CAP)
                fail("Wrong dimensions accepted")
            } catch(_:IllegalArgumentException) { }
        }
    }

    private fun digest()=MessageDigest.getInstance("SHA-256").digest(sourceFile.readBytes()).joinToString("") { "%02x".format(it) }
}
