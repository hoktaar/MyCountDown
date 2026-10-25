package de.mycountdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.UUID

/** Kopiert ein gewähltes Bild in den App-Speicher, weil der Zugriff auf die Original-URI nur vorübergehend gilt. */
fun importImage(ctx: Context, uri: Uri): String? = runCatching {
    val dir = File(ctx.filesDir, "images").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    ctx.contentResolver.openInputStream(uri)!!.use { input ->
        file.outputStream().use { input.copyTo(it) }
    }
    file.absolutePath
}.getOrNull()

/** Sucht den Ort in Google Maps (App, sonst Browser). */
fun openInMaps(ctx: Context, query: String) {
    val q = Uri.encode(query)
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).setPackage("com.google.android.apps.maps")
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q"))
    runCatching { ctx.startActivity(app) }.onFailure { runCatching { ctx.startActivity(web) } }
}
