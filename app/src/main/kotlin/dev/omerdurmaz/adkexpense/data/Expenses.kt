package dev.omerdurmaz.adkexpense.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

enum class Category { GROCERIES, TRANSPORT, RENT, EATING_OUT, OTHER }

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val category: Category,
    val date: LocalDate,
    val note: String,
)

/** The only door the agent's tools have into your data: typed values in, typed values out. */
interface ExpenseRepository {
    suspend fun find(from: LocalDate, to: LocalDate, category: Category?): List<Expense>
    suspend fun add(amountCents: Long, category: Category, date: LocalDate, note: String): Expense
}

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {
    override suspend fun find(from: LocalDate, to: LocalDate, category: Category?) =
        dao.find(from, to, category)

    override suspend fun add(
        amountCents: Long,
        category: Category,
        date: LocalDate,
        note: String,
    ): Expense {
        val expense = Expense(id = 0, amountCents, category, date, note)
        return expense.copy(id = dao.insert(expense))
    }
}

@Dao
interface ExpenseDao {
    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :from AND :to " +
            "AND (:category IS NULL OR category = :category) ORDER BY date DESC, id DESC",
    )
    suspend fun find(from: LocalDate, to: LocalDate, category: Category?): List<Expense>

    @Insert
    suspend fun insert(expense: Expense): Long
}

/** ISO dates sort as text, so BETWEEN works on the stored strings. */
object DateConverter {
    @TypeConverter fun toText(date: LocalDate): String = date.toString()
    @TypeConverter fun toDate(text: String): LocalDate = LocalDate.parse(text)
}

@Database(entities = [Expense::class], version = 1, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao
}

fun openExpenseDatabase(context: Context, today: LocalDate = LocalDate.now()): ExpenseDatabase =
    Room.databaseBuilder(context, ExpenseDatabase::class.java, "expenses.db")
        .addCallback(SeedCallback(today))
        .build()

/** A few rows from the last two weeks so the first question has something to find. */
private class SeedCallback(private val today: LocalDate) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        fun row(cents: Long, category: Category, daysAgo: Long, note: String) = db.execSQL(
            "INSERT INTO expenses (amountCents, category, date, note) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(cents, category.name, today.minusDays(daysAgo).toString(), note),
        )
        row(4250, Category.GROCERIES, 1, "Migros")
        row(1290, Category.EATING_OUT, 2, "Lunch")
        row(320, Category.TRANSPORT, 3, "Metro")
        row(2780, Category.GROCERIES, 6, "Farmers market")
        row(3450, Category.EATING_OUT, 9, "Pizza night")
        row(85000, Category.RENT, 12, "Rent")
    }
}
