package dev.omerdurmaz.adkexpense.agent

import com.google.adk.kt.runners.InMemoryRunner
import dev.omerdurmaz.adkexpense.data.Category
import dev.omerdurmaz.adkexpense.data.Expense
import dev.omerdurmaz.adkexpense.ui.FinanceChatViewModel
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The model the app runs without Firebase: proves a fresh clone gets the whole flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoModelTest {
    private val repo = FakeExpenseRepository()
    private val today = LocalDate.of(2026, 10, 20)
    private val vm by lazy {
        val agent = financeAgent(DemoModel { today }, FinanceTools(repo) { today })
        FinanceChatViewModel(InMemoryRunner(agent, appName = "finance"), "u1", "s1")
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a write goes through the confirmation card`() = runTest {
        vm.send("Add yesterday's taxi 18.40")
        advanceUntilIdle()

        val draft = assertNotNull(vm.state.value.pending).drafts.single()
        assertEquals("TRANSPORT", draft["category"])
        assertEquals("2026-10-19", draft["date"])
        assertTrue(repo.items.isEmpty())

        vm.answer(approved = true)
        advanceUntilIdle()

        assertEquals(1840L, repo.items.single().amountCents)
        assertEquals(
            "Saved: 2026-10-19 | TRANSPORT | 18.40 | Taxi.",
            vm.state.value.lines.last().text,
        )
    }

    @Test
    fun `a question is answered from search_expenses`() = runTest {
        repo.items += Expense(1, 4250, Category.GROCERIES, today.minusDays(1), "Migros")
        repo.items += Expense(2, 1290, Category.EATING_OUT, today.minusDays(2), "Lunch")

        vm.send("How much did I spend on groceries?")
        advanceUntilIdle()

        assertEquals(
            "From 2026-09-21 to 2026-10-20 you spent 42.50 on GROCERIES (1 expense). " +
                "Latest: 2026-10-19 | GROCERIES | 42.50 | Migros.",
            vm.state.value.lines.last().text,
        )
    }
}
