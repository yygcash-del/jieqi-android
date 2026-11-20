package com.jjchess.helper.core

import kotlin.math.abs

/**
 * 揭棋走法生成规则（严格按揭棋规则实现）：
 * - 明子：按真实棋字的中国象棋走法（相/士可过河，这是揭棋特有规则）。
 * - 暗子：按其"所在开局位置"对应的棋子类型走第一步，走完即揭开。
 *   例如开局车位的暗子当车走、马位暗子当马走、兵位暗子当兵走。
 *   ——这是揭棋的核心，也是本辅助能给出"合法走法"的关键。
 *
 * 坐标约定：col 0..8 左->右；row 0 为棋谱黑方底线(截图里是红方帅所在的顶排)，
 * row 9 为红方底线(截图里黑方将所在的底排)。红黑归属由 Const.BOARD_RED_ON_TOP 决定。
 */
object JieqiRules {

    /**
     * 生成某坐标棋子的所有合法走法。
     * @param board 当前棋盘
     * @param col 起点列
     * @param row 起点行
     * @param kind 明子的真实棋字；暗子传 null（此时按开局位置推断类型）
     */
    fun legalMoves(board: BoardModel, col: Int, row: Int, kind: PieceKind?): List<Move> {
        val piece = board.get(col, row) ?: return emptyList()
        val side = piece.side
        val result = mutableListOf<Move>()

        fun inside(c: Int, r: Int) = BoardModel.inBoard(c, r)
        fun canLand(c: Int, r: Int): Boolean {
            if (!inside(c, r)) return false
            val target = board.get(c, r) ?: return true
            return target.side != side // 明暗子之间可互吃
        }
        fun add(c: Int, r: Int) {
            if (canLand(c, r)) result.add(Move(col, row, c, r))
        }

        // 暗子：按开局位置对应的棋子类型走；明子：按真实棋字
        val effective = if (piece.isOpen && kind != null) kind else positionKind(side, col, row)
        when (effective ?: PieceKind.PAWN) {
            PieceKind.KING -> kingMoves(side, col, row, ::add)
            PieceKind.ADVISOR -> advisorMoves(side, col, row, ::add) // 揭棋明士可过河
            PieceKind.ELEPHANT -> elephantMoves(side, col, row, ::add) // 揭棋明相可过河
            PieceKind.HORSE -> horseMoves(board, col, row, ::add)
            PieceKind.ROOK -> rookMoves(board, col, row, ::add)
            PieceKind.CANNON -> cannonMoves(board, col, row, ::add)
            PieceKind.PAWN -> pawnMoves(side, col, row, ::add)
        }
        return result
    }

    /**
     * 开局位置 -> 棋子类型映射（揭棋暗子按位置走的核心）。
     * 标准象棋开局阵型（某一方底线 row、炮线 row±2、兵线 row±3）：
     *   底线: 车马象士将士象马车 (col 0..8)
     *   炮线: col1、col7 为炮
     *   兵线: col0,2,4,6,8 为兵
     * 红方在上时底线 row=0（红帅顶排），黑方底线 row=9（黑将底排）。
     */
    fun positionKind(side: Side, col: Int, row: Int): PieceKind? {
        val baseRow = if (side == Side.RED) Const.BOARD_RED_BASE_ROW else Const.BOARD_BLACK_BASE_ROW
        val dir = if (baseRow == 0) 1 else -1 // 从己方底线向对方推进的方向
        val d = (row - baseRow) * dir        // 距己方底线的行数
        return when (d) {
            0 -> BACK_RANK.getOrNull(col)     // 车马象士将士象马车
            2 -> if (col == 1 || col == 7) PieceKind.CANNON else null
            3 -> if (col % 2 == 0) PieceKind.PAWN else null
            else -> null
        }
    }

    private val BACK_RANK = listOf(
        PieceKind.ROOK, PieceKind.HORSE, PieceKind.ELEPHANT, PieceKind.ADVISOR, PieceKind.KING,
        PieceKind.ADVISOR, PieceKind.ELEPHANT, PieceKind.HORSE, PieceKind.ROOK
    )

    /** 兵/卒：从己方底线向对方推进；红方在上则向下(row+1)，黑方在下则向上(row-1) */
    private fun pawnMoves(side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val forward = if (side == Side.RED) Const.RED_FORWARD else Const.BLACK_FORWARD
        add(col, row + forward)
        // 揭棋中兵过河后可横走
        val crossed = if (forward > 0) BoardModel.inBlackHalf(row) else BoardModel.inRedHalf(row)
        if (crossed) {
            add(col - 1, row)
            add(col + 1, row)
        }
    }

    /** 将/帅：九宫内直走一步（九宫行范围由 Const 决定，红上黑下） */
    private fun kingMoves(side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val (minRow, maxRow) = palaceRows(side)
        if (col - 1 in 3..5) add(col - 1, row)
        if (col + 1 in 3..5) add(col + 1, row)
        if (row - 1 in minRow..maxRow) add(col, row - 1)
        if (row + 1 in minRow..maxRow) add(col, row + 1)
    }

    /** 仕/士：九宫内斜走一步；揭棋里明士可过河，故放开行范围到全盘 */
    private fun advisorMoves(side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        // 揭棋明士/士可以过河，行不限制在九宫，仍限斜走一步且列在 3..5 附近放宽到全盘
        listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1).forEach { (dc, dr) ->
            val c = col + dc; val r = row + dr
            if (BoardModel.inBoard(c, r)) add(c, r)
        }
    }

    /** 相/象：田字走，塞象眼则不可走；揭棋里明相/象可过河 */
    private fun elephantMoves(board: BoardModel, side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).forEach { (dc, dr) ->
            val c = col + dc; val r = row + dr
            if (BoardModel.inBoard(c, r) && board.get(col + dc / 2, row + dr / 2) == null) add(c, r)
        }
    }

    /** 马：日字走，蹩马腿则不可走 */
    private fun horseMoves(board: BoardModel, col: Int, row: Int, add: (Int, Int) -> Unit) {
        data class HorseStep(val dc: Int, val dr: Int, val lc: Int, val lr: Int)
        val steps = listOf(
            HorseStep(-2, -1, -1, 0), HorseStep(-2, 1, -1, 0),
            HorseStep(2, -1, 1, 0), HorseStep(2, 1, 1, 0),
            HorseStep(-1, -2, 0, -1), HorseStep(1, -2, 0, -1),
            HorseStep(-1, 2, 0, 1), HorseStep(1, 2, 0, 1),
        )
        steps.forEach { s ->
            val c = col + s.dc; val r = row + s.dr
            if (BoardModel.inBoard(c, r) && board.get(col + s.lc, row + s.lr) == null) add(c, r)
        }
    }

    /** 车：直线任意步，遇子停（可吃） */
    private fun rookMoves(board: BoardModel, col: Int, row: Int, add: (Int, Int) -> Unit) {
        listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dc, dr) ->
            var c = col + dc; var r = row + dr
            while (BoardModel.inBoard(c, r)) {
                val t = board.get(c, r)
                add(c, r)
                if (t != null) break
                c += dc; r += dr
            }
        }
    }

    /** 炮：不吃子如车；吃子需隔恰好一个炮架 */
    private fun cannonMoves(board: BoardModel, col: Int, row: Int, add: (Int, Int) -> Unit) {
        listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dc, dr) ->
            var c = col + dc; var r = row + dr
            var jumped = false
            while (BoardModel.inBoard(c, r)) {
                val t = board.get(c, r)
                if (!jumped) {
                    if (t == null) add(c, r) else jumped = true
                } else {
                    if (t != null) { add(c, r); break }
                }
                c += dc; r += dr
            }
        }
    }

    private fun palaceRows(side: Side): Pair<Int, Int> =
        if (side == Side.RED) Const.RED_PALACE_ROW_MIN to Const.RED_PALACE_ROW_MAX
        else Const.BLACK_PALACE_ROW_MIN to Const.BLACK_PALACE_ROW_MAX
}
