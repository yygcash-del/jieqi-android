package com.jjchess.helper.core

import kotlin.math.abs

/**
 * 揭棋走法生成规则。
 * - 明子：按真实棋字的中国象棋走法。
 * - 暗子：按兵/卒基础走法（未过河只能向前一步；过河后可前后左右各一步，不能斜走）。
 *   注意：揭棋规则中暗子在未翻开前"按兵的走法"，但通常允许暗子向前走一步；
 *   过河后的暗子可横走。此处严格按用户规格实现。
 */
object JieqiRules {

    /**
     * 生成某坐标棋子的所有合法走法（不考虑将军/蹩马腿以外的全局校验，保证够用且稳定）。
     * @param board 当前棋盘
     * @param col 起点列
     * @param row 起点行
     * @param kind 该子的真实棋字；暗子传 null（按兵卒基础走法）
     */
    fun legalMoves(board: BoardModel, col: Int, row: Int, kind: PieceKind?): List<Move> {
        val piece = board.get(col, row) ?: return emptyList()
        val side = piece.side
        val result = mutableListOf<Move>()

        fun inside(c: Int, r: Int) = BoardModel.inBoard(c, r)
        fun canLand(c: Int, r: Int): Boolean {
            if (!inside(c, r)) return false
            val target = board.get(c, r) ?: return true // 空点可走
            return target.side != side // 可吃对方子（揭棋暗子可吃暗子）
        }
        fun add(c: Int, r: Int) {
            if (canLand(c, r)) result.add(Move(col, row, c, r))
        }

        if (kind == null) {
            // 暗子：兵卒基础走法
            pawnMoves(side, col, row, ::add)
            return result
        }

        when (kind) {
            PieceKind.KING -> kingMoves(side, col, row, ::add)
            PieceKind.ADVISOR -> advisorMoves(side, col, row, ::add)
            PieceKind.ELEPHANT -> elephantMoves(board, side, col, row, ::add)
            PieceKind.HORSE -> horseMoves(board, col, row, ::add)
            PieceKind.ROOK -> rookMoves(board, col, row, ::add)
            PieceKind.CANNON -> cannonMoves(board, col, row, ::add)
            PieceKind.PAWN -> pawnMoves(side, col, row, ::add)
        }
        return result
    }

    /** 兵/卒：红方 row 减小方向前进，黑方 row 增大方向前进；过河后可横走 */
    private fun pawnMoves(side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val forward = if (side == Side.RED) -1 else 1
        add(col, row + forward)
        val crossed = if (side == Side.RED) BoardModel.inBlackHalf(row) else BoardModel.inRedHalf(row)
        if (crossed) {
            add(col - 1, row)
            add(col + 1, row)
            // 部分揭棋玩法允许过河兵后退，此处按"横走"规格不加入后退
        }
    }

    /** 将/帅：九宫内直走一步 */
    private fun kingMoves(side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val minRow: Int = if (side == Side.RED) 7 else 0
        val maxRow: Int = if (side == Side.RED) 9 else 2
        val minCol = 3; val maxCol = 5
        if (col - 1 in minCol..maxCol) add(col - 1, row)
        if (col + 1 in minCol..maxCol) add(col + 1, row)
        if (row - 1 in minRow..maxRow) add(col, row - 1)
        if (row + 1 in minRow..maxRow) add(col, row + 1)
    }

    /** 仕/士：九宫内斜走一步 */
    private fun advisorMoves(side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val minRow: Int = if (side == Side.RED) 7 else 0
        val maxRow: Int = if (side == Side.RED) 9 else 2
        val minCol = 3; val maxCol = 5
        listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1).forEach { (dc, dr) ->
            val c = col + dc; val r = row + dr
            if (c in minCol..maxCol && r in minRow..maxRow) add(c, r)
        }
    }

    /** 相/象：田字走，不能过河，塞象眼则不可走 */
    private fun elephantMoves(board: BoardModel, side: Side, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val canCross = if (side == Side.RED) row - 2 >= 5 else row + 2 <= 4
        if (!canCross) return
        listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).forEach { (dc, dr) ->
            val c = col + dc; val r = row + dr
            if (BoardModel.inBoard(c, r)) {
                // 象眼 = 中间点 (col+dc/2, row+dr/2)
                val eye = board.get(col + dc / 2, row + dr / 2)
                if (eye == null) add(c, r)
            }
        }
    }

    /** 马：日字走，蹩马腿则不可走 */
    private fun horseMoves(board: BoardModel, col: Int, row: Int, add: (Int, Int) -> Unit) {
        // 每项: 目标偏移(dc,dr) + 马腿偏移(legDc,legDr)
        data class HorseStep(val dc: Int, val dr: Int, val lc: Int, val lr: Int)
        val steps = listOf(
            HorseStep(-2, -1, -1, 0), HorseStep(-2, 1, -1, 0),
            HorseStep(2, -1, 1, 0), HorseStep(2, 1, 1, 0),
            HorseStep(-1, -2, 0, -1), HorseStep(1, -2, 0, -1),
            HorseStep(-1, 2, 0, 1), HorseStep(1, 2, 0, 1),
        )
        steps.forEach { s ->
            val c = col + s.dc; val r = row + s.dr
            if (BoardModel.inBoard(c, r)) {
                if (board.get(col + s.lc, row + s.lr) == null) add(c, r)
            }
        }
    }

    /** 车：直线任意步，遇子停 */
    private fun rookMoves(board: BoardModel, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        dirs.forEach { (dc, dr) ->
            var c = col + dc; var r = row + dr
            while (BoardModel.inBoard(c, r)) {
                val t = board.get(c, r)
                if (t == null) {
                    add(c, r)
                } else {
                    add(c, r) // 可吃子
                    break
                }
                c += dc; r += dr
            }
        }
    }

    /** 炮：不吃子时如车；吃子需隔恰好一个炮架 */
    private fun cannonMoves(board: BoardModel, col: Int, row: Int, add: (Int, Int) -> Unit) {
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        dirs.forEach { (dc, dr) ->
            var c = col + dc; var r = row + dr
            var jumped = false
            while (BoardModel.inBoard(c, r)) {
                val t = board.get(c, r)
                if (!jumped) {
                    if (t == null) {
                        add(c, r)
                    } else {
                        jumped = true // 找到炮架
                    }
                } else {
                    if (t != null) {
                        add(c, r) // 隔一个炮架吃子
                        break
                    }
                }
                c += dc; r += dr
            }
        }
    }
}
