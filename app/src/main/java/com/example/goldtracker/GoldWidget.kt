package com.example.goldtracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlin.math.abs

private val WidgetBg = ColorProvider(day = Color(0xFFFFF6DC), night = Color(0xFF241E0E))
private val TextMain = ColorProvider(day = Color(0xFF1C1B17), night = Color(0xFFF3E8D2))
private val TextSoft = ColorProvider(day = Color(0xFF6B6455), night = Color(0xFFB8AD94))
private val UpColor = ColorProvider(day = Palette.Up, night = Palette.Up)
private val DownColor = ColorProvider(day = Palette.Down, night = Palette.Down)

class GoldWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = Settings(context)
        val series = settings.loadSeries(Range.DAY)
        if (series == null) RefreshWorker.enqueueOnce(context)

        val values = series?.let { Pricing.values(it, settings) }.orEmpty()
        val price = values.lastOrNull()
        val first = values.firstOrNull()
        val change = if (price != null && first != null) price - first else null
        val pct = if (change != null && first != null && first != 0.0) change / first * 100 else null
        val up = (change ?: 0.0) >= 0.0

        val sparkline = if (values.size >= 2) {
            Sparkline.render(values, if (up) Palette.UpArgb else Palette.DownArgb)
        } else null

        val decimals = if (settings.unitGrams == 1) 2 else 0
        val priceText = price?.let { Pricing.formatInr(it, decimals) }
        val changeText = if (change != null && pct != null) {
            "${if (up) "▲" else "▼"} ${Pricing.formatInr(abs(change), decimals)} " +
                "(${"%.2f".format(abs(pct))}%) · 24h"
        } else null
        val title = "Gold ${settings.karat}K · per ${settings.unitGrams} g"
        val updated = series?.let { "Updated ${TimeFmt.clock(it.fetchedAtMs)}" }

        provideContent {
            WidgetContent(title, priceText, changeText, up, sparkline, updated)
        }
    }
}

@Composable
@GlanceComposable
private fun WidgetContent(
    title: String,
    price: String?,
    change: String?,
    up: Boolean,
    sparkline: Bitmap?,
    updated: String?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetBg)
            .cornerRadius(22.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(color = TextSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "↻",
                style = TextStyle(color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .padding(horizontal = 6.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            )
        }

        if (price == null) {
            Spacer(GlanceModifier.defaultWeight())
            Text("Loading gold rate…", style = TextStyle(color = TextMain, fontSize = 16.sp))
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Text(
                text = price,
                style = TextStyle(color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            if (change != null) {
                Text(
                    text = change,
                    style = TextStyle(
                        color = if (up) UpColor else DownColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            if (sparkline != null) {
                Image(
                    provider = ImageProvider(sparkline),
                    contentDescription = "24 hour gold price trend",
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(vertical = 4.dp),
                )
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            if (updated != null) {
                Text(updated, style = TextStyle(color = TextSoft, fontSize = 10.sp))
            }
        }
    }
}

class GoldWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoldWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.enqueueOnce(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}

/** Tapped via the ↻ button on the widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching { GoldRepository.refresh(context, Range.DAY) }
        GoldWidget().updateAll(context)
    }
}

/** Renders the line + soft fill as a bitmap, since Glance has no Canvas. */
private object Sparkline {
    fun render(values: List<Double>, lineColor: Int, w: Int = 600, h: Int = 180): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val lo = values.min()
        val span = (values.max() - lo).takeIf { it > 0 } ?: 1.0
        val pad = 8f

        fun x(i: Int) = pad + (w - 2 * pad) * i / (values.size - 1)
        fun y(v: Double) = (h - pad - (v - lo) / span * (h - 2 * pad)).toFloat()

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        val fill = Path().apply {
            moveTo(x(0), h.toFloat())
            for (i in values.indices) lineTo(x(i), y(values[i]))
            lineTo(x(values.size - 1), h.toFloat())
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                (lineColor and 0x00FFFFFF) or 0x55000000,
                (lineColor and 0x00FFFFFF),
                Shader.TileMode.CLAMP,
            )
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = lineColor
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(line, strokePaint)
        return bmp
    }
}
