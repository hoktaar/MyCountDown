package de.mycountdown

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

class CountdownViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = CountdownStore(app)
    var items by mutableStateOf(store.load())
        private set

    private fun update(new: List<Countdown>) {
        items = new
        store.save(new)
        CountdownWidget.updateAll(app)
    }

    var categories by mutableStateOf(store.loadCategories())
        private set

    var customTemplates by mutableStateOf(store.loadTemplates())
        private set

    fun addTemplate(t: CustomTemplate) {
        customTemplates = customTemplates + t
        store.saveTemplates(customTemplates)
    }

    fun deleteTemplate(t: CustomTemplate) {
        customTemplates = customTemplates - t
        store.saveTemplates(customTemplates)
    }

    fun addCategory(c: Category) {
        categories = categories + c
        store.saveCategories(categories)
    }

    fun deleteCategory(c: Category) {
        categories = categories - c
        store.saveCategories(categories)
        update(items.map { if (it.categoryId == c.id) it.copy(categoryId = null) else it })
    }

    fun upsert(c: Countdown) = update(items.filterNot { it.id == c.id } + c)
    fun delete(c: Countdown) = update(items.filterNot { it.id == c.id })
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel(this)
        scheduleDailyReminder(this)
        setContent {
            val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (!canNotify(this@MainActivity)) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            MaterialTheme { App() }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: CountdownViewModel = viewModel()) {
    var editing by remember { mutableStateOf<Countdown?>(null) }
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<String?>(null) }
    val activeFilter = filter?.takeIf { id -> vm.categories.any { it.id == id } }
    // Datum neu berechnen, wenn die App wieder in den Vordergrund kommt / Liste sich ändert.
    val today = LocalDate.now()
    val sorted = vm.items
        .filter { activeFilter == null || it.categoryId == activeFilter }
        .sortedBy { it.daysLeft(today).let { d -> if (d < 0) Long.MAX_VALUE + d else d } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyCountDown") },
                actions = { IconButton(onClick = { managing = true }) { Icon(Icons.Default.Edit, "Kategorien verwalten") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Neuer Countdown") }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (vm.categories.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(activeFilter == null, { filter = null }, label = { Text("Alle") })
                    vm.categories.forEach { cat ->
                        FilterChip(activeFilter == cat.id, { filter = cat.id }, label = { Text("${cat.emoji} ${cat.name}") })
                    }
                }
            }
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Noch keine Countdowns.\nTippe auf + um einen anzulegen.", fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted, key = { it.id }) { c ->
                        CountdownCard(c, vm.categories.find { it.id == c.categoryId }, today, onClick = { editing = c })
                    }
                }
            }
        }
    }

    if (managing) CategoryDialog(vm.categories, { managing = false }, vm::addCategory, vm::deleteCategory)
    if (adding) EditDialog(null, vm.categories, vm.customTemplates, vm::addTemplate, vm::deleteTemplate, { adding = false }, { vm.upsert(it); adding = false }, null)
    editing?.let { c ->
        EditDialog(c, vm.categories, vm.customTemplates, vm::addTemplate, vm::deleteTemplate, { editing = null }, { vm.upsert(it); editing = null }, { vm.delete(c); editing = null })
    }
}

@Composable
fun CountdownCard(c: Countdown, category: Category?, today: LocalDate, onClick: () -> Unit) {
    val days = c.daysLeft(today)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 36.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(c.target(today).format(dateFormat), fontSize = 13.sp)
                if (c.repeat != Repeat.NONE) Text("🔁 ${c.repeat.label}", fontSize = 12.sp)
                if (category != null) Text("${category.emoji} ${category.name}", fontSize = 12.sp)
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
fun EditDialog(
    initial: Countdown?,
    categories: List<Category>,
    customTemplates: List<CustomTemplate>,
    onAddTemplate: (CustomTemplate) -> Unit,
    onDeleteTemplate: (CustomTemplate) -> Unit,
    onDismiss: () -> Unit, onSave: (Countdown) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "📅") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(7)) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.NONE) }
    var categoryId by remember { mutableStateOf(initial?.categoryId?.takeIf { id -> categories.any { it.id == id } }) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Countdown" else "Countdown bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Text("Vorlagen", fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val apply = { t: Template ->
                            title = t.title; emoji = t.emoji; repeat = t.repeat
                            t.date()?.let { date = it }
                        }
                        templates.forEach { t ->
                            AssistChip(onClick = { apply(t) }, label = { Text("${t.emoji} ${t.title}") })
                        }
                        customTemplates.forEach { ct ->
                            InputChip(
                                selected = false,
                                onClick = { apply(ct.toTemplate()) },
                                label = { Text("${ct.emoji} ${ct.title}") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close, "Vorlage löschen",
                                        Modifier.size(18.dp).clickable { onDeleteTemplate(ct) },
                                    )
                                },
                            )
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
                if (categories.isNotEmpty()) {
                    Text("Kategorie", fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(categoryId == null, { categoryId = null }, label = { Text("Keine") })
                        categories.forEach { cat ->
                            FilterChip(categoryId == cat.id, { categoryId = cat.id; emoji = cat.emoji }, label = { Text("${cat.emoji} ${cat.name}") })
                        }
                    }
                }
                Text("Wiederholung", fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Repeat.entries.forEach {
                        FilterChip(repeat == it, { repeat = it }, label = { Text(it.label) })
                    }
                }
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onAddTemplate(
                            CustomTemplate(
                                title = title.trim(), emoji = emoji.ifBlank { "📅" }, repeat = repeat,
                                monthDay = if (repeat == Repeat.YEARLY) "%02d-%02d".format(date.monthValue, date.dayOfMonth) else null,
                            )
                        )
                    },
                ) { Text("⭐ Als Vorlage speichern") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Countdown(title = "", emoji = "", date = date))
                            .copy(title = title.trim(), emoji = emoji.ifBlank { "📅" }, date = date, repeat = repeat, categoryId = categoryId)
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

@Composable
fun CategoryDialog(categories: List<Category>, onDismiss: () -> Unit, onAdd: (Category) -> Unit, onDelete: (Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📁") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorien") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isEmpty()) Text("Noch keine Kategorien.", fontSize = 14.sp)
                categories.forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${cat.emoji} ${cat.name}", Modifier.weight(1f), fontSize = 16.sp)
                        IconButton(onClick = { onDelete(cat) }) { Icon(Icons.Default.Delete, "Löschen") }
                    }
                }
                HorizontalDivider()
                Text("Neue Kategorie", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, Modifier.width(80.dp), singleLine = true)
                    OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                }
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onAdd(Category(name = name.trim(), emoji = emoji.ifBlank { "📁" })); name = "" },
                ) { Text("Hinzufügen") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}
