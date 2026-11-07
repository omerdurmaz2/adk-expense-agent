package dev.omerdurmaz.adkexpense.agent

import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import dev.omerdurmaz.adkexpense.data.Category
import dev.omerdurmaz.adkexpense.data.Expense
import dev.omerdurmaz.adkexpense.ui.ChatLine
import dev.omerdurmaz.adkexpense.ui.FinanceChatViewModel
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceAgentTest {
    private val repo = FakeExpenseRepository() // in-memory list
    private val today = LocalDate.of(2026, 10, 20)
    private val taxi = mapOf(
        "amount" to 18.4,
        "category" to "TRANSPORT",
        "date" to "2026-10-19",
        "note" to "Taxi",
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun runnerFor(model: Model, tools: FinanceTools = FinanceTools(repo) { today }) =
        InMemoryRunner(financeAgent(model, tools), appName = "finance")

    private fun chat(runner: InMemoryRunner) =
        FinanceChatViewModel(runner, userId = "u1", sessionId = "s1")

    @Test
    fun `add_expense waits for the user`() = runTest {
        val model = ScriptedModel(
            callTool(
                "add_expense",
                mapOf(
                    "amount" to 42.5,
                    "category" to "GROCERIES",
                    "date" to "2026-10-19",
                    "note" to "Migros",
                ),
            ),
            say("Saved 42.50 for groceries."),
        )
        val agent = financeAgent(model, FinanceTools(repo) { today })
        val runner = InMemoryRunner(agent, appName = "finance")
        val vm = FinanceChatViewModel(runner, userId = "u1", sessionId = "s1")

        vm.send("I spent 42.50 at Migros yesterday")
        val pending = assertNotNull(vm.state.value.pending)
        assertEquals("GROCERIES", pending.drafts.single()["category"])
        assertTrue(repo.items.isEmpty()) // nothing written yet

        vm.answer(approved = true)
        assertEquals(4250L, repo.items.single().amountCents)
    }

    @Test
    fun `a rejected write never reaches the repository`() = runTest {
        val model = ScriptedModel(callTool("add_expense", taxi), say("OK, I didn't save it."))
        val vm = chat(runnerFor(model))

        vm.send("Add yesterday's taxi 18.40")
        assertNotNull(vm.state.value.pending)
        vm.answer(approved = false)

        assertTrue(repo.items.isEmpty())
        assertNull(vm.state.value.pending)
        assertTrue("error" in assertNotNull(model.requests.last().lastToolResult()))
        assertEquals(ChatLine(false, "OK, I didn't save it."), vm.state.value.lines.last())
    }

    @Test
    fun `a model stuck on an unknown tool is told no and stopped by maxSteps`() = runTest {
        val model = ScriptedModel(callTool("delete_all_expenses", emptyMap()))
        val vm = chat(runnerFor(model))

        vm.send("Delete everything")

        assertEquals(6, model.requests.size) // maxSteps = 6
        model.requests.drop(1).forEach {
            assertEquals("tool not available", it.lastToolResult()?.get("error"))
        }
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.failed) // maxSteps ends the turn quietly, no exception
        assertTrue(repo.items.isEmpty())
    }

    @Test
    fun `a throwing repository reaches the model as a generic error`() = runTest {
        val broken = FakeExpenseRepository(IllegalStateException("SQLITE_FULL /data/data/x.db"))
        val model = ScriptedModel(
            callTool("search_expenses", mapOf("from" to "2026-10-01", "to" to "2026-10-20")),
            say("Sorry, I couldn't read your expenses."),
        )
        val vm = chat(runnerFor(model, FinanceTools(broken) { today }))

        vm.send("How much did I spend this month?")

        val seen = model.requests[1]
        assertEquals(
            mapOf("error" to "search_expenses failed; check the arguments"),
            seen.lastToolResult(),
        )
        assertFalse("SQLITE_FULL" in seen.contents.toString()) // no exception text in the prompt
        assertFalse(vm.state.value.failed)
    }

    @Test
    fun `an invalid category shows the card first and fails after the tap`() = runTest {
        val model = ScriptedModel(
            callTool("add_expense", taxi + ("category" to "FOOD")),
            say("FOOD isn't a category I know."),
        )
        val vm = chat(runnerFor(model))

        vm.send("Add 18.40 for food")
        assertNotNull(vm.state.value.pending) // the gate runs before argument parsing
        vm.answer(approved = true)

        assertTrue(repo.items.isEmpty())
        assertTrue("error" in assertNotNull(model.requests.last().lastToolResult()))
    }

    @Test
    fun `a new ViewModel restores the transcript from the session`() = runTest {
        val runner = runnerFor(ScriptedModel(say("You spent 61.40 this month.")))
        chat(runner).send("How much this month?")

        val restored = chat(runner)

        assertEquals(
            listOf(
                ChatLine(true, "How much this month?"),
                ChatLine(false, "You spent 61.40 this month."),
            ),
            restored.state.value.lines,
        )
    }

    @Test
    fun `search_expenses adds up the total in Kotlin`() = runTest {
        repo.items += Expense(1, 4250, Category.GROCERIES, today.minusDays(1), "Migros")
        repo.items += Expense(2, 1890, Category.GROCERIES, today.minusDays(3), "Market")
        repo.items += Expense(3, 85000, Category.RENT, today.minusDays(40), "Rent")

        val result = FinanceTools(repo) { today }
            .searchExpenses("2026-10-01", "2026-10-20", category = null)

        assertEquals("61.40", result["total"])
        assertEquals(2, result["count"])
        assertEquals("2026-10-19 | GROCERIES | 42.50 | Migros", (result["expenses"] as List<*>)[0])
    }

    @Test
    fun `add_expense validates before writing`() = runTest {
        val tools = FinanceTools(repo) { today }

        val future = tools.addExpense(18.4, Category.TRANSPORT, "2026-10-21", "Taxi")
        val tooBig = tools.addExpense(18_400.0, Category.TRANSPORT, "2026-10-19", "Taxi")
        val saved = tools.addExpense(18.405, Category.TRANSPORT, "2026-10-19", "Taxi\u0007")

        assertTrue("error" in future)
        assertTrue("error" in tooBig)
        assertEquals("2026-10-19 | TRANSPORT | 18.41 | Taxi", saved["saved"]) // HALF_UP, no BEL
        assertEquals(1841L, repo.items.single().amountCents)
    }
}
