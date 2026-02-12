package com.rajvir.FuturesTracker

import android.graphics.*

object SparklineRenderer {

    /**
     * Renders a sparkline bitmap with:
     *  • gradient fill under the line
     *  • smooth line
     *  • glowing dot at the current (rightmost) data point
     */
    fun render(data: List<Float>, positive: Boolean): Bitmap {
        val width  = 500
        val height = 200
        val dotRadius  = 8f
        val glowRadius = 14f
        val lineWidth  = 3.5f
        val padTop    = glowRadius + 2f   // room for glow at top
        val padBottom = 4f
        val chartTop    = padTop
        val chartBottom = height - padBottom

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val lineColor    = if (positive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        val gradTopColor = if (positive) Color.parseColor("#4010B981") else Color.parseColor("#40EF4444")

        val max = data.maxOrNull() ?: 1f
        val min = data.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(0.01f)

        // Map data → pixel coordinates
        val points = data.mapIndexed { i, v ->
            val x = if (data.size <= 1) width / 2f else i * (width / (data.size - 1f))
            val y = chartTop + (chartBottom - chartTop) * (1f - (v - min) / range)
            x to y
        }

        // Build line path
        val linePath = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        // ── Gradient fill under the line ──
        if (points.size >= 2) {
            val fillPath = Path(linePath)
            fillPath.lineTo(points.last().first, height.toFloat())
            fillPath.lineTo(points.first().first, height.toFloat())
            fillPath.close()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f, chartTop, 0f, height.toFloat(),
                    gradTopColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, fillPaint)
        }

        // ── Line ──
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(linePath, linePaint)

        // ── Endpoint dot with soft glow ──
        if (points.isNotEmpty()) {
            val (ex, ey) = points.last()
            // Outer glow
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(
                    ex, ey, glowRadius,
                    Color.argb(80, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(ex, ey, glowRadius, glowPaint)
            // Solid dot
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(ex, ey, dotRadius, dotPaint)
        }

        return bmp
    }
}