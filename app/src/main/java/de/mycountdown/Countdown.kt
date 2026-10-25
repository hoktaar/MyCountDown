package de.mycountdown

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Wie sich ein Countdown nach Ablauf wiederholt. */
enum class Repeat(val label: String) {
    NONE("Einmalig"),
    YEARLY("Jedes Jahr"),
    ADVENT("1. Advent"),
    EASTER("Ostern"),
}

data class Countdown(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val emoji: String,
    val date: LocalDate,
    val repeat: Repeat = Repeat.NONE,
    val categoryId: String? = null,
) {
    /** Das nächste relevante Datum (bei Wiederholung immer in der Zukunft bzw. heute). */
    fun target(today: LocalDate = LocalDate.now()): LocalDate = when (repeat) {
        Repeat.NONE -> date
        Repeat.YEARLY -> nextYearly(date, today)
        Repeat.ADVENT -> nextOf(today, ::firstAdvent)
        Repeat.EASTER -> nextOf(today, ::easterSunday)
    }

    fun daysLeft(today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, target(today))
}

private fun nextOf(today: LocalDate, f: (Int) -> LocalDate): LocalDate {
    val thisYear = f(today.year)
    return if (thisYear >= today) thisYear else f(today.year + 1)
}

private fun nextYearly(date: LocalDate, today: LocalDate): LocalDate {
    fun inYear(y: Int) = runCatching { date.withYear(y) }.getOrElse { date.withYear(y).withDayOfMonth(28) }
    val c = inYear(today.year)
    return if (c >= today) c else inYear(today.year + 1)
}

/** 1. Advent = vierter Sonntag vor dem 25.12. (also der Sonntag vor dem 24.12. minus 3 Wochen). */
fun firstAdvent(year: Int): LocalDate =
    LocalDate.of(year, 12, 24).with(TemporalAdjusters.previous(DayOfWeek.SUNDAY)).minusWeeks(3)

/** Ostersonntag (Gaußsche Osterformel, anonymer Algorithmus). */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

/** Vom Nutzer angelegte Kategorie, z. B. „Auto“ oder „Familie“. */
data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
)

/** Vom Nutzer gespeicherte Vorlage; bei jährlicher Wiederholung mit festem Tag (MM-dd). */
data class CustomTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val emoji: String,
    val repeat: Repeat,
    val monthDay: String? = null,
) {
    fun toTemplate() = Template(title, emoji, repeat) {
        monthDay?.let { runCatching { java.time.MonthDay.parse("--$it").atYear(LocalDate.now().year) }.getOrNull() }
    }
}

/** Vorlagen für typische Events. */
data class Template(val title: String, val emoji: String, val repeat: Repeat, val date: () -> LocalDate?)

val templates = listOf(
    Template("Urlaub", "🏖️", Repeat.NONE) { null },
    Template("1. Advent", "🕯️", Repeat.ADVENT) { null },
    Template("Weihnachten", "🎄", Repeat.YEARLY) { LocalDate.of(LocalDate.now().year, 12, 24) },
    Template("Silvester", "🎆", Repeat.YEARLY) { LocalDate.of(LocalDate.now().year, 12, 31) },
    Template("Winterreifen drauf", "❄️", Repeat.YEARLY) { LocalDate.of(LocalDate.now().year, 10, 1) },
    Template("Sommerreifen drauf", "🛞", Repeat.EASTER) { null },
    Template("Geburtstag", "🎂", Repeat.YEARLY) { null },
)

/** Einfache Persistenz als JSON in SharedPreferences. */
class CountdownStore(context: Context) {
    private val prefs = context.getSharedPreferences("countdowns", Context.MODE_PRIVATE)

    fun load(): List<Countdown> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Countdown(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    emoji = o.getString("emoji"),
                    date = LocalDate.parse(o.getString("date")),
                    repeat = Repeat.valueOf(o.getString("repeat")),
                    categoryId = o.optString("category").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun loadCategories(): List<Category> {
        val raw = prefs.getString("categories", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Category(o.getString("id"), o.getString("name"), o.getString("emoji"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveCategories(items: List<Category>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji)) }
        prefs.edit().putString("categories", arr.toString()).apply()
    }

    fun loadTemplates(): List<CustomTemplate> {
        val raw = prefs.getString("templates", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                CustomTemplate(
                    o.getString("id"), o.getString("title"), o.getString("emoji"),
                    Repeat.valueOf(o.getString("repeat")), o.optString("monthDay").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveTemplates(items: List<CustomTemplate>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("emoji", it.emoji)
                    .put("repeat", it.repeat.name).put("monthDay", it.monthDay ?: "")
            )
        }
        prefs.edit().putString("templates", arr.toString()).apply()
    }

    fun save(items: List<Countdown>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("emoji", it.emoji)
                    .put("date", it.date.toString())
                    .put("repeat", it.repeat.name)
                    .put("category", it.categoryId ?: "")
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
