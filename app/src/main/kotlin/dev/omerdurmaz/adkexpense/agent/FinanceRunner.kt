package dev.omerdurmaz.adkexpense.agent

import android.content.Context
import com.google.adk.firebase.models.Firebase as FirebaseModel
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import dev.omerdurmaz.adkexpense.data.ExpenseRepository

/** True when the google-services plugin ran, i.e. app/google-services.json was present. */
fun Context.hasFirebase(): Boolean = FirebaseApp.getApps(this).isNotEmpty()

fun createFinanceRunner(appContext: Context, repo: ExpenseRepository): InMemoryRunner {
    val model: Model = if (appContext.hasFirebase()) {
        FirebaseModel.create(
            name = "gemini-3.8-flash",
            firebaseAI = Firebase.ai(backend = GenerativeBackend.googleAI()),
        )
    } else {
        DemoModel() // offline and scripted, so a fresh clone runs without any keys
    }
    return InMemoryRunner(
        agent = financeAgent(model, FinanceTools(repo)),
        appName = "finance",
        sessionService = RoomSessionService.fromContext(appContext, "finance_agent.db"),
    )
}
