package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private val release = AppRelease("0.1.3-dev", 4, "runmoni-0.1.3-dev.apk", 200_000_000,
        "a".repeat(64), AppRelease.RELEASE_CERTIFICATE, true, "com.runmoni.app",
        "${AppRelease.REPOSITORY}/releases/download/v0.1.3-dev/runmoni-0.1.3-dev.apk",
        "${AppRelease.REPOSITORY}/releases/tag/v0.1.3-dev")

    @Test fun comparesNumericVersionCodeRatherThanVersionText() {
        assertTrue(release.isNewerThan(2)); assertFalse(release.isNewerThan(4)); assertFalse(release.isNewerThan(5))
    }
    @Test fun rejectsOtherHostsRepositoriesAndQueryPayloads() {
        assertFalse(release.copy(downloadUrl = release.downloadUrl.replace("github.com", "github.com.example.org")).isTrusted())
        assertFalse(release.copy(downloadUrl = release.downloadUrl.replace("wjdals988/runmoni", "other/runmoni")).isTrusted())
        assertFalse(release.copy(downloadUrl = release.downloadUrl + "?redirect=other").isTrusted())
    }
    @Test fun rejectsWrongPackageSigningIdentityOrUnsignedMetadata() {
        assertFalse(release.copy(packageName = "other.app").isTrusted())
        assertFalse(release.copy(certificateSha256 = "b".repeat(64)).isTrusted())
        assertFalse(release.copy(signed = false).isTrusted())
    }
    @Test fun rejectsMalformedVersionsNamesAndUnboundedSizes() {
        assertFalse(release.copy(version = "../x").isTrusted())
        assertFalse(release.copy(fileName = "other.apk").isTrusted())
        assertFalse(release.copy(sizeBytes = 0).isTrusted())
        assertFalse(release.copy(sizeBytes = AppRelease.MAX_APK_BYTES + 1).isTrusted())
        assertFalse(release.copy(versionCode = 0).isTrusted())
    }
    @Test fun rejectsMalformedDigestAndReleaseLink() {
        assertFalse(release.copy(sha256 = "a".repeat(63)).isTrusted())
        assertFalse(release.copy(releaseUrl = "https://example.org").isTrusted())
    }
    private fun version(name: String, code: Long) = release.copy(version = name, versionCode = code,
        fileName = "runmoni-$name.apk", downloadUrl = "${AppRelease.REPOSITORY}/releases/download/v$name/runmoni-$name.apk",
        releaseUrl = "${AppRelease.REPOSITORY}/releases/tag/v$name")

    @Test fun selectsHighestNumericCodeAndRespectsStableChannel() {
        val stable = version("0.1.9", 9)
        val preview = version("0.1.10-dev", 10)
        assertEquals(preview, latestPublishedRelease(stable, preview, true))
        assertEquals(stable, latestPublishedRelease(stable, preview, false))
        assertEquals(stable, latestPublishedRelease(stable, version("0.1.8-dev", 8), true))
        assertNull(latestPublishedRelease(null, preview, false))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUntrustedPublishedFeedInsteadOfSayingCurrent() {
        latestPublishedRelease(null, release.copy(downloadUrl = "https://other.example/app.apk"), true)
    }
}
