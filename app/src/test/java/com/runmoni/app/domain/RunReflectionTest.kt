package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class RunReflectionTest {
    @Test fun effortAndNoteAreBothOptionalAndTheNoteHasOneLine() {
        assertNull(RunReflection.fromInput(null, " \n \t"))
        assertEquals(RunReflection(RunEffort.EASY, null), RunReflection.fromInput(RunEffort.EASY, " "))
        assertEquals(RunReflection(null, "바람이 강함 회복은 편안"),
            RunReflection.fromInput(null, " 바람이 강함\r\n회복은 편안 "))
        assertEquals(RunReflection(null, "첫 문장 둘째 문장"),
            RunReflection.fromStorage("unknown", "첫 문장\u2028둘째 문장"))
    }

    @Test fun longOrInvalidStoredNotesDoNotHideAKnownEffort() {
        val atLimit = "🏃".repeat(RunReflection.MaxNoteLength)
        assertTrue(RunReflection.noteFits(atLimit))
        assertFalse(RunReflection.noteFits(atLimit + "가"))
        assertEquals(RunReflection(RunEffort.HARD, null), RunReflection.fromStorage("hard", atLimit + "가"))
        assertNull(RunReflection.fromStorage("unknown", null))
    }

    @Test fun privateReflectionDoesNotChangeCloudSummaryOrShareCardContent() {
        val original = RunRecord("reflection-private", startedAtMillis = 1_000, finishedAtMillis = 61_000,
            distanceMeters = 200.0, activeDurationMillis = 60_000)
        val updated = original.copy(reflection = RunReflection(RunEffort.HARD, "개인 메모"))
        assertEquals(CloudRunSummary.fromRunRecord(original).toFields(), CloudRunSummary.fromRunRecord(updated).toFields())
        assertNotNull(RunShareContent.from(original))
        assertEquals(RunShareContent.from(original), RunShareContent.from(updated))
    }
}
