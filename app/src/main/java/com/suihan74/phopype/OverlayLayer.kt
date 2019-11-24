package com.suihan74.phopype

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayLayer : View {
    interface Item {
        fun draw(canvas: Canvas, paint: Paint)
    }

    private val paint = Paint()
    private val items = ArrayList<Item>()

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        context.obtainStyledAttributes(attrs, R.styleable.OverlayLayer).run {
            val foregroundColor = getColor(R.styleable.OverlayLayer_foregroundColor, Color.BLACK)
            paint.color = foregroundColor
            recycle()
        }

    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas != null) {
            items.forEach { it.draw(canvas, paint) }
        }
    }

    fun add(item: Item) = synchronized(this) {
        items.add(item)
    }

    fun remove(item: Item) = synchronized(this) {
        items.remove(item)
    }

    fun clear() = synchronized(this) {
        items.clear()
    }
}
