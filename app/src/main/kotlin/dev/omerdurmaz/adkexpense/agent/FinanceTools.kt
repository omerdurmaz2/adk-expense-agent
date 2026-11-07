package dev.omerdurmaz.adkexpense.agent

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dev.omerdurmaz.adkexpense.data.Category
import dev.omerdurmaz.adkexpense.data.Expense
import dev.omerdurmaz.adkexpense.data.ExpenseRepository
import java.math.RoundingMode
import java.time.LocalDate

class FinanceTools(
    private val repo: ExpenseRepository,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** Lists expenses in a date range (newest first, at most 50) with their total. */
    @Tool(name = "search_expenses")
    suspend fun searchExpenses(
        @Param("First day, YYYY-MM-DD") from: String,
        @Param("Last day, YYYY-MM-DD") to: String,
        @Param("Only this category; omit for all") category: Category?,
    ): Map<String, Any> {
        val start = day(from) ?: return error("from must be YYYY-MM-DD")
        val end = day(to) ?: return error("to must be YYYY-MM-DD")
        if (end < start || end > start.plusDays(366)) {
            return error("range must be 1 to 366 days")
        }
        val found = repo.find(start, end, category).sortedByDescending { it.date }
        return mapOf(
            "expenses" to found.take(50).map { it.line() },
            "count" to found.size,
            "total" to money(found.sumOf { it.amountCents }),
        )
    }

    /** Saves one expense. The app asks the user to confirm before this runs. */
    @Tool(name = "add_expense", requireConfirmation = true)
    suspend fun addExpense(
        @Param("Amount, e.g. 12.50") amount: Double,
        @Param("Expense category") category: Category,
        @Param("Day of the expense, YYYY-MM-DD") date: String,
        @Param("Short note, up to 80 characters") note: String,
    ): Map<String, Any> {
        if (amount !in 0.01..10_000.0) return error("amount must be 0.01 to 10000")
        val day = day(date)?.takeIf { it <= today() }
            ?: return error("date must be YYYY-MM-DD and not in the future")
        val cents = amount.toBigDecimal()
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        val cleanNote = note.filterNot(Char::isISOControl).take(80)
        return mapOf("saved" to repo.add(cents, category, day, cleanNote).line())
    }

    private fun day(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
    private fun error(message: String) = mapOf("error" to message)
    private fun money(cents: Long) = cents.toBigDecimal().movePointLeft(2).toPlainString()
    private fun Expense.line() = "$date | $category | ${money(amountCents)} | $note"
}
