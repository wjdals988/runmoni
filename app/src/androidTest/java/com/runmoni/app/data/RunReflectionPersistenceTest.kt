package com.runmoni.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.RunEffort
import com.runmoni.app.domain.RunReflection
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunReflectionPersistenceTest {
    @Test fun updateAndClearOnlyTheExactCompletedRunAcrossDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "run-reflection-${UUID.randomUUID()}.db"
        suspend fun withDatabase(block: suspend (RunDatabase) -> Unit) {
            val database = Room.databaseBuilder(context, RunDatabase::class.java, name).build()
            try { block(database) } finally { database.close() }
        }
        val completed = StoredRun("completed", 1_000, finishedAtMillis = 66_000, distanceMeters = 200.0,
            activeDurationMillis = 60_000, intervalRunSeconds = 10, intervalRecoverySeconds = 20, intervalRepeats = 3)
        val other = completed.copy(id = "other", startedAtMillis = 2_000, privateNote = "다른 기록")
        val unfinished = StoredRun("unfinished", 70_000)
        val initial = RunReflection(RunEffort.EASY, "  바람이 강함\n돌아오는 길은 편함 ")
        val revised = RunReflection(RunEffort.MODERATE, "다음에도 같은 코스")

        try {
            withDatabase { database ->
                val dao = database.runs()
                listOf(completed, other, unfinished).forEach { dao.save(it) }
                val repository = RunRepository(database, credit = { _, _ -> })
                try {
                    assertFalse(repository.updateReflection(unfinished.id, initial))
                    assertFalse(repository.updateReflection("completed ", initial))
                    assertTrue(repository.updateReflection(completed.id, initial))
                    assertEquals(RunReflection(RunEffort.EASY, "바람이 강함 돌아오는 길은 편함"),
                        repository.record(completed.id)?.reflection)
                    assertTrue(repository.updateReflection(completed.id, revised))
                    withTimeout(5_000) { repository.records.first { it.any { record -> record.id == completed.id && record.reflection == revised } } }
                } finally { repository.close() }
            }
            withDatabase { database ->
                val repository = RunRepository(database, credit = { _, _ -> })
                try {
                    assertEquals(revised, repository.record(completed.id)?.reflection)
                    assertTrue(repository.updateReflection(completed.id, null))
                    assertNull(repository.record(completed.id)?.reflection)
                    assertEquals(completed, database.runs().get(completed.id))
                    assertEquals(other, database.runs().get(other.id))
                    assertEquals(unfinished, database.runs().get(unfinished.id))
                } finally { repository.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
