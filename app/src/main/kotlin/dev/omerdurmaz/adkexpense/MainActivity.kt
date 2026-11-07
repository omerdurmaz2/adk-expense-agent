package dev.omerdurmaz.adkexpense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.omerdurmaz.adkexpense.agent.hasFirebase
import dev.omerdurmaz.adkexpense.ui.FinanceChatScreen
import dev.omerdurmaz.adkexpense.ui.FinanceChatViewModel

// A stable session ID is what lets the transcript survive restarts (see the README).
private const val USER_ID = "local_user"
private const val SESSION_ID = "main"

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ExpenseApp
        val modelLabel = if (hasFirebase()) R.string.model_firebase else R.string.model_demo
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                val vm = viewModel { FinanceChatViewModel(app.runner, USER_ID, SESSION_ID) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(stringResource(R.string.app_name))
                                    Text(
                                        stringResource(modelLabel),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
                        FinanceChatScreen(vm)
                    }
                }
            }
        }
    }
}
