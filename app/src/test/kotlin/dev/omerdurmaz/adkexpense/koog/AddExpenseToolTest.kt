package dev.omerdurmaz.adkexpense.koog

import dev.omerdurmaz.adkexpense.agent.FakeExpenseRepository
import dev.omerdurmaz.adkexpense.data.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AddExpenseToolTest {
    private val repo = FakeExpenseRepository()
    private val args = AddExpenseTool.Args(18.405, Category.TRANSPORT, "2026-10-19", "Taxi")

    @Test
    fun `the hand-written gate keeps rejected writes out`() = runTest {
        val result = AddExpenseTool(repo) { false }.execute(args)

        assertEquals("The user rejected this expense.", result)
        assertTrue(repo.items.isEmpty())
    }

    @Test
    fun `a confirmed write is saved in cents`() = runTest {
        val result = AddExpenseTool(repo) { true }.execute(args)

        assertEquals("saved #1", result)
        assertEquals(1841L, repo.items.single().amountCents) // HALF_UP
    }
}
