package com.jjchess.helper.core

/**
 * 暗子池：记录每一方"尚未翻开"的暗子身份分布。
 * 揭棋规则：帅/将开局为明子固定不动；其余 15 子为暗子，身份从池中随机确定。
 *
 * 池构成（每方 15 暗子）：车2 马2 炮2 相2 士2 兵5
 * 说明：用户规格描述为"车马炮相士兵各二加明帅"，但那样每方仅 13 子，
 *       与揭棋标准每方 16 子（1 明将 + 15 暗子）不符。此处按标准揭棋取兵/卒各 5，
 *       保证暗子总数 15 与开局检测一致；各类数量集中在 POOL_COUNTS，可按需调整。
 *
 * 翻子/吃子时从池扣减；当棋盘翻出的明子数超过池配额时，判定为冲突，
 * 触发"重新校准/手动摆盘"提示。
 */
class DarkPool {

    /** 每一方各类暗子的剩余数量 */
    private val remaining: MutableMap<Side, MutableMap<PieceKind, Int>> = mutableMapOf()

    init {
        reset()
    }

    /** 重置为开局满池状态 */
    fun reset() {
        remaining.clear()
        for (side in Side.entries) {
            remaining[side] = POOL_COUNTS.toMutableMap()
        }
    }

    /** 从池中扣减一个指定身份的暗子（翻子时调用）。返回是否扣减成功 */
    fun consume(side: Side, kind: PieceKind): Boolean {
        val map = remaining[side] ?: return false
        val cur = map[kind] ?: 0
        if (cur <= 0) return false
        map[kind] = cur - 1
        return true
    }

    /** 池中某类暗子剩余数量 */
    fun count(side: Side, kind: PieceKind): Int = remaining[side]?.get(kind) ?: 0

    /** 某方暗子剩余总数 */
    fun total(side: Side): Int = remaining[side]?.values?.sum() ?: 0

    /**
     * 校验当前棋盘上已翻开的明子是否超出池配额。
     * @param openKinds 每方已翻开明子的棋字统计
     * @return 冲突描述；null 表示无冲突
     */
    fun validate(openKinds: Map<Side, Map<PieceKind, Int>>): String? {
        for (side in Side.entries) {
            val opened = openKinds[side] ?: continue
            for ((kind, openedCount) in opened) {
                // 帅/将不占暗子池
                if (kind.isKing) continue
                val availableAtStart = POOL_COUNTS[kind] ?: 0
                if (openedCount > availableAtStart) {
                    return "${side.cn}方已翻开 ${openedCount} 个${kind.charFor(side)}，超过池中上限 ${availableAtStart}"
                }
            }
        }
        return null
    }

    companion object {
        /** 每方暗子池配额（合计 15） */
        val POOL_COUNTS: Map<PieceKind, Int> = mapOf(
            PieceKind.ROOK to 2,
            PieceKind.HORSE to 2,
            PieceKind.CANNON to 2,
            PieceKind.ELEPHANT to 2,
            PieceKind.ADVISOR to 2,
            PieceKind.PAWN to 5,
        )
    }
}
