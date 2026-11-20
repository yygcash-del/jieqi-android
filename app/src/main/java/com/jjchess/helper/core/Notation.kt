package com.jjchess.helper.core

/**
 * 中国象棋中文着法记谱（揭棋版）。
 * 参考电脑版效果：暗子按"开局位置类型"命名，如「暗车1进1」「暗仕六进五」；
 * 明子按真实棋字命名，如「炮7进5」「马8进7」。
 *
 * 记谱格式：[前缀][棋子][起始纵线][进/退/平][目标]
 *  - 红方纵线用汉字 一二三四五六七八九，黑方用阿拉伯 1-9（传统习惯）。
 *  - 车/炮/兵/将等直线兵种：进退记步数，平记目标纵线。
 *  - 马/相/仕等斜行兵种：进退记目标纵线。
 */
object Notation {

    private val CN_NUM = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

    /** 生成一步着法的中文记谱。board 用走子前的局面判断起点棋子类型。 */
    fun of(board: BoardModel, move: Move, side: Side): String {
        val piece = board.get(move.fromCol, move.fromRow)
        val kind: PieceKind? = if (piece != null && piece.isOpen) piece.kind
        else JieqiRules.positionKind(side, move.fromCol, move.fromRow)

        val char = if (kind != null) kind.charFor(side) else "兵"
        val prefix = if (piece != null && piece.isDark) "暗" else ""

        // 纵线：红方在顶部时从右往左数(9-col)，否则从左往右(col+1)
        fun file(col: Int): String {
            val n = if (Const.BOARD_RED_ON_TOP) (if (side == Side.RED) 9 - col else col + 1)
                    else (if (side == Side.RED) col + 1 else 9 - col)
            return if (side == Side.RED) CN_NUM.getOrElse(n - 1) { "$n" } else "$n"
        }

        val fromFile = file(move.fromCol)
        val forward = if (side == Side.RED) Const.RED_FORWARD else Const.BLACK_FORWARD
        val deltaRow = move.toRow - move.fromRow
        val action: String
        val target: String
        when {
            move.toRow == move.fromRow -> {
                action = "平"; target = file(move.toCol)
            }
            (deltaRow * forward) > 0 -> {
                action = "进"
                target = if (kind == PieceKind.HORSE || kind == PieceKind.ELEPHANT || kind == PieceKind.ADVISOR)
                    file(move.toCol)   // 斜行兵种记目标纵线
                else stepText(side, kotlin.math.abs(deltaRow))
            }
            else -> {
                action = "退"
                target = if (kind == PieceKind.HORSE || kind == PieceKind.ELEPHANT || kind == PieceKind.ADVISOR)
                    file(move.toCol)
                else stepText(side, kotlin.math.abs(deltaRow))
            }
        }
        return "$prefix$char$fromFile$action$target"
    }

    private fun stepText(side: Side, steps: Int): String =
        if (side == Side.RED) CN_NUM.getOrElse(steps - 1) { "$steps" } else "$steps"
}
