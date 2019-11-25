package com.suihan74.phopype

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class OverlayLayer : View {
    interface Item {
        fun draw(canvas: Canvas, paint: Paint)
        val rect: RectF

        fun onSelected() {}
        fun onUnselected() {}
    }

    private val paint = Paint()
    private val items = ArrayList<Item>()
    private val selectedItems = ArrayList<Item>()

    val selected: List<Item>
        get() = selectedItems

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


    private var touchingItem: Item? = null

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        synchronized(items) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchingItem = items.firstOrNull {
                        it.rect.contains(event.x, event.y)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (touchingItem?.rect?.contains(event.x, event.y) != true) {
                        touchingItem = null
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (touchingItem?.rect?.contains(event.x, event.y) == true) {
                        val item = touchingItem!!
                        touchingItem = null

                        if (selectedItems.contains(item)) {
                            selectedItems.remove(item)
                            item.onUnselected()
                        }
                        else {
                            selectedItems.add(item)
                            item.onSelected()
                        }
                    }
                }

                else -> return false
            }
        }
        invalidate()
        return true
    }

    fun add(item: Item) = synchronized(this) {
        items.add(item)
    }

    fun remove(item: Item) = synchronized(this) {
        items.remove(item)
        selectedItems.remove(item)
    }

    fun clear() = synchronized(this) {
        items.clear()
        selectedItems.clear()
    }
}
