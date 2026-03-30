package com.lifestreams.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import kotlin.math.min
import kotlin.math.max

/**
 * Widget 1 — Grid View
 * Shows a mini top-down perspective grid with all checker pieces,
 * each labeled with the stream name and hour count.
 * Size: 2×2 cells (approx 180×180dp)
 */
class GridWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateGridWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateGridWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val streams = StreamData.load(context)

            // Widget canvas size in px (use 320px for crisp rendering on hdpi)
            val W = 320
            val H = 320
            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawGridWidgetCanvas(canvas, W, H, streams)

            val views = RemoteViews(context.packageName, R.layout.widget_image)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            // Tap opens the app
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, pi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun drawGridWidgetCanvas(
            canvas: Canvas,
            W: Int,
            H: Int,
            streams: List<Stream>
        ) {
            // ── Background ──
            canvas.drawColor(Color.parseColor("#0a0a12"))

            // ── Rounded clip ──
            val clipPath = Path().apply {
                addRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), 24f, 24f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawColor(Color.parseColor("#0a0a12"))

            if (streams.isEmpty()) {
                drawEmptyState(canvas, W, H)
                return
            }

            // ── Perspective grid ──
            drawPerspectiveGrid(canvas, W, H)

            // ── Checker pieces ──
            // Layout: columns = stream index, rows = hour depth
            val n = streams.size
            val maxHours = max(streams.maxOfOrNull { it.hours } ?: 1, 1)

            // Horizontal: spread checkers across width
            // Vertical: origin at bottom ~80% of H, each hour = some pixels up
            val originY = H * 0.78f
            val horizMargin = W * 0.12f
            val colWidth = if (n > 1) (W - 2 * horizMargin) / (n - 1) else 0f

            // Max visual depth: 60% of height
            val maxDepthPx = H * 0.58f
            val UNIT = maxDepthPx / max(maxHours.toFloat(), 8f)

            streams.forEachIndexed { i, stream ->
                val cx = if (n == 1) W / 2f else horizMargin + i * colWidth
                val depth = stream.hours * UNIT
                // Perspective: objects further away appear smaller and higher
                // t=0 (0 hours) = near/bottom, t=1 (maxHours) = far/top
                val t = (depth / maxDepthPx).coerceIn(0f, 0.95f)
                val cy = originY - depth
                // Perspective scale: near=1.0, far=0.45
                val scale = 1.0f - t * 0.55f
                val baseRadius = W * 0.09f

                drawChecker3D(canvas, cx, cy, baseRadius * scale, stream, scale)
            }

            // ── Title ──
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#ffffff")
                alpha = 180
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Life Streams", 14f, 22f, titlePaint)
        }

        private fun drawPerspectiveGrid(canvas: Canvas, W: Int, H: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7c6af7")
                alpha = 28
                strokeWidth = 0.8f
                style = Paint.Style.STROKE
            }

            val vanishX = W / 2f
            val vanishY = H * 0.28f   // horizon

            // Horizontal lines (compressed toward horizon)
            val numH = 8
            for (i in 0..numH) {
                val t = i.toFloat() / numH
                // Perspective compression: lines bunch up near horizon
                val y = vanishY + (H - vanishY) * (1f - (1f - t) * (1f - t))
                canvas.drawLine(0f, y, W.toFloat(), y, paint)
            }

            // Vertical lines converging to vanishing point
            val numV = 7
            for (i in 0..numV) {
                val x = W.toFloat() * i / numV
                canvas.drawLine(x, H.toFloat(), vanishX + (x - vanishX) * 0.05f, vanishY, paint)
            }
        }

        private fun drawChecker3D(
            canvas: Canvas,
            cx: Float, cy: Float,
            r: Float,
            stream: Stream,
            scale: Float
        ) {
            val color = StreamData.parseColor(stream.color)
            val darkColor = StreamData.darken(color, 0.55f)
            val h = r * 0.35f  // cylinder height in screen space

            // Shadow
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.BLACK
                alpha = 80
                maskFilter = BlurMaskFilter(r * 0.5f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawOval(cx - r * 0.9f, cy + h * 0.5f, cx + r * 0.9f, cy + h * 1.1f + r * 0.25f, shadowPaint)

            // Side face (cylinder bottom ellipse)
            val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = darkColor
                style = Paint.Style.FILL
            }
            canvas.drawOval(cx - r, cy + h, cx + r, cy + h + r * 0.42f, sidePaint)

            // Top face gradient
            val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - r * 0.3f, cy - r * 0.2f, r * 1.1f,
                    intArrayOf(color, darkColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            canvas.drawOval(cx - r, cy - r * 0.4f, cx + r, cy + r * 0.4f, topPaint)

            // Inner ring
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                alpha = 70
                strokeWidth = max(0.8f, r * 0.06f)
                style = Paint.Style.STROKE
            }
            canvas.drawOval(cx - r * 0.76f, cy - r * 0.31f, cx + r * 0.76f, cy + r * 0.31f, ringPaint)

            // Gloss highlight
            val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - r * 0.3f, cy - r * 0.15f, r * 0.6f,
                    intArrayOf(Color.argb(80, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawOval(cx - r, cy - r * 0.4f, cx + r, cy + r * 0.4f, glossPaint)

            // Hours number
            val numSize = (r * 0.72f).coerceIn(9f, 22f)
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = numSize
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(2f, 0f, 1f, Color.argb(180, 0, 0, 0))
            }
            canvas.drawText(stream.hours.toString(), cx, cy + numSize * 0.35f, numPaint)

            // Stream name below checker
            val nameSize = (r * 0.38f).coerceIn(7f, 12f)
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = StreamData.parseColor(stream.color)
                textSize = nameSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                alpha = 220
            }
            // Truncate name to fit
            var displayName = stream.name
            while (namePaint.measureText(displayName) > r * 2.2f && displayName.length > 2) {
                displayName = displayName.dropLast(1)
            }
            if (displayName != stream.name) displayName += "…"
            canvas.drawText(displayName, cx, cy + r * 0.6f + nameSize + 2f, namePaint)
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
            paint.textSize = 11f
            paint.alpha = 70
            canvas.drawText("Open Life Streams to start", W / 2f, H / 2f + 20f, paint)
        }
    }
}
