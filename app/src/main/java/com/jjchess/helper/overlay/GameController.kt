package com.jjchess.helper.overlay

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jjchess.helper.capture.CaptureService
import com.jjchess.helper.core.BoardModel
import com.jjchess.helper.core.Candidate
import com.jjchess.helper.core.Const
import com.jjchess.helper.core.Move
import com.jjchess.helper.core.Side
import com.jjchess.helper.engine.AnalysisEngine
import com.jjchess.helper.vision.BoardRecognizer
import com.jjchess.helper.vision.CalibrationStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 总调度器（应用大脑）：
 * - 串行抓帧 -> 识别 -> 分析 -> (可选)自动落子。
 * - 用"棋盘指纹"判断局面是否变化：同一局面只分析/落子一次，避免重复点击。
 * - 用"识别到的棋子数"判断帧是否有效，规避截图失败/被遮挡。
 * - 诊断信息直接推送到悬浮窗统计行，便于用户直观看到状态。
 */
class GameController(private val context: Context) {

    companion object {
        private const val TAG = "GameController"
        private const val MIN_VALID_PIECES = 12 // 一盘揭棋至少 32 子；低于此值视为无效帧
    }

    private val recognizer = BoardRecognizer(context)
    private val calib = CalibrationStore(context)
    private val engine = AnalysisEngine(context)
    private val board = BoardModel()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 自动辅助总开关（开启才会识别和分析） */
    @Volatile
    var autoAssist = false

    /** 自动落子开关（关掉只显示建议） */
    @Volatile
    var autoMove = false

    /** 算力档位 */
    @Volatile
    var power = AnalysisEngine.Power.STD

    /** 我方执棋颜色 */
    @Volatile
    var ourSide = Side.RED

    /** 最近一次候选（供悬浮窗显示） */
    @Volatile
    var candidates: List<Candidate> = emptyList()
        private set

    @Volatile
    var statsText: String = ""
        private set

    /** 状态回调给悬浮窗 */
    interface StatusListener {
        fun onUpdate(stats: String, candidates: List<Candidate>, fallback: Boolean)
        fun onToast(msg: String)
    }
    @Volatile
    var listener: StatusListener? = null

    /** 工作线程控制 */
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val clickFailCount = AtomicInteger(0)
    private val shotFailCount = AtomicInteger(0)

    /** 上一局面指纹：相同局面不重复分析/落子 */
    @Volatile
    private var lastFingerprint = ""

    /** 诊断状态（推送到悬浮窗） */
    @Volatile
    private var diagnostic = ""

    /** 启动调度循环 */
    fun start() {
        if (running.getAndSet(true)) return
        worker = Thread {
            Log.d(TAG, "调度线程启动")
            while (running.get()) {
                try {
                    tick()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Throwable) {
                    Log.w(TAG, "tick异常", e)
                    Thread.sleep(300)
                }
            }
        }.apply { isDaemon = true; name = "jjchess-worker" }
        worker?.start()
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
        engine.close()
    }

    /** 每个循环：抓帧 -> 识别 -> 分析 -> 落子 */
    private fun tick() {
        val svc = CaptureService.instance
        if (svc == null) {
            postDiagnostic("等待无障碍服务")
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        if (!autoAssist) {
            postDiagnostic("已开启辅助后开始识别")
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 1) 抓一帧（串行入口，内部带最小间隔/防重入/重试）
        val shot = svc.captureOnce()
        if (shot == null) {
            val n = shotFailCount.incrementAndGet()
            postDiagnostic("截图失败($n)·检查无障碍/录屏权限")
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 2) 识别
        val cells = recognizer.recognize(shot)
        recognizer.toBoard(cells, board)
        val stats = board.stats()
        if (stats.total < MIN_VALID_PIECES) {
            // 棋子太少：多半是被弹窗/其他界面遮挡，或截图不是棋盘
            postDiagnostic("未识别到棋盘(${stats.total}子)·请在JJ象棋对局中")
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }
        shotFailCount.set(0)

        // 3) 局面指纹：棋子未变化则跳过重复分析/落子
        val fp = fingerprint()
        if (fp == lastFingerprint) {
            // 局面没变，保持上次显示，不重复分析
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 4) 暗子池冲突检测
        val conflict = board.pool.validate(board.openKinds())
        if (conflict != null) {
            statsText = stats.format()
            postDiagnostic("识别冲突:$conflict")
            mainHandler.post { listener?.onToast("识别冲突：$conflict") }
            lastFingerprint = fp
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 5) 分析（引擎或降级统计）
        val mode = if (engine.fallbackMode) "统计模式" else "引擎模式"
        diagnostic = "已识别${stats.total}子·$mode"
        statsText = stats.format() + " · " + diagnostic
        candidates = engine.analyze(board, ourSide, power)
        mainHandler.post {
            listener?.onUpdate(statsText, candidates, engine.fallbackMode)
        }

        // 6) 局面变化后，允许落子一次
        lastFingerprint = fp
        if (autoMove && candidates.isNotEmpty()) {
            performAutoMove(svc, candidates.first().move)
        }
    }

    /** 棋盘指纹：所有格子棋子状态的字符串表示 */
    private fun fingerprint(): String {
        val sb = StringBuilder(90)
        for (c in 0 until BoardModel.COLS) for (r in 0 until BoardModel.ROWS) {
            val p = board.get(c, r)
            if (p == null) sb.append('.')
            else {
                sb.append(if (p.side == Side.RED) 'R' else 'B')
                sb.append(if (p.isOpen) (p.kind?.name?.first() ?: '?') else 'd')
            }
        }
        return sb.toString()
    }

    private fun postDiagnostic(text: String) {
        diagnostic = text
        // 诊断时清空旧统计，避免文本越拼越长；识别成功后会被正式统计覆盖
        mainHandler.post {
            listener?.onUpdate(text, emptyList(), engine.fallbackMode)
        }
    }

    /** 执行自动落子：点起点 -> 延时 -> 点终点 */
    private fun performAutoMove(svc: CaptureService, move: Move) {
        val pts = calib.points(screenWidth, screenHeight)
        val (sx, sy) = pts[move.fromCol][move.fromRow][0] to pts[move.fromCol][move.fromRow][1]
        val (ex, ey) = pts[move.toCol][move.toRow][0] to pts[move.toCol][move.toRow][1]

        var attempt = 0
        var success = false
        while (attempt <= Const.MOVE_MAX_RETRY && !success) {
            attempt++
            val a = svc.click(sx, sy, Const.MOVE_TAP_START_DELAY_MS)
            val b = svc.click(ex, ey, Const.MOVE_TAP_END_DELAY_MS)
            success = a && b
            if (!success) {
                clickFailCount.incrementAndGet()
                Thread.sleep(150)
            }
        }
        if (success) {
            clickFailCount.set(0)
            // 落子后等待画面稳定，进入下一轮识别
            Thread.sleep(Const.MOVE_SETTLE_WAIT_MS)
        } else if (clickFailCount.get() >= Const.MOVE_MAX_RETRY + 1) {
            autoMove = false
            mainHandler.post { listener?.onToast(context.getString(com.jjchess.helper.R.string.toast_click_failed)) }
        }
    }

    /** 屏幕宽高（由 OverlayService 启动时注入） */
    @Volatile
    var screenWidth: Int = 1080
    @Volatile
    var screenHeight: Int = 2400

    /** 手动摆盘：把外部摆好的棋盘注入当前局面，并重新触发一轮分析 */
    fun applyManualBoard(newBoard: BoardModel) {
        board.clear()
        for (c in 0 until BoardModel.COLS) for (r in 0 until BoardModel.ROWS) {
            board.set(c, r, newBoard.get(c, r))
        }
        refreshAfterManual()
    }

    /** 手动摆盘后重新触发一轮 */
    fun refreshAfterManual() {
        lastFingerprint = ""
        statsText = ""
    }
}
