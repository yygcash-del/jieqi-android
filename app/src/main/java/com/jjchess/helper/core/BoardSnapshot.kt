package com.jjchess.helper.core

/**
 * 棋盘快照（不可变），供悬浮窗棋盘缩略图绘制，避免跨线程访问 BoardModel。
 */
data class BoardSnapshot(
    val cells: Array<Array<Piece?>>,          // [col][row]
    val best: Move? = null,                    // 推荐走法（画箭头）
    val ourSide: Side = Side.RED,
) {
    companion object {
        fun from(board: BoardModel, best: Move?, ourSide: Side): BoardSnapshot {
            val arr = Array(BoardModel.COLS) { arrayOfNulls<Piece?>(BoardModel.ROWS) }
            for (c in 0 until BoardModel.COLS)
                for (r in 0 until BoardModel.ROWS)
                    arr[c][r] = board.get(c, r)
            return BoardSnapshot(arr, best, ourSide)
        }
    }
}
