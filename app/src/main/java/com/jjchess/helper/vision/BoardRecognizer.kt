package com.jjchess.helper.vision

import android.content.Context
import android.graphics.Bitmap
import com.jjchess.helper.core.BoardModel
import com.jjchess.helper.core.Const
import com.jjchess.helper.core.Piece
import com.jjchess.helper.core.PieceKind
import com.jjchess.helper.core.Side
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 棋盘视觉识别器：从一帧截图识别 9x10 交叉点上的棋子。
 * 流程：交叉点定位 -> 有子判断(金橙占比) -> 中心色采样 -> 红黑/明暗判断 -> 棋字模板匹配。
 * 置信度不足时按"未知暗子"处理，避免误判。
 */
class BoardRecognizer(private val context: Context) {

    private val store = CalibrationStore(context)
    private val templates = TemplateMatcher(context)

    /** 单点识别结果 */
    data class CellResult(
        val piece: Piece?,
        val confidence: Float,
        val hasGold: Float,       // 金橙色像素占比
        val centerRgb: IntArray,
    )

    /**
     * 识别整盘。
     * @param bitmap 截图
     * @return 9x10 的识别结果数组
     */
    fun recognize(bitmap: Bitmap): Array<Array<CellResult>> {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = Array(9) { Array(10) { CellResult(null, 0f, 0f, intArrayOf()) } }
        for (col in 0 until 9) for (row in 0 until 10) {
            val (cx, cy) = store.pointPixels(col, row, w, h)
            result[col][row] = recognizeCell(pixels, w, h, cx.toInt(), cy.toInt())
        }
        return result
    }

    /** 将识别结果写入棋盘模型 */
    fun toBoard(cells: Array<Array<CellResult>>, board: BoardModel) {
        board.clear()
        for (col in 0 until 9) for (row in 0 until 10) {
            board.set(col, row, cells[col][row].piece)
        }
    }

    private fun recognizeCell(px: IntArray, w: Int, h: Int, cx: Int, cy: Int): CellResult {
        val radius = (Const.PIECE_SAMPLE_RADIUS_RATIO * w).toInt().coerceAtLeast(8)
        var gold = 0; var total = 0
        var rSum = 0; var gSum = 0; var bSum = 0; var cnt = 0
        var darkText = 0; var redText = 0
        for (dy in -radius..radius step 2) for (dx in -radius..radius step 2) {
            val x = cx + dx; val y = cy + dy
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            val color = px[y * w + x]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            total++
            // 金橙判定
            if (r > Const.GOLD_R_MIN && g > Const.GOLD_G_MIN && g < Const.GOLD_G_MAX &&
                b < Const.GOLD_B_MAX && (r - b) > Const.GOLD_RB_DIFF_MIN) gold++
            // 中心区域(半径一半)才统计文字色
            if (abs(dx) <= radius / 2 && abs(dy) <= radius / 2) {
                cnt++; rSum += r; gSum += g; bSum += b
                if (r < Const.BLACK_TEXT_MAX && g < Const.BLACK_TEXT_MAX && b < Const.BLACK_TEXT_MAX) darkText++
                if (r > Const.RED_TEXT_R_MIN && r < Const.RED_TEXT_R_MAX && g < Const.RED_TEXT_G_MAX &&
                    b < Const.RED_TEXT_B_MAX && (r - g) > Const.RED_TEXT_RG_DIFF_MIN) redText++
            }
        }
        val goldRatio = if (total > 0) gold.toFloat() / total else 0f
        val center = if (cnt > 0) intArrayOf(rSum / cnt, gSum / cnt, bSum / cnt) else intArrayOf(0, 0, 0)
        val textRatio = if (cnt > 0) (darkText + redText).toFloat() / cnt else 0f
        val redRatio = if (cnt > 0) redText.toFloat() / cnt else 0f

        // 有子判断
        if (goldRatio < Const.PIECE_OCCUPIED_MIN_GOLD) {
            return CellResult(null, 1f - goldRatio, goldRatio, center)
        }

        // 红黑判断
        val side = if (center[0] - center[2] > Const.SIDE_RED_RB_DIFF_MIN &&
            (center[0] + center[1] + center[2]) / 3 > Const.SIDE_RED_BRIGHT_MIN) Side.RED else Side.BLACK

        // 明暗判断：明子中心金色占比低、深色文字占比高
        val isOpen = goldRatio < Const.OPEN_GOLD_RATIO_MAX && textRatio > Const.OPEN_TEXT_RATIO_MIN

        val piece: Piece
        var confidence: Float
        if (isOpen) {
            // 棋字模板匹配
            val kind = templates.match(px, w, h, cx, cy, side, radius)
            if (kind != null) {
                piece = Piece.open(side, kind)
                confidence = 0.9f
            } else {
                // 模板为空或未匹配：按未知处理 -> 当作暗子
                piece = Piece.dark(side)
                confidence = 0.45f
            }
        } else {
            piece = Piece.dark(side)
            // 置信度：金橙占比越高越确信是暗子
            confidence = (goldRatio / 0.9f).coerceIn(0f, 1f)
        }
        return CellResult(piece, confidence, goldRatio, center)
    }

    /** 自动检测棋盘边界：返回归一化(firstColX, firstRowY, colStep, rowStep) */
    fun detectBoard(bitmap: Bitmap): CalibrationStore.Calib? {
        // 基于金橙色棋子的分布估计棋盘外接范围。
        // 统计所有金橙像素的行列直方图，取主要聚集区作为棋盘范围。
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val colHist = IntArray(w); val rowHist = IntArray(h)
        val step = Const.CAPTURE_DIFF_SAMPLE_STEP
        for (y in 0 until h step step) for (x in 0 until w step step) {
            val color = pixels[y * w + x]
            val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
            if (r > Const.GOLD_R_MIN && g > Const.GOLD_G_MIN && g < Const.GOLD_G_MAX &&
                b < Const.GOLD_B_MAX && (r - b) > Const.GOLD_RB_DIFF_MIN) {
                colHist[x]++; rowHist[y]++
            }
        }
        // 找到覆盖 90% 金橙像素的 x/y 区间
        val totalGold = colHist.sum()
        if (totalGold < 50) return null
        fun range(hist: IntArray, keep: Float): Pair<Int, Int> {
            val sorted = hist.indices.sortedByDescending { hist[it] }
            var acc = 0; var last = 0
            for ((i, idx) in sorted.withIndex()) { acc += hist[idx]; last = i; if (acc >= totalGold * keep) break }
            val chosen = sorted.take(last + 1)
            return (chosen.minOrNull() ?: 0) to (chosen.maxOrNull() ?: hist.size - 1)
        }
        val (xMin, xMax) = range(colHist, 0.92f)
        val (yMin, yMax) = range(rowHist, 0.92f)
        // 由外接范围反推 9 列 10 行的首点与步长
        val colStep = (xMax - xMin) / 8f
        val rowStep = (yMax - yMin) / 9f
        return CalibrationStore.Calib(
            xMin.toFloat() / w,
            yMin.toFloat() / h,
            colStep / w,
            rowStep / h,
        )
    }

    /** 供校准界面画预览用：判断单点是否异常（置信度低） */
    fun isCellAbnormal(c: CellResult): Boolean = c.confidence < 0.5f
}
