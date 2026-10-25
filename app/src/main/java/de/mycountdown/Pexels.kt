package de.mycountdown

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class PexelsPhoto(
    val photographer: String,
    val pageUrl: String,
    val thumbUrl: String,
    val imageUrl: String,
)

val pexelsConfigured: Boolean get() = BuildConfig.PEXELS_API_KEY.isNotBlank()

/** Sucht Querformat-Fotos über die Pexels-API. */
suspend fun searchPexels(query: String): List<PexelsPhoto> = withContext(Dispatchers.IO) {
    val url = "https://api.pexels.com/v1/search?query=${Uri.encode(query)}&per_page=30&orientation=landscape&locale=de-DE"
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.setRequestProperty("Authorization", BuildConfig.PEXELS_API_KEY)
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
        val photos = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("photos")
        (0 until photos.length()).map {
            val o = photos.getJSONObject(it)
            val src = o.getJSONObject("src")
            PexelsPhoto(o.getString("photographer"), o.getString("url"), src.getString("medium"), src.getString("large"))
        }
    } finally {
        conn.disconnect()
    }
}

/** Lädt das Foto in den App-Speicher und legt die Fotografen-Nennung daneben. */
suspend fun downloadPexelsPhoto(ctx: Context, photo: PexelsPhoto): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(ctx.filesDir, "images").apply { mkdirs() }
        val name = UUID.randomUUID().toString()
        val file = File(dir, "$name.jpg")
        URL(photo.imageUrl).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        File(dir, "$name.txt").writeText("${photo.photographer}\n${photo.pageUrl}")
        file.absolutePath
    }.getOrNull()
}
