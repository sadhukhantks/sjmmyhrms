package com.example.myhrms

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * A simple flow (wrapping) container: lays its children left-to-right and wraps
 * to the next line when the current row runs out of width. Used so the employee
 * buttons fill the available width and wrap to multiple rows instead of
 * scrolling horizontally when there are many employees.
 *
 * Children may carry [MarginLayoutParams] (e.g. LinearLayout.LayoutParams) and
 * their margins are honoured for spacing both within and between rows.
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams =
        MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val maxRow = widthSize - paddingLeft - paddingRight

        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0
        var widestRow = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            val w = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val h = child.measuredHeight + lp.topMargin + lp.bottomMargin

            if (rowWidth + w > maxRow && rowWidth > 0) {
                totalHeight += rowHeight
                widestRow = maxOf(widestRow, rowWidth)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += w
            rowHeight = maxOf(rowHeight, h)
        }
        totalHeight += rowHeight
        widestRow = maxOf(widestRow, rowWidth)

        val resolvedWidth =
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) widthSize
            else widestRow + paddingLeft + paddingRight

        setMeasuredDimension(
            resolvedWidth,
            resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxRow = (r - l) - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val lp = child.layoutParams as MarginLayoutParams
            val w = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val h = child.measuredHeight + lp.topMargin + lp.bottomMargin

            if (x + w > paddingLeft + maxRow && x > paddingLeft) {
                x = paddingLeft
                y += rowHeight
                rowHeight = 0
            }
            child.layout(
                x + lp.leftMargin,
                y + lp.topMargin,
                x + lp.leftMargin + child.measuredWidth,
                y + lp.topMargin + child.measuredHeight
            )
            x += w
            rowHeight = maxOf(rowHeight, h)
        }
    }
}
