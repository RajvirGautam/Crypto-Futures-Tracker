package com.rajvir.FuturesTracker

import android.graphics.*

object SparklineRenderer {

    fun render(data: List<Float>, positive: Boolean): Bitmap {
        val width = 800
        val height = 300
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Make the line have slightly rounded joints for a smoother look
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (positive) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val max = data.maxOrNull() ?: 1f
        val min = data.minOrNull() ?: 0f

        val path = Path()
        data.forEachIndexed { i, v ->
            val x = i * (width / (data.size - 1f))
            val y = height - ((v - min) / (max - min + 0.01f)) * height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, paint)
        return bmp
    }
}