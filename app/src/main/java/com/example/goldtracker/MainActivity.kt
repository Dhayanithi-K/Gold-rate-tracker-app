package com.example.goldtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoldTheme { GoldApp() }
        }
    }
}

private val GoldLight = lightColorScheme(
    primary = Color(0xFF8A6A00),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF3B2F00),
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFF0E6CC),
    onSurfaceVariant = Color(0xFF5E5846),
    outlineVariant = Color(0xFFE2D8BE),
)

private val GoldDark = darkColorScheme(
    primary = Color(0xFFF2C94C),
    onPrimary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF4D3E00),
    onSecondaryContainer = Color(0xFFFFE9A8),
    background = Color(0xFF15120A),
    onBackground = Color(0xFFF3E8D2),
    surface = Color(0xFF15120A),
    onSurface = Color(0xFFF3E8D2),
    surfaceVariant = Color(0xFF2E2819),
    onSurfaceVariant = Color(0xFFC4B99E),
    outlineVariant = Color(0xFF3F3724),
)

@Composable
fun GoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) GoldDark else GoldLight,
        content = content,
    )
}

@Composable
fun GoldApp() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()

    var range by rememberSaveable { mutableStateOf(Range.DAY) }
    var karat by remember { mutableIntStateOf(settings.karat) }
    var unit by remember { mutableIntStateOf(settings.unitGrams) }
    var premium by remember { mutableFloatStateOf(settings.premiumPct) }
    var series by remember(range) { mutableStateOf(settings.loadSeries(range)) }
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    suspend fun load(r: Range) {
        loading = true
        error = null
        try {
            series = GoldRepository.refresh(context, r)
            if (r == Range.DAY) GoldWidget().updateAll(context)
        } catch (e: Exception) {
            error = "Couldn't refresh (${e.message ?: "network error"})." +
                if (series != null) " Showing saved data." else ""
        } finally {
            loading = false
        }
    }

    fun updateWidget() {
        scope.launch { GoldWidget().updateAll(context) }
    }

    LaunchedEffect(range) { load(range) }

    // ---- derived values ----
    val points = series?.points.orEmpty()
    val values = remember(series, karat, unit, premium) {
        points.map { Pricing.inrPerUnit(it, karat, unit, premium) }
    }
    val decimals = if (unit == 1) 2 else 0
    val idx = (selected ?: values.lastIndex).coerceIn(0, maxOf(values.lastIndex, 0))
    val shown = values.getOrNull(idx)
    val start = values.firstOrNull()
    val change = if (shown != null && start != null) shown - start else null
    val pct = if (change != null && start != null && start != 0.0) change / start * 100 else null
    val up = (change ?: 0.0) >= 0.0
    val changeColor = if (up) Palette.Up else Palette.Down

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title + refresh
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Gold rate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { scope.launch { load(range) } }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }

            // Headline price
            Column {
                Text(
                    text = shown?.let { Pricing.formatInr(it, decimals) } ?: "—",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "per $unit g · ${karat}K gold",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (change != null && pct != null) {
                    Text(
                        text = "${if (up) "▲" else "▼"} ${Pricing.formatInr(abs(change), decimals)} " +
                            "(${"%.2f".format(abs(pct))}%) · ${range.changeLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = changeColor,
                    )
                }
                Text(
                    text = points.getOrNull(idx)?.let { TimeFmt.full(it.ts, range) }
                        ?: "Fetching latest rate…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Range selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    FilterChip(
                        selected = r == range,
                        onClick = { range = r },
                        label = { Text(r.label) },
                    )
                }
            }

            // Chart
            TrendChart(
                values = values,
                selected = if (selected != null) idx else null,
                onSelect = { selected = it },
                lineColor = MaterialTheme.colorScheme.primary,
                highLabel = values.maxOrNull()?.let { Pricing.formatInr(it, decimals) } ?: "",
                lowLabel = values.minOrNull()?.let { Pricing.formatInr(it, decimals) } ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            if (points.size >= 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(points.first(), points[points.size / 2], points.last()).forEach {
                        Text(
                            TimeFmt.axis(it.ts, range),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // High / Low / Start
            if (values.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("High", Pricing.formatInr(values.max(), decimals), Modifier.weight(1f))
                    Stat("Low", Pricing.formatInr(values.min(), decimals), Modifier.weight(1f))
                    Stat("Start", Pricing.formatInr(values.first(), decimals), Modifier.weight(1f))
                }
            }

            // Display options
            Text(
                "Display",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 22).forEach { k ->
                    FilterChip(
                        selected = karat == k,
                        onClick = { karat = k; settings.karat = k; updateWidget() },
                        label = { Text("${k}K") },
                    )
                }
                Spacer(Modifier.size(8.dp))
                listOf(1, 10).forEach { g ->
                    FilterChip(
                        selected = unit == g,
                        onClick = { unit = g; settings.unitGrams = g; updateWidget() },
                        label = { Text("$g g") },
                    )
                }
            }
            OutlinedButton(onClick = { showPremiumDialog = true }) {
                Text("Local premium: ${"%.1f".format(premium)}%")
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            val fetched = series?.fetchedAtMs?.takeIf { it > 0 }?.let { "Updated ${TimeFmt.clock(it)}. " } ?: ""
            Text(
                "$fetched" +
                    "Source: Yahoo Finance — COMEX gold futures (GC=F) × USD/INR, converted to ₹. " +
                    "This is an indicative international-price rate; jeweller and bank rates include " +
                    "import duty, GST and margin (use Local premium to approximate).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPremiumDialog) {
        var text by remember { mutableStateOf(if (premium == 0f) "" else premium.toString()) }
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            title = { Text("Local premium") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Percent added on top of the converted international price " +
                            "(import duty, GST, margin). Leave blank for the plain converted price.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    premium = (text.toFloatOrNull() ?: 0f).coerceIn(0f, 50f)
                    settings.premiumPct = premium
                    showPremiumDialog = false
                    updateWidget()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Line chart with a soft fill. Press and drag across it to inspect individual points. */
@Composable
private fun TrendChart(
    values: List<Double>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    lineColor: Color,
    highLabel: String,
    lowLabel: String,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "No chart data yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotCenter = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()

    val lo = values.min()
    val hi = values.max()
    val pad = if (hi > lo) (hi - lo) * 0.12 else hi * 0.01
    val minY = lo - pad
    val maxY = hi + pad

    Canvas(
        modifier = modifier.pointerInput(values.size) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun indexAt(x: Float) =
                    ((x / size.width) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                onSelect(indexAt(down.position.x))
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.pressed) {
                        onSelect(indexAt(change.position.x))
                        // Only claim the gesture when it is mostly horizontal, so vertical
                        // scrolling of the page still works when starting on the chart.
                        val dx = abs(change.position.x - down.position.x)
                        val dy = abs(change.position.y - down.position.y)
                        if (dx > dy) change.consume()
                    }
                } while (event.changes.any { it.pressed })
                onSelect(null)
            }
        },
    ) {
        val w = size.width
        val h = size.height
        fun px(i: Int): Float = w * i / (values.size - 1)
        fun py(v: Double): Float = (h - (v - minY) / (maxY - minY) * h).toFloat()

        // grid
        for (g in 0..3) {
            val gy = h * g / 3f
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
        }

        // fill + line
        val linePath = Path().apply {
            moveTo(px(0), py(values[0]))
            for (i in 1 until values.size) lineTo(px(i), py(values[i]))
        }
        val fillPath = Path().apply {
            moveTo(px(0), h)
            for (i in values.indices) lineTo(px(i), py(values[i]))
            lineTo(px(values.size - 1), h)
            close()
        }
        drawPath(
            fillPath,
            Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)),
        )
        drawPath(
            linePath,
            lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // high / low labels
        val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)
        drawText(measurer, highLabel, Offset(4.dp.toPx(), 2.dp.toPx()), labelStyle)
        val lowSize = measurer.measure(lowLabel, labelStyle).size
        drawText(measurer, lowLabel, Offset(4.dp.toPx(), h - lowSize.height - 2.dp.toPx()), labelStyle)

        // inspected point
        if (selected != null) {
            val sx = px(selected)
            val sy = py(values[selected])
            drawLine(lineColor.copy(alpha = 0.5f), Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
            drawCircle(lineColor, radius = 6.dp.toPx(), center = Offset(sx, sy))
            drawCircle(dotCenter, radius = 2.5.dp.toPx(), center = Offset(sx, sy))
        }
    }
}
