package ca.fuwafuwa.gaku.Windows.Views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

import ca.fuwafuwa.gaku.*

/**
 * Created by 0xbad1d3a5 on 5/5/2016.
 */
open class SquareGridView : ViewGroup
{
    protected var squareCellSize = 0
    protected var maxSquares = 0

    private var mItemCount = 0
    private var mRowLimit = 0
    private var mRows = 1

    constructor(context: Context) : super(context)
    {
        Init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    {
        Init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    {
        Init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    {
        Init(context)
    }

    private fun Init(context: Context)
    {
        squareCellSize = dpToPx(context, 37)
    }

    fun setCellSize(dp: Int)
    {
        squareCellSize = dpToPx(context, dp)
    }

    fun setItemCount(items: Int)
    {
        mItemCount = items
    }

    fun setRowLimit(rowLimit: Int)
    {
        mRowLimit = rowLimit
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    {
        val cellWidthSpec = View.MeasureSpec.makeMeasureSpec(squareCellSize, View.MeasureSpec.EXACTLY)
        val cellHeightSpec = View.MeasureSpec.makeMeasureSpec(squareCellSize, View.MeasureSpec.EXACTLY)

         val count = childCount
        var visibleCount = 0 // Track actual visible items

        for (index in 0 until count)
        {
            val child = getChildAt(index)
            if (child.visibility != View.GONE) {
                child.measure(cellWidthSpec, cellHeightSpec)
                visibleCount++
            }
        }

        // Use visibleCount instead of count for calculating rows
        val x = View.resolveSize(squareCellSize * visibleCount, widthMeasureSpec)
        
        // Check for 0 to avoid divide by zero if no items visible
        val columns = if (x / squareCellSize > 0) x / squareCellSize else 1
        
        mRows = Math.ceil(mItemCount.toDouble() / columns.toDouble()).toInt()
        mRows = if (mRows <= 0) 1 else mRows

        when (mRowLimit)
        {
            0 -> { mRows = if (mRows >= 4) 4 else mRows }
            1 -> { mRows = 1 }
            2 -> { mRows = if (mRows >= 8) 8 else mRows }
        }

        val y = View.resolveSize(squareCellSize * mRows, heightMeasureSpec)
        setMeasuredDimension(x, y)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int)
    {
        var columns = (r - l) / squareCellSize
        val xStart = (r - l - squareCellSize * columns) / 2
        if (columns < 0)
        {
            columns = 1
        }

         var rows = 1
        var x = xStart
        var y = 0
        var i = 0
        val count = childCount
        
        for (index in 0 until count)
        {
            val child = getChildAt(index)
            
            // ADD THIS CHECK: Skip layout for hidden views
            if (child.visibility == View.GONE) continue

            val w = child.measuredWidth
            val h = child.measuredHeight
            val left = x + (squareCellSize - w) / 2
            val top = y + (squareCellSize - h) / 2
            child.layout(left, top, left + w, top + h)
            if (i >= columns - 1)
            {
                // advance to next row
                i = 0
                x = xStart
                y += squareCellSize
                rows++
                if (rows > mRows)
                {
                    break
                }
            } else
            {
                i++
                x += squareCellSize
            }
        }

        maxSquares = columns * mRows
    }

    companion object
    {
        private val TAG = SquareGridView::class.java.name
    }
}




