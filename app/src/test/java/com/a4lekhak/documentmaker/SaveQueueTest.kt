package com.a4lekhak.documentmaker

import com.a4lekhak.documentmaker.data.SaveQueue
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SaveQueueTest {
    @Test
    fun coalescesPendingSnapshotsButPreservesOperationOrder() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val log = mutableListOf<String>()
        val queue =
            SaveQueue<String>(
                write = { log.add(it) },
                onSaved = {},
                onFailure = { throw AssertionError(it) },
            )
        queue.enqueue {
            entered.complete(Unit)
            gate.await()
        }
        entered.await()
        repeat(1000) { queue.save("a", "a$it") }
        queue.enqueue { log.add("rename") }
        queue.save("a", "after-rename")
        queue.save("b", "other-document")
        gate.complete(Unit)
        queue.flush()
        queue.close()
        queue.awaitClosed()
        assertEquals(listOf("a999", "rename", "after-rename", "other-document"), log)
    }

    @Test
    fun flushPropagatesFailedSaveAndRetryClearsIt() = runBlocking {
        var fail = true
        val failures = mutableListOf<Throwable>()
        val queue =
            SaveQueue<String>(
                write = { if (fail) error("disk full") },
                onSaved = {},
                onFailure = { failures.add(it) },
            )
        queue.save("a", "text")
        assertEquals("disk full", runCatching { queue.flush() }.exceptionOrNull()?.message)
        fail = false
        queue.save("a", "text")
        queue.flush()
        queue.close()
        queue.awaitClosed()
        assertEquals(1, failures.size)
    }

    @Test
    fun closeDrainsAlreadyAcceptedWrites() = runBlocking {
        val saved = CompletableDeferred<String>()
        val queue =
            SaveQueue<String>(
                write = {
                    delay(10)
                    saved.complete(it)
                },
                onSaved = {},
                onFailure = { throw AssertionError(it) },
            )
        queue.save("a", "last edit")
        queue.close()
        queue.awaitClosed()
        assertEquals("last edit", saved.await())
    }
}
