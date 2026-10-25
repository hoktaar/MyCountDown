package de.mycountdown

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class CountdownViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CountdownStore(app)
    var items by mutableStateOf(store.load())
        private set

    private fun update(new: List<Countdown>) {
        items = new
        store.save(new)
    }

    fun upsert(c: Countdown) = update(items.filterNot { it.id == c.id } + c)
    fun delete(c: Countdown) = update(items.filterNot { it.id == c.id })
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: CountdownViewModel = viewModel()) {
    var editing by remember { mutableStateOf<Countdown?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Datum neu berechnen, wenn die App wieder in den Vordergrund kommt / Liste sich ändert.
    val today = LocalDate.now()
    val sorted = vm.items.sortedBy { it.daysLeft(today).let { d -> if (d < 0) Long.MAX_VALUE + d else d } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MyCountDown") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Neuer Countdown") }
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("Noch keine Countdowns.\nTippe auf + um einen anzulegen.", fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.id }) { c ->
                    CountdownCard(c, today, onClick = { editing = c })
                }
            }
        }
    }

    if (adding) EditDialog(null, { adding = false }, { vm.upsert(it); adding = false }, null)
    editing?.let { c ->
        EditDialog(c, { editing = null }, { vm.upsert(it); editing = null }, { vm.delete(c); editing = null })
    }
}

@Composable
fun CountdownCard(c: Countdown, today: LocalDate, onClick: () -> Unit) {
    val days = c.daysLeft(today)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 36.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(c.target(today).format(dateFormat), fontSize = 13.sp)
                if (c.repeat != Repeat.NONE) Text("🔁 ${c.repeat.label}", fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                when {
                    days == 0L -> Text("Heute!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    days > 0 -> {
                        Text("$days", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(if (days == 1L) "Tag" else "Tage", fontSize = 12.sp)
                    }
                    else -> {
                        Text("${-days}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(if (days == -1L) "Tag her" else "Tage her", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDialog(initial: Countdown?, onDismiss: () -> Unit, onSave: (Countdown) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "📅") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(7)) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.NONE) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Countdown" else "Countdown bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Text("Vorlagen", fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { t ->
                            AssistChip(onClick = {
                                title = t.title; emoji = t.emoji; repeat = t.repeat
                                t.date()?.let { date = it }
                            }, label = { Text("${t.emoji} ${t.title}") })
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Symbol (Emoji)") }, singleLine = true)
                OutlinedButton(onClick = { pickDate = true }, enabled = repeat != Repeat.ADVENT && repeat != Repeat.EASTER) {
                    Text(
                        if (repeat == Repeat.ADVENT || repeat == Repeat.EASTER) "Datum wird automatisch berechnet"
                        else "📆 " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    )
                }
                Text("Wiederholung", fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Repeat.entries.forEach {
                        FilterChip(repeat == it, { repeat = it }, label = { Text(it.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Countdown(title = "", emoji = "", date = date))
                            .copy(title = title.trim(), emoji = emoji.ifBlank { "📅" }, date = date, repeat = repeat)
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    IconButton(onClick = it) { Icon(Icons.Default.Delete, "Löschen") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}
