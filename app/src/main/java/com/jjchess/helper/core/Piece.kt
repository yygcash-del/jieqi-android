package com.jjchess.helper.core

/**
 * 棋子：颜色(红/黑)、明子真实棋字(可为 null 表示暗子)、是否被翻开。
 * 揭棋(char) 里"暗子"指身份未公开的棋子，其真实棋字只有系统知道，
 * 在本辅助中始终以 null 表示，由引擎采样模块随机分配身份。
 */
data class Piece(
    val side: Side,
    val kind: PieceKind?,   // 明子的真实棋字；暗子为 null
    val isDark: Boolean,
) {
    val isOpen: Boolean get() = !isDark

    companion object {
        fun open(side: Side, kind: PieceKind) = Piece(side, kind, false)
        fun dark(side: Side) = Piece(side, null, true)
    }
}

enum class Side(val cn: String) {
    RED("红"), BLACK("黑");

    fun opposite(): Side = if (this == RED) BLACK else RED
}

/**
 * 中国象棋 14 种棋字（红黑字面不同，但规则一致）。
 * 红方使用汉字：帅仕相马车炮兵
 * 黑方使用汉字：将士象馬車砲卒
 */
enum class PieceKind(
    val redChar: Char,
    val blackChar: Char,
    val isAdvisorLike: Boolean = false, // 仕/士
    val isElephantLike: Boolean = false, // 相/象
    val isKing: Boolean = false,
    val isPawn: Boolean = false,
    val isRook: Boolean = false,
    val isCannon: Boolean = false,
    val isHorse: Boolean = false,
) {
    KING('帅', '将', isKing = true),
    ADVISOR('仕', '士', isAdvisorLike = true),
    ELEPHANT('相', '象', isElephantLike = true),
    HORSE('马', '馬', isHorse = true),
    ROOK('车', '車', isRook = true),
    CANNON('炮', '砲', isCannon = true),
    PAWN('兵', '卒', isPawn = true);

    fun charFor(side: Side): Char = if (side == Side.RED) redChar else blackChar

    companion object {
        /** 红方字面 -> 棋字 */
        fun fromRedChar(c: Char): PieceKind? = entries.firstOrNull { it.redChar == c }
        /** 黑方字面 -> 棋字 */
        fun fromBlackChar(c: Char): PieceKind? = entries.firstOrNull { it.blackChar == c }
    }
}
