package ca.fuwafuwa.gaku.Windows.Views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import ca.fuwafuwa.gaku.Windows.Data.DisplayData
import ca.fuwafuwa.gaku.Windows.Data.ISquareChar
import ca.fuwafuwa.gaku.Windows.Interfaces.IRecalculateKanjiViews
import ca.fuwafuwa.gaku.Windows.Interfaces.ISearchPerformer
import ca.fuwafuwa.gaku.Windows.WindowCoordinator
import java.util.*


/**
 * Created by 0xbad1d3a5 on 5/5/2016.
 */
class KanjiGridView : SquareGridView, IRecalculateKanjiViews
{
    private lateinit var mWindowCoordinator: WindowCoordinator
    private lateinit var mSearchPerformer: ISearchPerformer
    private lateinit var mDisplayData: DisplayData

    private var mScrollValue: Int = 0

    private val mKanjiCellSize = squareCellSize

    var offset: Int = 0
        private set

    val kanjiViewList: List<KanjiCharacterView>
        get()
        {
            val count = childCount
            val kanjiViewList = ArrayList<KanjiCharacterView>()

            for (i in 0 until count)
            {
                kanjiViewList.add(getChildAt(i) as KanjiCharacterView)
            }

            return kanjiViewList
        }

    constructor(context: Context) : super(context)
    {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    {
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    {
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    {
    }

    fun setDependencies(windowCoordinator: WindowCoordinator, searchPerformer: ISearchPerformer)
    {
        mWindowCoordinator = windowCoordinator
        mSearchPerformer = searchPerformer
    }

    fun setText(displayData: DisplayData)
    {
        mDisplayData = displayData
        offset = 0

        ensureViews()
    }

    fun getText() : String
    {
        return mDisplayData.text
    }

    fun clearText()
    {
        removeAllViews()
        postInvalidate()
    }

    fun clearSelection()
    {
        unhighlightAll()
    }

    fun unhighlightAll(squareCharToExclude: ISquareChar)
    {
        for (k in kanjiViewList)
        {
            if (k.getSquareChar() !== squareCharToExclude) k.unhighlight()
        }
    }

    fun unhighlightAll()
    {
        for (k in kanjiViewList)
        {
            k.unhighlight()
        }
    }

    fun scrollNext()
    {
        if (offset + maxSquares < mDisplayData.count)
        {
            offset += maxSquares
            mScrollValue = maxSquares
        }

        ensureViews()
    }

    fun scrollPrev()
    {
        if (offset - mScrollValue >= 0)
        {
            offset -= mScrollValue
        }
        else
        {
            offset = 0
        }

        ensureViews()
    }

    override fun recalculateKanjiViews()
    {
        mDisplayData.recomputeChars()

        ensureViews()
    }

    private fun ensureViews()
    {
        val numChars = mDisplayData.count - offset
        val currentChildCount = childCount

        // 1. If we need MORE views, add them
        if (numChars > currentChildCount)
        {
            addKanjiViews(numChars - currentChildCount)
        } 
        
        // 2. Iterate through all children. Bind data to needed ones, Hide the rest.
        // We do NOT remove views anymore, avoiding GC overhead.
        for (i in 0 until childCount) 
        {
            val kanjiView = getChildAt(i) as KanjiCharacterView
            
            if (i < numChars) {
                // We need this view. Make it visible and update data.
                if (kanjiView.visibility != View.VISIBLE) {
                    kanjiView.visibility = View.VISIBLE
                }
                
                // Get data based on offset
                val squareChar = mDisplayData.squareChars[offset + i]
                kanjiView.setText(squareChar)
                kanjiView.unhighlight()
            } else {
                // We don't need this view right now. Hide it.
                if (kanjiView.visibility != View.GONE) {
                    kanjiView.visibility = View.GONE
                }
            }
        }

        setItemCount(numChars)
        postInvalidate()
    }

    private fun addKanjiViews(count: Int)
    {
        for (i in 0 until count)
        {
            val kanjiView = KanjiCharacterView(context)
            kanjiView.setDependencies(mWindowCoordinator, mSearchPerformer)
            kanjiView.setCellSize(mKanjiCellSize)

            addView(kanjiView)
        }
    }

    companion object
    {
        private val TAG = KanjiGridView::class.java.name
    }
}




