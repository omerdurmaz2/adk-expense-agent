package dev.omerdurmaz.adkexpense

import android.app.Application
import com.google.adk.kt.runners.InMemoryRunner
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import dev.omerdurmaz.adkexpense.agent.createFinanceRunner
import dev.omerdurmaz.adkexpense.agent.hasFirebase
import dev.omerdurmaz.adkexpense.data.RoomExpenseRepository
import dev.omerdurmaz.adkexpense.data.openExpenseDatabase

class ExpenseApp : Application() {
    /** One runner per process: it owns the Room-backed session store. */
    val runner: InMemoryRunner by lazy {
        createFinanceRunner(this, RoomExpenseRepository(openExpenseDatabase(this).expenses()))
    }

    override fun onCreate() {
        super.onCreate()
        // Firebase AI Logic enforces App Check since 2026-11-02; without it every call fails.
        if (hasFirebase()) {
            Firebase.appCheck.installAppCheckProviderFactory(appCheckProviderFactory())
        }
    }
}
