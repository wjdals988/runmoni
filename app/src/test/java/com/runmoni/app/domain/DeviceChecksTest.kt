package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class DeviceChecksTest {
    @Test fun automaticChecksCannotMarkAnyPhoneFlowPassed() {
        val report = deviceCheckReport("0.2.1-dev", 11, "a".repeat(64), DeviceChecks(room = RoomCheck.PUBLIC_READY, roomCheckedAt = 123), true, true, 124, 125, true, true)
        assertTrue(report.contains("room=PUBLIC_READY"))
        PhoneCheck.entries.forEach { assertTrue(report.contains("selfReported.${it.name}=false")) }
        assertTrue(report.contains("do not verify physical-device flows"))
    }

    @Test fun onlyChosenSelfReportsAppearAndCertificateCannotInjectText() {
        val report = deviceCheckReport("0.2.1-dev\ncode=99", 11, "not-a-cert\nemail=private@example.com", DeviceChecks(selfReported = setOf(PhoneCheck.PHYSICAL_PHONE)), false, false, 0, 0, false, false)
        assertTrue(report.contains("certificateSha256=unavailable"))
        assertTrue(report.contains("selfReported.PHYSICAL_PHONE=true"))
        assertTrue(report.contains("selfReported.TWO_DEVICE_ROOMS=false"))
        assertFalse(report.contains("email="))
        assertFalse(report.contains("\ncode=99"))
    }
}
