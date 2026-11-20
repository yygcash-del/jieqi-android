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
import com.jjchess.helper.vision.FrameDiffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 总调度器（应用大脑）：
 * - 实时触发状态机：画面稳定 -> 识别 -> 分析 -> (可选)自动落子 -> 等待画面变化 -> 下一轮
 * - 识别中若画面变化，中断重来（generation 机制）
 * - 自动辅助开关 + 自动落子开关独立控制
 * - 通过回调把统计与候选走法推送给悬浮窗
 */
class GameController(private val context: Context) {

    companion object {
        private const val TAG = "GameController"
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
    private var lastBitmap: Bitmap? = null
    private val seenGeneration = AtomicInteger(-1)
    private val clickFailCount = AtomicInteger(0)

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
                    Thread.sleep(200)
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

    /** 每个循环：等待画面稳定 -> 抓帧 -> 识别 -> 分析 -> 落子 */
    private fun tick() {
        val svc = CaptureService.instance
        if (svc == null) {
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        if (!autoAssist) {
            // 关闭自动辅助：仅保持帧同步，不做识别
            lastBitmap = null
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 1) 抓取当前帧（通过 callback 同步等待）
        val shot = requestFrameSync(svc)
        if (shot == null) {
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 2) 稳定性判断：连续两帧几乎一致才认为稳定
        if (FrameDiffer.isChanging(lastBitmap, shot)) {
            lastBitmap = shot
            Thread.sleep(Const.CAPTURE_STABLE_MS)
            return
        }
        lastBitmap = shot
        val genAtStart = svc.currentGeneration()

        // 3) 已处理过这一代画面则跳过（避免重复识别同一帧）
        if (genAtStart == seenGeneration.get()) {
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
            return
        }

        // 4) 识别 + 分析（识别中若 generation 变化则中断重来）
        var retry = 0
        while (retry < Const.MAX_RECOGNIZE_RETRIES) {
            if (svc.currentGeneration() != genAtStart) return // 画面变了，本轮作废
            val cells = recognizer.recognize(shot)
            if (svc.currentGeneration() != genAtStart) return
            recognizer.toBoard(cells, board)

            // 暗子池冲突检测
            val conflict = board.pool.validate(board.openKinds())
            if (conflict != null) {
                mainHandler.post { listener?.onToast("识别冲突：$conflict，请重新校准或手动摆盘") }
                autoAssist = false
                return
            }

            statsText = board.stats().format()
            candidates = engine.analyze(board, ourSide, power)

            mainHandler.post {
                listener?.onUpdate(statsText, candidates, engine.fallbackMode)
            }
            break
        }
        seenGeneration.set(genAtStart)

        // 5) 自动落子
        if (autoMove && candidates.isNotEmpty()) {
            performAutoMove(svc, candidates.first().move)
        } else {
            Thread.sleep(Const.CAPTURE_POLL_INTERVAL_MS)
        }
    }

    /** 同步等待一帧截图 */
    private fun requestFrameSync(svc: CaptureService): Bitmap? {
        val latch = java.util.concurrent.CountDownLatch(1)
        val holder = arrayOfNulls<Bitmap>(1)
        svc.requestScreenshot(object : CaptureService.ShotCallback {
            override fun onShot(bitmap: Bitmap?) {
                holder[0] = bitmap
                latch.countDown()
            }
        })
        try {
            if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) return null
        } catch (e: InterruptedException) { return null }
        return holder[0]
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
            // 落子后等待画面变化，进入下一轮识别
            seenGeneration.set(svc.currentGeneration())
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
        seenGeneration.set(-1)
        lastBitmap = null
    }
}
