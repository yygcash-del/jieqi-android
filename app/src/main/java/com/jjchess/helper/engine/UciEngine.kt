package com.jjchess.helper.engine

import android.content.Context
import com.jjchess.helper.core.Const
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Pikafish UCI 引擎封装。
 * - 首次运行时把 assets/engine/pikafish（arm64 可执行文件）复制到应用私有目录并 chmod +x。
 * - 通过子进程的标准输入/输出进行 UCI 通信。
 * - 引擎缺失或启动失败时，isAvailable=false，调用方退化为纯统计模式。
 */
class UciEngine(private val context: Context) {

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var engineFile: File? = null

    val isAvailable: Boolean get() = process != null

    /**
     * 启动引擎。返回是否成功。
     */
    fun start(): Boolean {
        if (process != null) return true
        return try {
            val file = prepareBinary() ?: return false
            engineFile = file
            val pb = ProcessBuilder(file.absolutePath)
                .redirectErrorStream(false)
            val p = pb.start()
            process = p
            writer = OutputStreamWriter(p.outputStream, Charsets.UTF_8)
            reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
            // UCI 握手
            send("uci")
            // 读取直到 uciok（带超时保护：这里用简单循环，每行阻塞，进程正常会很快返回）
            val deadline = System.currentTimeMillis() + Const.ENGINE_STARTUP_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val line = reader?.readLine() ?: break
                if (line == "uciok") break
            }
            send("isready")
            while (System.currentTimeMillis() < deadline) {
                val line = reader?.readLine() ?: break
                if (line == "readyok") break
            }
            send("setoption name Threads value 1")
            true
        } catch (e: Throwable) {
            stop()
            false
        }
    }

    /** 复制 assets 中的引擎二进制到私有目录并赋可执行权限 */
    private fun prepareBinary(): File? {
        val out = File(context.filesDir, Const.ENGINE_ASSET_PATH.substringAfterLast('/'))
        try {
            if (!out.exists() || out.length() == 0L) {
                context.assets.open(Const.ENGINE_ASSET_PATH).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
            }
            if (out.length() < 10_000) return null // 明显不是有效二进制（占位/缺失）
            out.setExecutable(true, false)
            out.setReadable(true, false)
            return out
        } catch (e: Throwable) {
            return null
        }
    }

    private fun send(cmd: String) {
        try {
            writer?.write(cmd + "\n")
            writer?.flush()
        } catch (e: Throwable) {
        }
    }

    /**
     * 分析一个全明 FEN，返回 bestmove 的 UCI 字符串与得分。
     * @param fen 全明 FEN
     * @param nodes 节点数上限
     * @param movetime 思考时间(ms)
     */
    fun analyze(fen: String, nodes: Int, movetime: Int): AnalysisResult? {
        if (process == null) return null
        return try {
            send("position fen $fen")
            send("go movetime $movetime nodes $nodes")
            var bestmove: String? = null
            var score = 0f
            val deadline = System.currentTimeMillis() + Const.ENGINE_READ_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val line = reader?.readLine() ?: break
                if (line.startsWith("info") && "score cp" in line) {
                    score = parseScore(line)
                }
                if (line.startsWith("bestmove")) {
                    bestmove = line.split(" ").getOrNull(1)
                    break
                }
            }
            if (bestmove == null || bestmove == "(none)") null
            else AnalysisResult(bestmove!!, score)
        } catch (e: Throwable) {
            null
        }
    }

    /** 解析 info 行中的 score cp 或 score mate */
    private fun parseScore(line: String): Float {
        val cpIdx = line.indexOf("score cp")
        if (cpIdx >= 0) {
            val rest = line.substring(cpIdx + 8).trim()
            val num = rest.split(" ").firstOrNull()?.toFloatOrNull() ?: 0f
            return num / 100f // 厘兵 -> 兵
        }
        val mateIdx = line.indexOf("score mate")
        if (mateIdx >= 0) {
            val rest = line.substring(mateIdx + 10).trim()
            val m = rest.split(" ").firstOrNull()?.toIntOrNull() ?: 0
            return if (m > 0) 10f else -10f
        }
        return 0f
    }

    fun stop() {
        try { send("quit") } catch (e: Throwable) {}
        try { process?.waitFor(500, TimeUnit.MILLISECONDS) } catch (e: Throwable) {}
        try { process?.destroyForcibly() } catch (e: Throwable) {}
        process = null; writer = null; reader = null
    }
}

data class AnalysisResult(val bestmoveUci: String, val score: Float)
