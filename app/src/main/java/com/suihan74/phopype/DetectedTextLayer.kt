package com.suihan74.phopype

import android.content.Context
import android.graphics.*
import com.google.firebase.ml.vision.text.FirebaseVisionText

class DetectedTextLayer(
    private val context: Context,
    val data: FirebaseVisionText.Line
) : OverlayLayer.Item {

    override val rect = RectF(data.boundingBox)
    var isSelected: Boolean = false

    override fun draw(canvas: Canvas, paint: Paint) {
        val white = context.getColor(R.color.white)

        val textPaint = Paint().apply {
            color = if (isSelected) white else paint.color
            textSize = getFontSize()
        }

        canvas.drawRect(
            data.boundingBox!!,
            Paint().apply {
                color = paint.color
                if (isSelected) {
                    style = Paint.Style.FILL_AND_STROKE
                }
                else {
                    style = Paint.Style.STROKE
                }
                strokeWidth = 4f
            }
        )

        canvas.drawText(
            data.text,
            rect.left,
            rect.bottom,
            textPaint)
    }

    private fun getFontSize() : Float {
        val size = data.boundingBox?.height() ?: return 56f
        return size * 0.8f
    }

    override fun onSelected() {
        isSelected = true
    }

    override fun onUnselected() {
        isSelected = false
    }
}

