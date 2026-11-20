package com.jjchess.helper.vision

import android.content.Context
import android.content.SharedPreferences
import com.jjchess.helper.core.Const

/**
 * 棋盘校准参数存储。保存 9x10 交叉点的归一化坐标（比例），
 * 未校准时使用 Const 中的截图默认值。
 */
class CalibrationStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences(Const.PREF_CALIB, Context.MODE_PRIVATE)

    data class Calib(
        val firstColX: Float,
        val firstRowY: Float,
        val colStep: Float,
        val rowStep: Float,
    )

    /** 读取校准参数；未校准返回 null */
    fun load(): Calib? {
        if (!sp.getBoolean(Const.KEY_CALIB_CUSTOM, false)) return null
        return Calib(
            sp.getFloat(Const.KEY_FIRST_COL_X, Const.BOARD_FIRST_COL_X_RATIO),
            sp.getFloat(Const.KEY_FIRST_ROW_Y, Const.BOARD_FIRST_ROW_Y_RATIO),
            sp.getFloat(Const.KEY_COL_STEP, Const.BOARD_COL_STEP_RATIO),
            sp.getFloat(Const.KEY_ROW_STEP, Const.BOARD_ROW_STEP_RATIO),
        )
    }

    /** 读取有效参数（校准优先，否则默认） */
    fun effective(): Calib = load() ?: Calib(
        Const.BOARD_FIRST_COL_X_RATIO,
        Const.BOARD_FIRST_ROW_Y_RATIO,
        Const.BOARD_COL_STEP_RATIO,
        Const.BOARD_ROW_STEP_RATIO,
    )

    fun save(calib: Calib) {
        sp.edit()
            .putBoolean(Const.KEY_CALIB_CUSTOM, true)
            .putFloat(Const.KEY_FIRST_COL_X, calib.firstColX)
            .putFloat(Const.KEY_FIRST_ROW_Y, calib.firstRowY)
            .putFloat(Const.KEY_COL_STEP, calib.colStep)
            .putFloat(Const.KEY_ROW_STEP, calib.rowStep)
            .apply()
    }

    fun clear() {
        sp.edit().putBoolean(Const.KEY_CALIB_CUSTOM, false).apply()
    }

    /**
     * 根据截图宽高计算全部 9x10 交叉点的像素坐标。
     * @return array[col][row] = floatArrayOf(x, y)
     */
    fun points(width: Int, height: Int): Array<Array<FloatArray>> {
        val c = effective()
        val array = Array(9) { Array(10) { FloatArray(2) } }
        for (col in 0 until 9) for (row in 0 until 10) {
            array[col][row][0] = (c.firstColX + col * c.colStep) * width
            array[col][row][1] = (c.firstRowY + row * c.rowStep) * height
        }
        return array
    }

    /** 单点坐标：(col,row) -> (px,py) */
    fun pointPixels(col: Int, row: Int, width: Int, height: Int): Pair<Float, Float> {
        val c = effective()
        return ((c.firstColX + col * c.colStep) * width) to ((c.firstRowY + row * c.rowStep) * height)
    }
}
