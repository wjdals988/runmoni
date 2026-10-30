package com.runmoni.app.data

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

data class InstalledAppIdentity(val version: String, val code: Long, val certificateSha256: String) {
    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): InstalledAppIdentity {
            val info = context.packageManager.getPackageInfo(context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES)
            val signer = info.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()
            val sha256 = signer?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }.orEmpty()
            return InstalledAppIdentity(info.versionName.orEmpty(), info.longVersionCode, sha256)
        }
    }
}
