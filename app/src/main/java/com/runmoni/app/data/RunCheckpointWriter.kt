package com.runmoni.app.data

/** Retains accepted points until their parent checkpoint and all points commit together. */
internal class RunCheckpointWriter(
    private val persist: suspend (StoredRun, List<StoredPoint>) -> Unit,
) {
    private val pending = linkedMapOf<Int, StoredPoint>()
    val unsavedPointCount: Int get() = pending.size

    fun enqueue(point: StoredPoint) {
        check(pending[point.sequence]?.let { it == point } != false) { "A pending point cannot change." }
        pending[point.sequence] = point
    }

    suspend fun save(run: StoredRun) {
        val batch = pending.values.toList()
        check(batch.all { it.runId == run.id }) { "A checkpoint must contain only its own points." }
        persist(run, batch)
        // Nothing is removed on a failed or cancelled write. A repeated write is an upsert.
        batch.forEach { pending.remove(it.sequence) }
    }
}
