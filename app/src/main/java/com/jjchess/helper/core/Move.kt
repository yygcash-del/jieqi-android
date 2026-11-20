package com.jjchess.helper.core

/**
 * 一步走法：从 (fromCol,fromRow) 到 (toCol,toRow)。坐标为红方视角。
 * notation 用于悬浮窗显示，如 "红(7,4)-(6,4)"。
 */
data class Move(
    val fromCol: Int,
    val fromRow: Int,
    val toCol: Int,
    val toRow: Int,
) {
    fun notation(side: Side): String =
        "${side.cn}($fromCol,$fromRow)-($toCol,$toRow)"

    /** 转为 UCI 坐标，如 h3e3（列 letter a-i，行号 = 9-row） */
    fun toUci(): String {
        val fc = ('a'.code + fromCol).toChar()
        val tc = ('a'.code + toCol).toChar()
        val fr = 9 - fromRow
        val tr = 9 - toRow
        return "$fc$fr$tc$tr"
    }

    companion object {
        fun fromUci(uci: String): Move? {
            if (uci.length < 4) return null
            val fc = uci[0] - 'a'
            val fr = 9 - (uci[1] - '0')
            val tc = uci[2] - 'a'
            val tr = 9 - (uci[3] - '0')
            if (fc !in 0..8 || tc !in 0..8 || fr !in 0..9 || tr !in 0..9) return null
            return Move(fc, fr, tc, tr)
        }
    }
}

/** 候选走法及评分，用于悬浮窗第二行 */
data class Candidate(val move: Move, val side: Side, val score: Float) {
    fun format(): String = "${move.notation(side)} ${String.format("%.2f", score)}"
}
