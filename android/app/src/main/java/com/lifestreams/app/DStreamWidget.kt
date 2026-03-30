package com.lifestreams.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

class DStreamWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {

        const val ACTION_FIT_ALL = "com.lifestreams.app.ACTION_FIT_ALL"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val streams = StreamData.load(context)
            val mapName = StreamData.getActiveMapName(context)

            val W = 360
            val H = 360
            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawWidget(canvas, W, H, streams, mapName)

            val views = RemoteViews(context.packageName, R.layout.widget_image)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            // Tap body → open app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, openPi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun drawWidget(
            canvas: Canvas, W: Int, H: Int,
            streams: List<Stream>, mapName: String
        ) {
            val Wf = W.toFloat()
            val Hf = H.toFloat()

            // ── Background with rounded corners ──
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0a0a12")
            }
            val clipPath = Path().apply {
                addRoundRect(RectF(0f, 0f, Wf, Hf), 28f, 28f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawRoundRect(RectF(0f, 0f, Wf, Hf), 28f, 28f, bgPaint)

            // ── Header area ──
            val headerH = 44f
            // Map name
            val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                alpha = 200
            }
            canvas.drawText(mapName, 16f, 28f, mapPaint)

            // Total hours
            val totalHours = streams.sumOf { it.hours }
            val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7c6af7")
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                alpha = 180
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${totalHours}h", Wf - 16f, 28f, totalPaint)

            // ── FitAll button (top-right circle) ──
            val btnR = 18f
            val btnX = Wf - btnR - 12f
            val btnY = Hf - btnR - 12f
            drawFitAllButton(canvas, btnX, btnY, btnR)

            if (streams.isEmpty()) {
                drawEmpty(canvas, Wf, Hf)
                return
            }

            // ── Board area ──
            val boardTop = headerH
            val boardBottom = Hf - 16f
            val boardH = boardBottom - boardTop
            val boardW = Wf

            drawBoard(canvas, boardW, boardH, boardTop, streams)
        }

        private fun drawBoard(
            canvas: Canvas, W: Float, H: Float,
            offsetY: Float, streams: List<Stream>
        ) {
            // Simple perspective projection matching the app's feel
            // Camera looks down at ~22 degree tilt
            val n = streams.size
            val maxHours = max(streams.maxOfOrNull { it.hours } ?: 1, 1)

            val LANE_GAP = W / (n + 1f)
            val UNIT = H * 0.055f  // pixels per hour
            val maxZ = max(maxHours * UNIT, H * 0.5f)

            // Project world coords to screen
            // Simple perspective: x stays, z maps to y (bottom=0h, top=maxHours)
            val tilt = 0.72f  // perspective compression factor
            val vanishY = offsetY + H * 0.12f
            val originY = offsetY + H * 0.92f

            fun projectY(z: Float): Float {
                val t = (z / maxZ).coerceIn(0f, 1f)
                return originY - (originY - vanishY) * (1f - (1f - t) * (1f - t * tilt))
            }

            fun projectX(laneX: Float, z: Float): Float {
                val t = (z / maxZ).coerceIn(0f, 1f)
                val centerX = W / 2f
                return centerX + (laneX - centerX) * (1f - t * 0.35f)
            }

            fun projectScale(z: Float): Float {
                val t = (z / maxZ).coerceIn(0f, 1f)
                return 1f - t * 0.5f
            }

            // ── Grid lines ──
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7c6af7")
                alpha = 22
                strokeWidth = 0.6f
                style = Paint.Style.STROKE
            }

            // Horizontal grid lines
            for (step in 0..8) {
                val z = maxZ * step / 8f
                val y = projectY(z)
                val leftX = projectX(0f, z)
                val rightX = projectX(W, z)
                canvas.drawLine(leftX, y, rightX, y, gridPaint)
            }

            // Vertical grid lines
            val gridCols = min(n + 2, 8)
            for (col in 0..gridCols) {
                val wx = W * col / gridCols
                val y0 = projectY(0f)
                val y1 = projectY(maxZ)
                val x0 = projectX(wx, 0f)
                val x1 = projectX(wx, maxZ)
                canvas.drawLine(x0, y0, x1, y1, gridPaint)
            }

            // ── Lanes + Checkers ──
            // Sort by depth (far first) for correct overdraw
            val laneXs = (0 until n).map { i ->
                val wx = LANE_GAP * (i + 1)
                wx
            }

            // Draw lanes
            streams.forEachIndexed { i, stream ->
                val lx = laneXs[i]
                val checkerZ = stream.hours * UNIT
                val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = StreamData.parseColor(stream.color)
                    alpha = 140
                    strokeWidth = 1.2f
                    style = Paint.Style.STROKE
                }
                val x0 = projectX(lx, 0f)
                val y0 = projectY(0f)
                val x1 = projectX(lx, checkerZ + UNIT * 2)
                val y1 = projectY(checkerZ + UNIT * 2)
                canvas.drawLine(x0, y0, x1, y1, lanePaint)

                // Origin dot
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    alpha = 60
                }
                canvas.drawCircle(x0, y0, 2.5f, dotPaint)
            }

            // Draw checkers (sorted far-to-near)
            val sorted = streams.mapIndexed { i, s -> Pair(i, s) }
                .sortedByDescending { (_, s) -> s.hours }

            sorted.forEach { (i, stream) ->
                val lx = laneXs[i]
                val z = stream.hours * UNIT
                val cx = projectX(lx, z)
                val cy = projectY(z)
                val sc = projectScale(z)
                val baseR = W * 0.072f
                val r = baseR * sc.coerceIn(0.35f, 1f)

                drawChecker(canvas, cx, cy, r, stream)
            }
        }

        private fun drawChecker(
            canvas: Canvas, cx: Float, cy: Float, r: Float, stream: Stream
        ) {
            val color = StreamData.parseColor(stream.color)
            val dark = StreamData.darken(color, 0.55f)

            // Shadow
            if (r > 8f) {
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.BLACK; alpha = 60
                    maskFilter = BlurMaskFilter(r * 0.4f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawCircle(cx + 1f, cy + r * 0.15f, r * 0.85f, sp)
            }

            // Main circle
            val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - r * 0.3f, cy - r * 0.25f, r,
                    intArrayOf(color, dark), floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r, mp)

            // Inner ring
            if (r > 10f) {
                val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE; alpha = 55
                    strokeWidth = max(0.7f, r * 0.06f); style = Paint.Style.STROKE
                }
                canvas.drawCircle(cx, cy, r * 0.75f, rp)
            }

            // Hours text
            val fs = (r * 0.68f).coerceIn(8f, 20f)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = fs
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(1.5f, 0f, 0.5f, Color.argb(160, 0, 0, 0))
            }
            canvas.drawText(stream.hours.toString(), cx, cy + fs * 0.36f, tp)

            // Name label above
            if (r > 12f) {
                val ns = (r * 0.36f).coerceIn(7f, 11f)
                val np = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = StreamData.parseColor(stream.color)
                    textSize = ns
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    alpha = 210
                }
                var name = stream.name
                while (np.measureText(name) > r * 2.4f && name.length > 2)
                    name = name.dropLast(1)
                if (name != stream.name) name += "·"
                canvas.drawText(name, cx, cy - r - 4f, np)
            }
        }

        private fun drawFitAllButton(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            // Glass pill button
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7c6af7")
                alpha = 200
            }
            canvas.drawCircle(cx, cy, r, bgPaint)

            // Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; alpha = 60
                strokeWidth = 1f; style = Paint.Style.STROKE
            }
            canvas.drawCircle(cx, cy, r, borderPaint)

            // ⊡ icon
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; alpha = 230
                textSize = r * 0.9f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("⊡", cx, cy + r * 0.32f, iconPaint)
        }

        private fun drawEmpty(canvas: Canvas, W: Float, H: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7c6af7"); alpha = 100
                textSize = 12f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("No streams yet", W / 2f, H / 2f, p)
        }
    }
}
