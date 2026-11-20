package com.jjchess.helper.core

/**
 * 棋盘模型：9 列 x 10 行交叉点。
 * 坐标约定（红方视角）：col 0..8 从左到右，row 0..9 从上（黑方底线）到下（红方底线）。
 * 即 row=0 为黑方底线，row=9 为红方底线；红方在下、黑方在上，与截图一致。
 */
class BoardModel {

    /** 9 列 x 10 行，null 表示空点 */
    private val grid: Array<Array<Piece?>> = Array(COLS) { arrayOfNulls(ROWS) }

    /** 暗子池 */
    val pool = DarkPool()

    /** 上一手（用于检测画面变化/轮次），可为空 */
    var lastMove: Move? = null

    fun get(col: Int, row: Int): Piece? {
        if (!inBoard(col, row)) return null
        return grid[col][row]
    }

    fun set(col: Int, row: Int, piece: Piece?) {
        if (inBoard(col, row)) grid[col][row] = piece
    }

    fun clear() {
        for (c in 0 until COLS) for (r in 0 until ROWS) grid[c][r] = null
        pool.reset()
        lastMove = null
    }

    /** 统计信息，用于悬浮窗第一行 */
    fun stats(): BoardStats {
        var total = 0; var open = 0; var dark = 0; var red = 0; var black = 0
        for (c in 0 until COLS) for (r in 0 until ROWS) {
            val p = grid[c][r] ?: continue
            total++
            if (p.isOpen) open++ else dark++
            if (p.side == Side.RED) red++ else black++
        }
        return BoardStats(total, open, dark, red, black, pool.total(Side.RED), pool.total(Side.BLACK))
    }

    /** 统计每方已翻开明子的棋字（不含将帅），用于暗子池校验 */
    fun openKinds(): Map<Side, Map<PieceKind, Int>> {
        val red = mutableMapOf<PieceKind, Int>()
        val black = mutableMapOf<PieceKind, Int>()
        for (c in 0 until COLS) for (r in 0 until ROWS) {
            val p = grid[c][r] ?: continue
            if (!p.isOpen || p.kind == null || p.kind.isKing) continue
            val m = if (p.side == Side.RED) red else black
            m[p.kind] = (m[p.kind] ?: 0) + 1
        }
        return mapOf(Side.RED to red, Side.BLACK to black)
    }

    /** 生成当前局面的全明 FEN（暗子身份由调用方在采样时随机填入） */
    fun toFen(darkAssignments: Map<Pair<Int, Int>, PieceKind>, sideToMove: Side): String {
        val rows = mutableListOf<String>()
        for (r in 0 until ROWS) {
            var empty = 0
            val sb = StringBuilder()
            for (c in 0 until COLS) {
                val p = grid[c][r]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val kind = if (p.isOpen) p.kind else darkAssignments[c to r]
                    if (kind == null) {
                        // 未分配身份的暗子按兵/卒处理，避免 FEN 非法
                        sb.append(if (p.side == Side.RED) PieceKind.PAWN.redChar else PieceKind.PAWN.blackChar)
                    } else {
                        sb.append(kind.charFor(p.side))
                    }
                }
            }
            if (empty > 0) sb.append(empty)
            rows.add(sb.toString())
        }
        // FEN 行序：从第0行(黑方底线)到第9行(红方底线)，与 Pikafish 一致
        val placement = rows.joinToString("/")
        val sideChar = if (sideToMove == Side.RED) "w" else "b"
        return "$placement $sideChar - - 0 1"
    }

    /** 找出某方所有棋子坐标 */
    fun piecesOf(side: Side): List<Triple<Int, Int, Piece>> {
        val list = mutableListOf<Triple<Int, Int, Piece>>()
        for (c in 0 until COLS) for (r in 0 until ROWS) {
            val p = grid[c][r] ?: continue
            if (p.side == side) list.add(Triple(c, r, p))
        }
        return list
    }

    companion object {
        const val COLS = 9
        const val ROWS = 10

        fun inBoard(col: Int, row: Int) = col in 0 until COLS && row in 0 until ROWS

        /** 是否在红方半场（red bottom: row >=5） */
        fun inRedHalf(row: Int) = row >= 5
        fun inBlackHalf(row: Int) = row <= 4
    }
}

data class BoardStats(
    val total: Int,
    val open: Int,
    val dark: Int,
    val red: Int,
    val black: Int,
    val poolRed: Int,
    val poolBlack: Int,
) {
    /** 悬浮窗第一行：有子32，明2暗30，红16黑16，池红15黑15 */
    fun format(): String = "有子$total，明${open}暗${dark}，红${red}黑${black}，池红${poolRed}黑${poolBlack}"
}
