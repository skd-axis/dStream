package com.lifestreams.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import kotlin.math.max

/**
 * Widget 2 — Top 3 Streams
 * Shows the 3 streams with the most hours logged.
 * Each shows: colored circle with hours + stream name + mini bar.
 * Size: 2×2 cells
 */
class TopStreamsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateTopWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateTopWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val streams = StreamData.load(context)

            val W = 320
            val H = 320
            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawTopStreamsCanvas(canvas, W, H, streams)

            val views = RemoteViews(context.packageName, R.layout.widget_image)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, pi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun drawTopStreamsCanvas(
            canvas: Canvas,
            W: Int, H: Int,
            allStreams: List<Stream>
        ) {
            // Background + rounded clip
            val clipPath = Path().apply {
                addRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), 24f, 24f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawColor(Color.parseColor("#0d0d1a"))

            // Sort by hours descending, take top 3
            val top = allStreams.sortedByDescending { it.hours }.take(3)

            if (top.isEmpty()) {
                drawEmptyState(canvas, W, H)
                return
            }

            val maxH = max(top.maxOfOrNull { it.hours } ?: 1, 1)

            // ── Header ──
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 200
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Top Streams", 16f, 26f, headerPaint)

            // Accent line under header
            val accentPaint = Paint().apply {
                color = Color.parseColor("#7c6af7")
                alpha = 120
                strokeWidth = 1.5f
            }
            canvas.drawLine(16f, 32f, W - 16f, 32f, accentPaint)

            // ── Stream rows ──
            val rowH = (H - 48f) / top.size
            val circleR = 26f
            val barMaxW = W - 100f

            top.forEachIndexed { i, stream ->
                val rowTop = 42f + i * rowH
                val rowCenterY = rowTop + rowH / 2f
                val color = StreamData.parseColor(stream.color)
                val darkColor = StreamData.darken(color, 0.55f)

                // ── Circle with 3D look ──
                val cx = 20f + circleR
                val cy = rowCenterY

                // Shadow
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.BLACK
                    alpha = 100
                    maskFilter = BlurMaskFilter(circleR * 0.4f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawCircle(cx + 2f, cy + 3f, circleR, shadowPaint)

                // Side (bottom shifted ellipse)
                val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = darkColor
                }
                val sideH = circleR * 0.18f
                canvas.drawOval(cx - circleR, cy + circleR * 0.65f,
                    cx + circleR, cy + circleR * 0.65f + sideH * 2, sidePaint)

                // Top face gradient
                val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx - circleR * 0.3f, cy - circleR * 0.3f, circleR * 1.1f,
                        intArrayOf(color, darkColor),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(cx, cy, circleR, topPaint)

                // Gloss
                val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx - circleR * 0.3f, cy - circleR * 0.25f, circleR * 0.65f,
                        intArrayOf(Color.argb(90, 255, 255, 255), Color.TRANSPARENT),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(cx, cy, circleR, glossPaint)

                // Inner ring
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    alpha = 60
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                canvas.drawCircle(cx, cy, circleR * 0.76f, ringPaint)

                // Hours in circle
                val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    textSize = 18f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(2f, 0f, 1f, Color.argb(180, 0, 0, 0))
                }
                canvas.drawText(stream.hours.toString(), cx, cy + 6f, numPaint)

                // ── Text info ──
                val textX = cx + circleR + 14f

                // Stream name
                val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                // Truncate
                var displayName = stream.name
                val maxNameW = W - textX - 16f
                while (namePaint.measureText(displayName) > maxNameW && displayName.length > 2)
                    displayName = displayName.dropLast(1)
                if (displayName != stream.name) displayName += "…"
                canvas.drawText(displayName, textX, rowCenterY - 6f, namePaint)

                // Hours label
                val hoursLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    alpha = 120
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
                canvas.drawText("${stream.hours}h logged", textX, rowCenterY + 11f, hoursLabelPaint)

                // ── Progress bar ──
                val barY = rowCenterY + 24f
                val barH2 = 4f
                val barW = barMaxW * (stream.hours.toFloat() / maxH)

                // Track
                val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    alpha = 20
                }
                canvas.drawRoundRect(textX, barY, textX + barMaxW, barY + barH2, 2f, 2f, trackPaint)

                // Fill
                if (stream.hours > 0) {
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            textX, barY, textX + barW, barY + barH2,
                            intArrayOf(color, StreamData.darken(color, 0.75f)),
                            null, Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRoundRect(textX, barY, textX + barW, barY + barH2, 2f, 2f, fillPaint)
                }

                // Divider between rows (not after last)
                if (i < top.size - 1) {
                    val divPaint = Paint().apply {
                        this.color = Color.WHITE
                        alpha = 15
                        strokeWidth = 0.8f
                    }
                    canvas.drawLine(16f, rowTop + rowH, W - 16f, rowTop + rowH, divPaint)
                }
            }

            // ── Rank badge on #1 ──
            if (top.isNotEmpty()) {
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#f7c06a")
                    textSize = 10f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("★", W - 20f, 58f, badgePaint)
            }
        }

        private fun drawEmptyState(canvas: Canvas, W: Int, H: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7c6af7")
                alpha = 120
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("No streams yet", W / 2f, H / 2f, paint)
        }
    }
}
