package com.runmoni.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.runmoni.app.domain.AvatarCatalogue
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.variantFor
import java.security.MessageDigest

/** Artwork readiness is separate from inventory validity/ownership and never migrates a profile. */
object AvatarArtwork {
    fun itemReady(slot: String, variant: Int) = AvatarCatalogue.valid(slot, variant) && variant == 0
    fun styleReady(style: AvatarStyle) = AvatarCatalogue.slots.all { itemReady(it.key, style.variantFor(it.key)) }
}

/** Approved whole-frame artwork. No v0.4 rig, body rotation or old-art fallback. */
class ApprovedAvatarRenderer(context: Context) {
    companion object {
        const val WIDTH = 256
        const val HEIGHT = 384
        const val FRAMES = 8
        const val MILLIS = 90
        const val ASSET = "avatars/v0.3.0/running-motion.png"
        const val SHA256 = "f9035f29ced0c7ba513dfe24cb727410a7204dc482fc457e806c7a9b3cdbfa65"
        fun frameAt(elapsedMillis: Long) = ((elapsedMillis.coerceAtLeast(0) / MILLIS) % FRAMES).toInt()
    }

    private val assets = context.applicationContext.assets
    private val source by lazy {
        val bytes = assets.open(ASSET).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == SHA256) { "Approved avatar source integrity check failed" }
        val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).also {
            require(it.width == WIDTH * FRAMES && it.height == HEIGHT * 2) { "Invalid approved avatar sheet" }
        }
    }
    private val frames = arrayOfNulls<Bitmap>(FRAMES)

    /** Base outfit only until per-item frame layers exist. Readiness is disclosed in the closet. */
    @Synchronized
    fun render(frame: Int, running: Boolean): Bitmap {
        val selected = if (running) Math.floorMod(frame, FRAMES) else 0
        return frames[selected] ?: Bitmap.createBitmap(source, selected * WIDTH, 0, WIDTH, HEIGHT).also {
            frames[selected] = it
        }
    }
}
