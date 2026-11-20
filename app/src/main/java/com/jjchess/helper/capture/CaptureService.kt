package com.jjchess.helper.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import com.jjchess.helper.R
import com.jjchess.helper.core.Const
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * 无障碍服务：
 * 1) 截图：Android 11+(API30) 使用 takeScreenshot；
 * 2) 节点读取：尝试从界面节点直接读棋子信息（JJ象棋若暴露内容描述）；
 * 3) 模拟点击：dispatchGesture 点击起点/终点；
 * 4) 画面变化监听：onAccessibilityEvent + 轮询帧差触发重新识别。
 * 作为前台服务常驻通知。
 */
class CaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "CaptureService"
        const val CHANNEL_ID = "jjchess_capture"
        const val NOTIFY_ID = 1001

        @Volatile
        var instance: CaptureService? = null
            private set

        /** 是否已完成无障碍授权 */
        fun isEnabled(): Boolean = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var latestBitmap: Bitmap? = null
    private val generation = AtomicInteger(0)

    /** 截图回调 */
    interface ShotCallback { fun onShot(bitmap: Bitmap?) }
    private var shotCallback: ShotCallback? = null

    /** 画面变化回调（供总调度触发重新识别） */
    interface FrameListener { fun onFrame(bitmap: Bitmap, generation: Int) }
    private var frameListener: FrameListener? = null

    fun setFrameListener(l: FrameListener?) { frameListener = l }
    fun currentGeneration(): Int = generation.get()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 界面变化 -> 请求一帧截图（由总调度做稳定/帧差判断）
        if (event == null) return
        requestScreenshot()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "JJ揭棋助手", NotificationManager.IMPORTANCE_LOW)
            ch.description = "无障碍识别服务运行中"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)
        return builder
            .setContentTitle("JJ揭棋助手运行中")
            .setContentText("正在读取棋盘，可在通知栏停止")
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .build()
    }

    /** 请求截图，结果通过 shotCallback 与 frameListener 回调 */
    fun requestScreenshot(callback: ShotCallback? = null) {
        if (callback != null) shotCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    Executor { runnable -> mainHandler.post(runnable) },
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val hw = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                            hw.close()
                            if (bitmap != null) {
                                latestBitmap = bitmap
                                generation.incrementAndGet()
                                shotCallback?.onShot(bitmap)
                                frameListener?.onFrame(bitmap, generation.get())
                            }
                        }
                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failure: $errorCode")
                            shotCallback?.onShot(null)
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.w(TAG, "takeScreenshot exception", e)
                shotCallback?.onShot(null)
            }
        } else {
            shotCallback?.onShot(null)
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
