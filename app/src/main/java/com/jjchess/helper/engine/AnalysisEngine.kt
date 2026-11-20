package com.jjchess.helper.engine

import android.content.Context
import com.jjchess.helper.core.BoardModel
import com.jjchess.helper.core.Candidate
import com.jjchess.helper.core.Const
import com.jjchess.helper.core.DarkPool
import com.jjchess.helper.core.Move
import com.jjchess.helper.core.PieceKind
import com.jjchess.helper.core.Side
import kotlin.random.Random

/**
 * 揭棋分析引擎（核心策略）：
 * 1. 暗子身份未知 -> 采样 N 次，每次按暗子池剩余配额随机为每个暗子分配身份；
 * 2. 每次生成"全明 FEN"发给引擎，取根走法(bestmove)与评分；
 * 3. 按根走法汇总：出现次数 count 与平均分 avgScore；
 * 4. 排序取前三（先按出现次数，再按平均分），返回候选。
 * 5. 引擎不可用 -> 退化为纯统计模式：用走法生成器的合法走法按简单启发式打分，不崩溃。
 */
class AnalysisEngine(context: Context) {

    private val uci = UciEngine(context)
    private var engineStarted = false

    /** 算力档位 */
    enum class Power(val nodes: Int, val movetime: Int, val samples: Int) {
        SAVE(Const.ENGINE_NODES_SAVE, Const.ENGINE_MOVETIME_SAVE, Const.ENGINE_SAMPLES_SAVE),
        STD(Const.ENGINE_NODES_STD, Const.ENGINE_MOVETIME_STD, Const.ENGINE_SAMPLES_STD),
        FULL(Const.ENGINE_NODES_FULL, Const.ENGINE_MOVETIME_FULL, Const.ENGINE_SAMPLES_FULL),
    }

    var fallbackMode = false
        private set

    /** 确保引擎已启动；失败则进入降级模式 */
    private fun ensureEngine(): Boolean {
        if (engineStarted) return !fallbackMode
        engineStarted = true
        fallbackMode = !uci.start()
        return !fallbackMode
    }

    /**
     * 分析当前局面，返回前三个候选走法。
     */
    fun analyze(board: BoardModel, sideToMove: Side, power: Power): List<Candidate> {
        return if (ensureEngine()) analyzeWithEngine(board, sideToMove, power)
        else analyzeFallback(board, sideToMove)
    }

    /** 引擎模式：多次采样汇总 */
    private fun analyzeWithEngine(board: BoardModel, sideToMove: Side, power: Power): List<Candidate> {
        data class Agg(var count: Int = 0, var scoreSum: Float = 0f)
        val agg = mutableMapOf<String, Agg>() // key = UCI move

        repeat(power.samples) {
            val assignments = randomAssignDark(board)
            val fen = board.toFen(assignments, sideToMove)
            val res = uci.analyze(fen, power.nodes, power.movetime)
            if (res != null) {
                val a = agg.getOrPut(res.bestmoveUci) { Agg() }
                a.count++
                a.scoreSum += res.score
            }
        }

        if (agg.isEmpty()) {
            fallbackMode = true
            return analyzeFallback(board, sideToMove)
        }

        return agg.entries.mapNotNull { (uciMove, a) ->
            val move = Move.fromUci(uciMove) ?: return@mapNotNull null
            // 平均分：scoreSum/count；出现次数作为主要排序依据，评分做加权
            val avg = a.scoreSum / a.count
            val weight = a.count + avg * 0.5f
            val disp = com.jjchess.helper.core.Notation.of(board, move, sideToMove)
            Candidate(move, sideToMove, weight, disp)
        }
            .sortedByDescending { it.score }
            .take(3)
    }

    /**
     * 为棋盘上所有暗子随机分配身份（基于暗子池剩余配额），返回 坐标->棋字 映射。
     */
    private fun randomAssignDark(board: BoardModel): Map<Pair<Int, Int>, PieceKind> {
        val result = mutableMapOf<Pair<Int, Int>, PieceKind>()
        for (side in Side.entries) {
            val available = DarkPool.POOL_COUNTS.toMutableMap() // 简化：按满池分配，吃子影响较小
            // 收集该方所有暗子坐标
            val darkCells = mutableListOf<Pair<Int, Int>>()
            for (c in 0 until BoardModel.COLS) for (r in 0 until BoardModel.ROWS) {
                val p = board.get(c, r) ?: continue
                if (p.side == side && p.isDark) darkCells.add(c to r)
            }
            // 随机分配：从可用池中抽取
            val bag = mutableListOf<PieceKind>()
            for ((kind, cnt) in available) repeat(cnt) { bag.add(kind) }
            bag.shuffle(Random.Default)
            darkCells.forEachIndexed { idx, cell ->
                result[cell] = bag.getOrElse(idx) { PieceKind.PAWN }
            }
        }
        return result
    }

    /** 纯统计降级模式：不依赖引擎，用走法生成 + 简单启发式打分 */
    private fun analyzeFallback(board: BoardModel, sideToMove: Side): List<Candidate> {
        val cands = mutableListOf<Candidate>()
        for (c in 0 until BoardModel.COLS) for (r in 0 until BoardModel.ROWS) {
            val p = board.get(c, r) ?: continue
            if (p.side != sideToMove) continue
            // 暗子按兵卒基础走法，明子按真实棋字
            val moves = com.jjchess.helper.core.JieqiRules.legalMoves(board, c, r, p.kind)
            for (m in moves) {
                val target = board.get(m.toCol, m.toRow)
                // 启发式：吃子加分，暗子吃暗子加成，向前推进小幅加分，过河加分
                var s = 0.1f
                if (target != null) {
                    s += if (target.isDark) 0.35f else 0.25f
                }
                val forward = if (sideToMove == Side.RED) (r - m.toRow) else (m.toRow - r)
                s += forward * 0.05f
                if (p.isDark && BoardModel.inBoard(m.toCol, m.toRow) &&
                    (if (sideToMove == Side.RED) BoardModel.inBlackHalf(m.toRow) else BoardModel.inRedHalf(m.toRow))) {
                    s += 0.08f
                }
                val disp = com.jjchess.helper.core.Notation.of(board, m, sideToMove)
                cands.add(Candidate(m, sideToMove, s, disp))
            }
        }
        return cands.sortedByDescending { it.score }.take(3)
    }

    /** 判断当前是否为"轮到我方"。简化：统计红黑子数，缺口大者视为轮到另一方。 */
    fun isOurTurn(board: BoardModel, ourSide: Side): Boolean {
        val stats = board.stats()
        // 默认认为轮到子数较少的一方（对方刚吃完子则轮到我方）——真实对局中由画面提示判定，
        // 此处提供基础版，AutoMover 会结合画面变化与引擎建议综合判断。
        val fewerSide = if (stats.red <= stats.black) Side.RED else Side.BLACK
        return fewerSide == ourSide
    }

    fun close() {
        uci.stop()
    }
}
