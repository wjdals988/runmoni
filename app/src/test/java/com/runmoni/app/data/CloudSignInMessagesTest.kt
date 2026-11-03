package com.runmoni.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSignInMessagesTest {
    @Test fun untrustedProviderPayloadIsNeverReflectedInSupportMessage() {
        val payload = "person@example.invalid idToken=private-credential"
        val message = cloudSignInFailureMessage(payload)
        assertFalse(message.contains("person@"))
        assertFalse(message.contains("private-credential"))
        assertTrue(message.endsWith("(G09)"))
    }

    @Test fun accountPickerAndAuthenticationNetworkFailuresHaveDistinctAdvice() {
        assertTrue(cloudSignInFailureMessage("G01").contains("기기의 계정"))
        assertTrue(cloudSignInFailureMessage("F01").contains("인증 서버"))
        assertFalse(cloudSignInFailureMessage("G01") == cloudSignInFailureMessage("F01"))
    }

    @Test fun overlappingPickerAdviceDoesNotClaimAuthenticationFailure() {
        val message = cloudSignInFailureMessage("G07")
        assertTrue(message.contains("다른 Google 계정 선택"))
        assertTrue(message.contains("먼저 연 선택"))
        assertFalse(message.contains("인증 서버"))
    }
}
