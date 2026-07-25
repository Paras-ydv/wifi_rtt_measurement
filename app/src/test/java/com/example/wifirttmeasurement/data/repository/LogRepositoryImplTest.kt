package com.example.wifirttmeasurement.data.repository

import app.cash.turbine.test
import com.example.wifirttmeasurement.domain.model.LogSeverity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogRepositoryImplTest {

    private lateinit var repository: LogRepositoryImpl

    @Before
    fun setUp() {
        repository = LogRepositoryImpl()
    }

    @Test
    fun `initial logs are empty`() = runTest {
        assertTrue(repository.logs.value.isEmpty())
    }

    @Test
    fun `addLog adds entry to the top of the list`() = runTest {
        repository.addLog("First")
        repository.addLog("Second")
        val logs = repository.logs.value
        assertEquals("Second", logs[0].message)
        assertEquals("First", logs[1].message)
    }

    @Test
    fun `addLog sets correct severity`() = runTest {
        repository.addLog("warn", LogSeverity.Warning)
        assertEquals(LogSeverity.Warning, repository.logs.value[0].severity)
    }

    @Test
    fun `addLog default severity is Info`() = runTest {
        repository.addLog("info message")
        assertEquals(LogSeverity.Info, repository.logs.value[0].severity)
    }

    @Test
    fun `logs are capped at 300 entries`() = runTest {
        repeat(350) { repository.addLog("log $it") }
        assertEquals(300, repository.logs.value.size)
    }

    @Test
    fun `logs flow emits on each new entry`() = runTest {
        repository.logs.test {
            assertEquals(emptyList<Any>(), awaitItem()) // initial empty
            repository.addLog("hello")
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("hello", updated[0].message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each log entry has a unique id`() = runTest {
        repeat(5) { repository.addLog("msg $it") }
        val ids = repository.logs.value.map { it.id }.toSet()
        assertEquals(5, ids.size)
    }

    @Test
    fun `each log entry has a non-zero timestamp`() = runTest {
        repository.addLog("test")
        assertTrue(repository.logs.value[0].timestampMillis > 0)
    }
}
