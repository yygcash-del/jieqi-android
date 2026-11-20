package com.jjchess.helper.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jjchess.helper.core.Const
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 无障碍服务：
 * 1) 截图：Android 11+(API30) 使用 takeScreenshot；
 * 2) 节点读取：尝试从界面节点直接读棋子信息（JJ象棋若暴露内容描述）；
 * 3) 模拟点击：dispatchGesture 点击起点/终点；
 * 4) 画面变化：由 GameController 主动、串行轮询截图（captureOnce）。
 *
 * 重要：无障碍服务由系统管理生命周期并自动保活，不调用 startForeground。
 * Android 14+(API34) 起前台服务必须声明 foregroundServiceType，否则
 * startForeground 会抛异常导致服务连接失败（系统显示"此服务出现故障"）。
 * 常驻前台通知由合规声明 specialUse 类型的 OverlayService 承担。
 *
 * 关键：takeScreenshot 单一串行入口 + 最小间隔 + 防重入 + 重试。
 * typeAllMask 事件海量，若在 onAccessibilityEvent 里截图会与轮询线程并发，
 * 触发系统限流导致截图几乎全部失败（表现为棋盘永远识别不出棋子）。
 */
class CaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "CaptureService"

        @Volatile
        var instance: CaptureService? = null
            private set

        /** 是否已完成无障碍授权 */
        fun isEnabled(): Boolean = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)

    /** 截图串行化：防止并发调用 takeScreenshot 被系统限流 */
    private val shotInFlight = AtomicBoolean(false)
    private var lastShotMs = 0L

    fun currentGeneration(): Int = generation.get()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不在这里截图。截图统一由 GameController 轮询线程调用 captureOnce()，
        // 避免与事件回调并发触发 takeScreenshot 而被系统限流。
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * 同步抓取一帧（唯一截图入口）。带最小间隔、防重入、失败重试。
     * 应在工作线程调用——内部会 latch 等待截图回调。
     * @return 成功返回 bitmap（同时 generation+1），失败返回 null
     */
    fun captureOnce(): Bitmap? {
        if (!shotInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "captureOnce skipped: in flight")
            return null
        }
        try {
            var lastError = -1
            repeat(Const.SHOT_MAX_RETRY) { attempt ->
                // 保证两次截图之间的最小间隔，规避系统限流
                val wait = Const.SHOT_MIN_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastShotMs)
                if (wait > 0) SystemClock.sleep(wait)

                val bitmap = takeOneFrame { lastError = it }
                lastShotMs = SystemClock.elapsedRealtime()
                if (bitmap != null) {
                    generation.incrementAndGet()
                    return bitmap
                }
                Log.w(TAG, "captureOnce retry $attempt failed, err=$lastError")
            }
            return null
        } finally {
            shotInFlight.set(false)
        }
    }

    /** 执行一次 takeScreenshot 并同步等待回调；onError 回传错误码 */
    private fun takeOneFrame(onError: (Int) -> Unit): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Bitmap>(1)
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executor { runnable -> mainHandler.post(runnable) },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hw = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            hw.close()
                            holder[0] = bitmap
                        } catch (e: Throwable) {
                            Log.w(TAG, "bitmap convert failed", e)
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onError(errorCode)
                        latch.countDown()
                    }
                }
            )
            if (!latch.await(Const.SHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "takeScreenshot timeout")
                return null
            }
            return holder[0]
        } catch (e: Throwable) {
            Log.w(TAG, "takeScreenshot exception", e)
            return null
        }
    }

    /**
     * 读取当前窗口根节点，尝试提取带有棋子信息的内容描述。
     * JJ象棋若通过 contentDescription 暴露棋子（如"红车"），可用于更精准识别。
     * @return 文本列表；无可读节点返回空列表
     */
    fun readNodeTexts(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<String>()
        collectNodeTexts(root, out, 0)
        return out
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 12) return
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()
        val cls = node.className?.toString() ?: ""
        // 只收集ImageView/TextView上可能的棋子描述
        if ((cls.contains("ImageView") || cls.contains("TextView") || cls.contains("Button")) &&
            !desc.isNullOrBlank()) {
            out.add(desc)
        } else if (!text.isNullOrBlank() && text.length <= 4) {
            out.add(text)
        }
        for (i in 0 until node.childCount) {
            collectNodeTexts(node.getChild(i), out, depth + 1)
        }
    }

    /** 判断界面上是否存在棋子节点（优先用节点识别的标志） */
    fun hasReadablePieceNodes(): Boolean = readNodeTexts().isNotEmpty()

    /**
     * 模拟点击屏幕坐标 (x,y)。
     * @param delayMs 点击后的等待时间（在调用线程 sleep，需从后台线程调用）
     * @return 是否成功派发
     */
    fun click(x: Float, y: Float, delayMs: Long = Const.MOVE_TAP_GAP_MS): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, Const.MOVE_GESTURE_DURATION_MS
                )
            )
            .build()
        val done = java.util.concurrent.CountDownLatch(1)
        val result = booleanArrayOf(false)
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                result[0] = true; done.countDown()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result[0] = false; done.countDown()
            }
        }, mainHandler)
        if (!ok) return false
        try { done.await(1, java.util.concurrent.TimeUnit.SECONDS) } catch (e: InterruptedException) {}
        if (delayMs > 0) try { Thread.sleep(delayMs) } catch (e: InterruptedException) {}
        return result[0]
    }
}
