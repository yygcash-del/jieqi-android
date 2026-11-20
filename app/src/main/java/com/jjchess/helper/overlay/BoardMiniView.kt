package com.jjchess.helper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jjchess.helper.core.BoardModel
import com.jjchess.helper.core.BoardSnapshot
import com.jjchess.helper.core.Piece
import com.jjchess.helper.core.Side

/**
 * 悬浮窗里的棋盘缩略图：画 9x10 交叉点棋盘 + 棋子（明子写字、暗子画花纹环），
 * 并用高亮圈标出推荐走法的起点与终点。参照电脑版辅助的棋盘可视化样式。
 */
class BoardMiniView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var snapshot: BoardSnapshot? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 70, 45); strokeWidth = 1.5f
    }
    private val darkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 190, 120) }
    private val darkRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 120, 40); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val redText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 30, 30); textAlign = Paint.Align.CENTER; textSize = 20f; isFakeBoldText = true
    }
    private val blackText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 20, 20); textAlign = Paint.Align.CENTER; textSize = 20f; isFakeBoldText = true
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 220, 120); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }

    fun setSnapshot(s: BoardSnapshot?) {
        snapshot = s
        postInvalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val pad = 14f
        val w = width - pad * 2
        val h = height - pad * 2
        if (w <= 0 || h <= 0) return
        val dx = w / 8f  // 9 列 -> 8 段
        val dy = h / 9f  // 10 行 -> 9 段

        fun px(col: Int) = pad + col * dx
        fun py(row: Int) = pad + row * dy

        // 棋盘网格（简版：横线 + 竖线，九宫斜线）
        for (r in 0 until 10) c.drawLine(px(0), py(r), px(8), py(r), linePaint)
        for (col in 0 until 9) {
            if (col == 0 || col == 8) c.drawLine(px(col), py(0), px(col), py(9), linePaint)
            else {
                c.drawLine(px(col), py(0), px(col), py(4), linePaint)
                c.drawLine(px(col), py(5), px(col), py(9), linePaint)
            }
        }
        // 九宫斜线（红上：row0-2 与 row7-9）
        c.drawLine(px(3), py(0), px(5), py(2), linePaint)
        c.drawLine(px(5), py(0), px(3), py(2), linePaint)
        c.drawLine(px(3), py(7), px(5), py(9), linePaint)
        c.drawLine(px(5), py(7), px(3), py(9), linePaint)

        val rPiece = Math.min(dx, dy) * 0.42f
        val snap = snapshot ?: return
        for (col in 0 until BoardModel.COLS) for (row in 0 until BoardModel.ROWS) {
            val p = snap.cells[col][row] ?: continue
            drawPiece(c, px(col), py(row), rPiece, p)
        }
        // 推荐走法高亮
        snap.best?.let { m ->
            c.drawCircle(px(m.fromCol), py(m.fromRow), rPiece + 3f, markPaint)
            c.drawCircle(px(m.toCol), py(m.toRow), rPiece + 3f, markPaint)
        }
    }

    private fun drawPiece(c: Canvas, x: Float, y: Float, r: Float, p: Piece) {
        c.drawCircle(x, y, r, darkFill)
        c.drawCircle(x, y, r, darkRing)
        if (p.isOpen && p.kind != null) {
            val tp = if (p.side == Side.RED) redText else blackText
            val baseline = y - (tp.descent() + tp.ascent()) / 2f
            c.drawText(p.kind.charFor(p.side).toString(), x, baseline, tp)
        } else {
            // 暗子：中心画一个小菱形花纹
            c.drawCircle(x, y, r * 0.32f, darkRing)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 近似正方形
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.12f).toInt())
    }
}
