package com.example.goldtracker

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Range(
    val label: String,
    val yahooRange: String,
    val interval: String,
    val changeLabel: String,
) {
    DAY("Day", "5d", "15m", "last 24 hours"),
    MONTH("Month", "1mo", "1d", "past month"),
    YEAR("Year", "1y", "1d", "past year"),
}

/** One raw observation: gold in USD per troy ounce and the USD→INR rate at that moment. */
data class Point(val ts: Long, val usdPerOz: Double, val usdInr: Double)

data class Series(val points: List<Point>, val fetchedAtMs: Long)

object Palette {
    val Up = Color(0xFF2E9E5B)
    val Down = Color(0xFFE05252)
    const val UpArgb = 0xFF2E9E5B.toInt()
    const val DownArgb = 0xFFE05252.toInt()
}

/** User choices + the last downloaded data (shared by the app and the widget). */
class Settings(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("gold_prefs", Context.MODE_PRIVATE)

    /** 24 or 22 */
    var karat: Int
        get() = sp.getInt("karat", 24)
        set(v) = sp.edit().putInt("karat", v).apply()

    /** 1 or 10 grams */
    var unitGrams: Int
        get() = sp.getInt("unit_grams", 10)
        set(v) = sp.edit().putInt("unit_grams", v).apply()

    /**
     * Percent added on top of the converted international price, to approximate
     * import duty + GST + jeweller margin. 0 = plain converted price.
     */
    var premiumPct: Float
        get() = sp.getFloat("premium_pct", 0f)
        set(v) = sp.edit().putFloat("premium_pct", v).apply()

    fun saveSeries(range: Range, s: Series) {
        val arr = JSONArray()
        s.points.forEach { arr.put(JSONArray().put(it.ts).put(it.usdPerOz).put(it.usdInr)) }
        sp.edit()
            .putString("series_${range.name}", arr.toString())
            .putLong("fetched_${range.name}", s.fetchedAtMs)
            .apply()
    }

    fun loadSeries(range: Range): Series? {
        val raw = sp.getString("series_${range.name}", null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val pts = (0 until arr.length()).map {
                val a = arr.getJSONArray(it)
                Point(a.getLong(0), a.getDouble(1), a.getDouble(2))
            }
            if (pts.isEmpty()) null else Series(pts, sp.getLong("fetched_${range.name}", 0L))
        }.getOrNull()
    }
}

object Pricing {
    private const val GRAMS_PER_TROY_OZ = 31.1034768

    fun inrPerUnit(p: Point, karat: Int, unitGrams: Int, premiumPct: Float): Double =
        p.usdPerOz * p.usdInr / GRAMS_PER_TROY_OZ *
            (karat / 24.0) *
            (1 + premiumPct / 100.0) *
            unitGrams

    fun values(s: Series, settings: Settings): List<Double> =
        s.points.map { inrPerUnit(it, settings.karat, settings.unitGrams, settings.premiumPct) }

    private val indian: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun formatInr(v: Double, decimals: Int = 0): String {
        val nf = NumberFormat.getNumberInstance(indian)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        return "₹" + nf.format(v)
    }
}

object TimeFmt {
    private fun fmt(ts: Long, pattern: String): String =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(ts))

    /** Compact label for the chart's x-axis. */
    fun axis(ts: Long, range: Range): String = when (range) {
        Range.DAY -> fmt(ts, "HH:mm")
        Range.MONTH -> fmt(ts, "d MMM")
        Range.YEAR -> fmt(ts, "MMM yy")
    }

    /** Full label for the point being inspected. */
    fun full(ts: Long, range: Range): String = when (range) {
        Range.DAY -> fmt(ts, "d MMM, HH:mm")
        Range.MONTH -> fmt(ts, "d MMM yyyy")
        Range.YEAR -> fmt(ts, "d MMM yyyy")
    }

    fun clock(ms: Long): String = fmt(ms / 1000, "HH:mm")
}
