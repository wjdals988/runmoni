package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NicknameTest {
    @Test fun decomposedHangulAndWhitespaceHaveOneServerKey() {
        val composed = NicknamePolicy.requireValid("  러너   A  ")
        val pasted = NicknamePolicy.requireValid("\u1105\u1165\u1102\u1165\tA")
        assertEquals("러너 A", composed.nickname)
        assertEquals("러너 a", composed.key)
        assertEquals(composed, pasted)
    }

    @Test fun englishCaseFoldingDoesNotDependOnDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("istanbul", NicknamePolicy.requireValid("ISTANBUL").key)
        } finally { Locale.setDefault(original) }
    }

    @Test fun newNamesAreBoundedAndRequireLetterOrNumber() {
        assertNotNull(NicknamePolicy.validationError("가"))
        assertNull(NicknamePolicy.validationError("가".repeat(16)))
        assertNotNull(NicknamePolicy.validationError("가".repeat(17)))
        assertNotNull(NicknamePolicy.validationError("--"))
        assertNotNull(NicknamePolicy.validationError("__"))
        assertNull(NicknamePolicy.validationError("runner-2_가"))
    }

    @Test fun separatorsAndSpoofingCharactersCannotBecomeDocumentPaths() {
        for (name in listOf("a/b", "a\\b", "러너😀", "runn\u200ber", "runn\u0435r", "a.b")) {
            assertNotNull(name, NicknamePolicy.validationError(name))
        }
    }

    @Test fun availabilityBelongsToBothExactDisplayCandidateAndIdentity() {
        val check = NicknameCheck("Runner", "runner", "original-uid", NicknameAvailability.AVAILABLE)
        assertTrue(check.matches(" Runner ", "original-uid"))
        assertFalse(check.matches("Runner", "new-uid"))
        assertFalse(check.matches("Runner2", "original-uid"))
        assertFalse(check.matches("RUNNER", "original-uid"))
    }

}
