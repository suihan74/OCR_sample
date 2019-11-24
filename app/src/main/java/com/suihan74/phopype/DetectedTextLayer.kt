package com.suihan74.phopype

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.google.firebase.ml.vision.text.FirebaseVisionText

class DetectedTextLayer(private val data: FirebaseVisionText.TextBlock) : OverlayLayer.Item {
    val paint = Paint().apply {
        textSize = 56f
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        this.paint.color = paint.color
        val rect = RectF(data.boundingBox)

        canvas.drawText(
            data.text,
            rect.left,
            rect.bottom,
            this.paint)
    }
}

class BitmapLayer(
    private val bitmap: Bitmap,
    private val left: Float = 0f,
    private val top: Float = 0f
) : OverlayLayer.Item {
    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawBitmap(bitmap, left, top, paint)
    }
}
