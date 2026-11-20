package com.jjchess.helper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.jjchess.helper.core.Piece
import com.jjchess.helper.core.PieceKind
import com.jjchess.helper.core.Side

/**
 * 手动摆盘棋盘视图：9列10行交叉点，点击选中后由外部面板设置棋子状态。
 * 与自动识别共用 BoardModel。
 */
class ManualBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 9x10 棋子数组，null 为空 */
    val pieces: Array<Array<Piece?>> = Array(9) { arrayOfNulls(10) }

    var selectedCol = -1
    var selectedRow = -1
    var onSelect: ((Int, Int) -> Unit)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63"); strokeWidth = 2f
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F"); style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER; textSize = 30f; textBold()
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121"); style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER; textSize = 30f; textBold()
    }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E"); style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER; textSize = 30f; textBold()
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D6A0"); style = Paint.Style.FILL
    }

    private fun Paint.textBold() { typeface = android.graphics.Typeface.DEFAULT_BOLD }

    private val padX: Float get() = width * 0.06f
    private val padY: Float get() = height * 0.05f
    private val stepX: Float get() = (width - 2 * padX) / 8f
    private val stepY: Float get() = (height - 2 * padY) / 9f
    private val radius: Float get() = minOf(stepX, stepY) * 0.42f

    private fun cx(c: Int) = padX + c * stepX
    private fun cy(r: Int) = padY + r * stepY

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        // 网格线
        for (c in 0 until 9) canvas.drawLine(cx(c), cy(0), cx(c), cy(9), linePaint)
        for (r in 0 until 10) canvas.drawLine(cx(0), cy(r), cx(8), cy(r), linePaint)
        // 九宫斜线
        drawPalace(canvas, 3, 0, 5, 2)
        drawPalace(canvas, 3, 7, 5, 9)
        // 棋子
        for (c in 0 until 9) for (r in 0 until 10) {
            val p = pieces[c][r] ?: continue
            drawPiece(canvas, c, r, p)
        }
        // 选中框
        if (selectedCol >= 0 && selectedRow >= 0) {
            canvas.drawCircle(cx(selectedCol), cy(selectedRow), radius + 4, selectPaint)
        }
    }

    private fun drawPalace(canvas: Canvas, c1: Int, r1: Int, c2: Int, r2: Int) {
        canvas.drawLine(cx(c1), cy(r1), cx(c2), cy(r2), linePaint)
        canvas.drawLine(cx(c2), cy(r1), cx(c1), cy(r2), linePaint)
    }

    private fun drawPiece(canvas: Canvas, c: Int, r: Int, p: Piece) {
        val x = cx(c); val y = cy(r)
        val fill = if (p.isDark) darkPaint else if (p.side == Side.RED) redPaint else blackPaint
        canvas.drawCircle(x, y, radius, fill)
        val label = if (p.isDark) "?" else (p.kind?.charFor(p.side)?.toString() ?: "?")
        val textPaint = if (p.isDark) darkPaint else if (p.side == Side.RED) redPaint else blackPaint
        // 暗子文字用白，明子文字用深色
        val tp = Paint(textPaint).apply {
            color = if (p.isDark) Color.WHITE else if (p.side == Side.RED) Color.parseColor("#FFF1F1") else Color.WHITE
            textAlign = Paint.Align.CENTER; textSize = radius * 1.1f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(label, x, y + tp.textSize * 0.35f, tp)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        var bestC = -1; var bestR = -1; var best = Float.MAX_VALUE
        for (c in 0 until 9) for (r in 0 until 10) {
            val d = (cx(c) - event.x) * (cx(c) - event.x) + (cy(r) - event.y) * (cy(r) - event.y)
            if (d < best) { best = d; bestC = c; bestR = r }
        }
        if (bestC >= 0 && best < (maxOf(stepX, stepY) * 0.6f) * (maxOf(stepX, stepY) * 0.6f)) {
            selectedCol = bestC; selectedRow = bestR
            onSelect?.invoke(bestC, bestR)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun setPiece(c: Int, r: Int, p: Piece?) {
        pieces[c][r] = p
        invalidate()
    }
}
