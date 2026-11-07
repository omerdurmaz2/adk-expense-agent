package dev.omerdurmaz.adkexpense.agent

import dev.omerdurmaz.adkexpense.data.Category
import dev.omerdurmaz.adkexpense.data.Expense
import dev.omerdurmaz.adkexpense.data.ExpenseRepository
import java.time.LocalDate

/** A plain list behind the repository interface. Pass [failWith] to make every call throw. */
class FakeExpenseRepository(private val failWith: Exception? = null) : ExpenseRepository {
    val items = mutableListOf<Expense>()

    override suspend fun find(from: LocalDate, to: LocalDate, category: Category?): List<Expense> {
        failWith?.let { throw it }
        return items.filter {
            !it.date.isBefore(from) && !it.date.isAfter(to) &&
                (category == null || it.category == category)
        }
    }

    override suspend fun add(
        amountCents: Long,
        category: Category,
        date: LocalDate,
        note: String,
    ): Expense {
        failWith?.let { throw it }
        return Expense(items.size + 1L, amountCents, category, date, note).also { items += it }
    }
}
