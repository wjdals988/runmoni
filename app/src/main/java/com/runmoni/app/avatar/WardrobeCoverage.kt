package com.runmoni.app.avatar

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.security.MessageDigest

/** Checked, immutable clothing coverage for every original cell; loaded on MiniMi's worker. */
internal object WardrobeCoverage {
    const val VERSION = "approved-wardrobe-v1"
    private var frames: Array<IntArray>? = null
    @Synchronized fun frame(context: Context, row: Int, frame: Int): IntArray {
        frames?.let { return it[row*8+frame] }
        val assets=context.applicationContext.assets
        val directory="avatars/wardrobe-v1"
        val manifest=JSONObject(assets.open("$directory/manifest.json").use { it.readBytes().toString(Charsets.UTF_8) })
        require(manifest.getInt("schemaVersion")==1 && manifest.getString("version")==VERSION &&
            manifest.getString("sourceSha256")==ApprovedAvatarRenderer.SHA256 &&
            manifest.getInt("frames")==8 && manifest.getInt("rows")==2 && manifest.getString("file")=="coverage.png")
        require(manifest.getJSONArray("canvas").getInt(0)==2048 && manifest.getJSONArray("canvas").getInt(1)==768 &&
            manifest.getJSONArray("cell").getInt(0)==256 && manifest.getJSONArray("cell").getInt(1)==384)
        val bytes=assets.open("$directory/coverage.png").use { it.readBytes() }
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(sha==manifest.getString("sha256")) { "Wardrobe coverage integrity check failed" }
        val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inScaled=false }))
        try {
            require(bitmap.width==2048 && bitmap.height==768)
            val result=Array(16) { cell -> IntArray(256*384).also { bitmap.getPixels(it,0,256,(cell%8)*256,(cell/8)*384,256,384) } }
            require(manifest.getString("handProtectionFile")=="hand-protection.png")
            val protectedBytes=assets.open("$directory/hand-protection.png").use { it.readBytes() }
            val protectedSha=MessageDigest.getInstance("SHA-256").digest(protectedBytes).joinToString("") { "%02x".format(it) }
            require(protectedSha==manifest.getString("handProtectionSha256"))
            val hands=requireNotNull(BitmapFactory.decodeByteArray(protectedBytes,0,protectedBytes.size,BitmapFactory.Options().apply { inScaled=false }))
            val protected=try {
                require(hands.width==2048 && hands.height==768)
                Array(16) { cell -> IntArray(256*384).also { hands.getPixels(it,0,256,(cell%8)*256,(cell/8)*384,256,384) } }
            } finally { hands.recycle() }
            result.forEachIndexed { cell, labels ->
                var sleeves=0
                labels.forEachIndexed { index,label ->
                    require(protected[cell][index]==0 || protected[cell][index]==-1)
                    if(label!=0) {
                    require(protected[cell][index]==0) { "Sleeve coverage overlaps a protected hand" }
                    require((label ushr 24)==255 && ((label ushr 16) and 255) in 1..7 && (label and 255)<=3)
                    // Face/neck/hair and previous-row padding are never sleeve/tights coverage.
                    require(index/256 >= if(cell/8==0) 182 else 176)
                    if(((label ushr 16) and 3)!=0) sleeves++
                } }
                require(sleeves>100) { "Incomplete wardrobe cell" }
            }
            frames=result
            return result[row*8+frame]
        } finally { bitmap.recycle() }
    }
}
