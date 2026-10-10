package com.a4lekhak.documentmaker.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Coalesces consecutive pending snapshots only; barriers and other operations keep FIFO order. */
class SaveQueue<T>(
    private val write: suspend (T) -> Unit,
    private val onSaved: (T) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private class Ticket<T>(val id: String, var value: T)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val lock = Any()
    private var tail: Ticket<T>? = null
    private val failures = mutableMapOf<String, Throwable>()
    private val worker =
        scope.launch {
            try {
                for (operation in operations) try {
                    operation()
                } catch (e: Exception) {
                    onFailure(e)
                }
            } finally {
                scope.cancel()
            }
        }

    fun save(id: String, value: T) {
        synchronized(lock) {
            val pending = tail
            if (pending?.id == id) {
                pending.value = value
                return
            }
            val ticket = Ticket(id, value)
            tail = ticket
            check(
                operations
                    .trySend {
                        val latest =
                            synchronized(lock) {
                                if (tail === ticket) tail = null
                                ticket.value
                            }
                        try {
                            write(latest)
                            failures.remove(id)
                            onSaved(latest)
                        } catch (e: Exception) {
                            failures[id] = e
                            throw e
                        }
                    }
                    .isSuccess
            ) {
                "Storage is closing"
            }
        }
    }

    fun enqueue(action: suspend () -> Unit) {
        synchronized(lock) {
            tail = null
            check(operations.trySend(action).isSuccess) { "Storage is closing" }
        }
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        enqueue {
            val failure = failures.values.firstOrNull()
            if (failure == null) done.complete(Unit) else done.completeExceptionally(failure)
        }
        done.await()
    }

    fun close() {
        synchronized(lock) {
            tail = null
            operations.close()
        }
    }

    suspend fun awaitClosed() {
        worker.join()
    }
}
