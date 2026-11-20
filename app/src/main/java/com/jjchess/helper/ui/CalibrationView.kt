package com.jjchess.helper.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.jjchess.helper.core.Const
import com.jjchess.helper.vision.CalibrationStore

/**
 * 校准视图：显示棋盘截图，叠加 9x10 可拖动交叉点。
 * - 单个交叉点可拖动微调
 * - 预览模式下：正常点绿色，识别异常点红色
 */
class CalibrationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val store = CalibrationStore(context)
    private var bitmap: Bitmap? = null
    private var calib = store.effective()
    private var previewMode = false
    private var abnormal = Array(9) { BooleanArray(10) }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; textSize = 22f
    }

    /** 当前正在拖动的交叉点 */
    private var draggingCol = -1
    private var draggingRow = -1

    fun setBitmap(b: Bitmap?) {
        bitmap = b
        // 视图尺寸确定后按视图坐标初始化点阵（相对比例）
        invalidate()
    }

    fun setCalib(c: CalibrationStore.Calib) { calib = c; invalidate() }
    fun getCalib(): CalibrationStore.Calib = calib

    fun setPreview(abnormal: Array<BooleanArray>) {
        previewMode = true
        this.abnormal = abnormal
        invalidate()
    }

    fun clearPreview() { previewMode = false; invalidate() }

    /** 由外部设置异常点 */
    fun markAbnormal(col: Int, row: Int, bad: Boolean) { abnormal[col][row] = bad; previewMode = true }

    private fun pointPx(col: Int, row: Int): FloatArray {
        // 截图坐标 -> 视图坐标的缩放
        val bmp = bitmap
        val scaleX = if (bmp != null && bmp.width > 0) width.toFloat() / bmp.width else 1f
        val scaleY = if (bmp != null && bmp.height > 0) height.toFloat() / bmp.height else 1f
        return floatArrayOf(
            (calib.firstColX + col * calib.colStep) * (bmp?.width ?: width) * scaleX,
            (calib.firstRowY + row * calib.rowStep) * (bmp?.height ?: height) * scaleY,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        for (c in 0 until 9) for (r in 0 until 10) {
            val (x, y) = pointPx(c, r)
            val paint = if (previewMode && abnormal[c][r]) redPaint else pointPaint
            canvas.drawCircle(x, y, 10f, paint)
            canvas.drawText("$c,$r", x + 8, y - 8, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 找最近的交叉点
                var best = Float.MAX_VALUE
                for (c in 0 until 9) for (r in 0 until 10) {
                    val (x, y) = pointPx(c, r)
                    val d = (x - event.x) * (x - event.x) + (y - event.y) * (y - event.y)
                    if (d < best && d < 40f * 40f) {
                        best = d; draggingCol = c; draggingRow = r
                    }
                }
                return draggingCol >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingCol >= 0 && draggingRow >= 0) {
                    val bmp = bitmap
                    val scaleX = if (bmp != null && bmp.width > 0) width.toFloat() / bmp.width else 1f
                    val scaleY = if (bmp != null && bmp.height > 0) height.toFloat() / bmp.height else 1f
                    // 反算新的比例
                    val newXRatio = event.x / (scaleX * (bmp?.width ?: width))
                    val newYRatio = event.y / (scaleY * (bmp?.height ?: height))
                    // 调整 first + col*step：拖动单个点时，只更新该点对应的 first/step 组合
                    // 简化：把该点设为新的 first 参考，并按相邻步长重算
                    if (draggingCol == 0) calib = calib.copy(firstColX = newXRatio)
                    else calib = calib.copy(colStep = (newXRatio - calib.firstColX) / draggingCol)
                    if (draggingRow == 0) calib = calib.copy(firstRowY = newYRatio)
                    else calib = calib.copy(rowStep = (newYRatio - calib.firstRowY) / draggingRow)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                draggingCol = -1; draggingRow = -1
            }
        }
        return super.onTouchEvent(event)
    }
}
