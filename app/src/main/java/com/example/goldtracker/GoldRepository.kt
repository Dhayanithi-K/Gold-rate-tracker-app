package com.example.goldtracker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls COMEX gold futures (GC=F, USD/oz) and USD/INR (INR=X) from Yahoo Finance's public
 * chart endpoint. No API key needed. The raw numbers are cached; conversion to ₹ per gram /
 * 10 g / karat happens at display time (see [Pricing]).
 */
object GoldRepository {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"

    private fun url(symbol: String, range: Range) =
        "https://query1.finance.yahoo.com/v8/finance/chart/$symbol" +
            "?range=${range.yahooRange}&interval=${range.interval}"

    private fun httpGet(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Returns (epochSeconds, close) pairs in ascending time order, skipping null bars. */
    private fun parse(json: String): List<Pair<Long, Double>> {
        val result = JSONObject(json)
            .getJSONObject("chart")
            .getJSONArray("result")
            .getJSONObject(0)
        val ts = result.optJSONArray("timestamp") ?: return emptyList()
        val close = result.getJSONObject("indicators")
            .getJSONArray("quote").getJSONObject(0)
            .getJSONArray("close")
        val out = ArrayList<Pair<Long, Double>>(ts.length())
        for (i in 0 until ts.length()) {
            if (i >= close.length() || close.isNull(i)) continue
            out.add(ts.getLong(i) to close.getDouble(i))
        }
        return out.sortedBy { it.first }
    }

    suspend fun fetch(range: Range): Series = withContext(Dispatchers.IO) {
        val gold = parse(httpGet(url("GC%3DF", range)))
        val fx = parse(httpGet(url("INR%3DX", range)))
        if (gold.size < 2) throw IOException("No gold price data received")
        if (fx.isEmpty()) throw IOException("No USD/INR data received")

        // Pair each gold bar with the latest USD/INR bar at or before it.
        var j = 0
        var points = gold.map { (ts, usd) ->
            while (j + 1 < fx.size && fx[j + 1].first <= ts) j++
            Point(ts, usd, fx[j].second)
        }

        // "Day" = the most recent 24 hours of trading data we have.
        if (range == Range.DAY) {
            val cutoff = points.last().ts - 24 * 3600
            points = points.filter { it.ts >= cutoff }
        }
        Series(points, System.currentTimeMillis())
    }

    /** Fetch, cache for the widget/offline use, and return. */
    suspend fun refresh(context: Context, range: Range): Series {
        val series = fetch(range)
        Settings(context).saveSeries(range, series)
        return series
    }
}
