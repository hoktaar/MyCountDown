package de.mycountdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Bildsuche über Pexels; [onPicked] bekommt den lokalen Dateipfad des heruntergeladenen Fotos. */
@Composable
fun PexelsDialog(initialQuery: String, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<PexelsPhoto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (query.isBlank()) return
        loading = true
        message = null
        scope.launch {
            runCatching { searchPexels(query.trim()) }
                .onSuccess { results = it; if (it.isEmpty()) message = "Keine Treffer." }
                .onFailure { message = "Suche fehlgeschlagen (${it.message})." }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bild suchen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pexelsConfigured) {
                    Text("Kein Pexels-API-Key hinterlegt. Siehe README.", fontSize = 14.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Suchbegriff") }, singleLine = true)
                        Button(onClick = { search() }, enabled = !loading) { Text("Los") }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let { Text(it, fontSize = 13.sp) }
                    LazyVerticalGrid(
                        GridCells.Fixed(2),
                        Modifier.height(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(results) { photo ->
                            Column {
                                AsyncImage(
                                    photo.thumbUrl, photo.photographer,
                                    Modifier.fillMaxWidth().height(80.dp).clickable(enabled = !loading) {
                                        loading = true
                                        scope.launch {
                                            val path = downloadPexelsPhoto(ctx, photo)
                                            loading = false
                                            if (path != null) onPicked(path) else message = "Download fehlgeschlagen."
                                        }
                                    },
                                    contentScale = ContentScale.Crop,
                                )
                                Text(photo.photographer, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    TextButton(onClick = { openUrl(ctx, "https://www.pexels.com") }) { Text("Fotos von Pexels") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
