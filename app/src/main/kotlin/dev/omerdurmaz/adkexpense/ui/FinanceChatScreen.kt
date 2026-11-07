package dev.omerdurmaz.adkexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.omerdurmaz.adkexpense.R
import java.util.Locale

@Composable
fun FinanceChatScreen(vm: FinanceChatViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.lines.size, state.pending) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(state.lines) { line ->
                Text(
                    line.text,
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = if (line.fromUser) MaterialTheme.colorScheme.primary else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = if (line.fromUser) TextAlign.End else TextAlign.Start,
                )
            }
            if (state.streaming.isNotEmpty()) item { Text(state.streaming) }
        }
        state.pending?.let { pending ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    pending.drafts.forEach { draft ->
                        val amount = (draft["amount"] as? Number)
                            ?.let { String.format(Locale.ROOT, "%.2f", it.toDouble()) }
                            ?: draft["amount"].toString()
                        val category = draft["category"].toString()
                        val date = draft["date"].toString()
                        Text(stringResource(R.string.confirm_expense, amount, category, date))
                        // Show the note too: it is model text the user is about to store.
                        draft["note"]?.toString()?.takeIf { it.isNotBlank() }?.let { note ->
                            Text("“$note”", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { vm.answer(approved = false) }) {
                            Text(stringResource(R.string.reject))
                        }
                        Button(onClick = { vm.answer(approved = true) }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
        if (state.failed) {
            Text(stringResource(R.string.agent_failed), color = MaterialTheme.colorScheme.error)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input,
                { input = it },
                Modifier.weight(1f).padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.input_hint)) },
            )
            Button(
                onClick = { vm.send(input.trim()); input = "" },
                enabled = !state.busy && state.pending == null && input.isNotBlank(),
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
}
