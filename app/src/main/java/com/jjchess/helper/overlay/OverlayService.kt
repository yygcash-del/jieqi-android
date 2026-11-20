package com.jjchess.helper.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.jjchess.helper.R
import com.jjchess.helper.core.BoardSnapshot
import com.jjchess.helper.core.Candidate
import com.jjchess.helper.core.Const
import com.jjchess.helper.databinding.OverlayPanelBinding
import com.jjchess.helper.engine.AnalysisEngine

/**
 * 前台服务：承载悬浮窗与总调度器。
 * 悬浮窗显示在棋盘上下空白区域，可拖动、可折叠成小圆点，不抢焦点。
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "jjchess_overlay"
        const val NOTIFY_ID = 1002
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        @Volatile
        var instance: OverlayService? = null
            private set

        fun canDraw(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): OverlayService = this@OverlayService }

    private var wm: WindowManager? = null
    private var overlayView: View? = null
    private var binding: OverlayPanelBinding? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapsed = false

    lateinit var controller: GameController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        // Android 14+(API34) 起前台服务必须显式声明类型，否则 startForeground 抛异常
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFY_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFY_ID, buildNotification())
        }
        controller = GameController(this)
        val dm = resources.displayMetrics
        controller.screenWidth = dm.widthPixels
        controller.screenHeight = dm.heightPixels
        controller.listener = object : GameController.StatusListener {
            override fun onUpdate(stats: String, candidates: List<Candidate>, fallback: Boolean) {
                binding?.tvStats?.text = stats
                binding?.tvCandidates?.text = formatCandidates(candidates) +
                    if (fallback) " [统计模式]" else ""
            }
            override fun onBoard(snapshot: BoardSnapshot) {
                binding?.boardMini?.setSnapshot(snapshot)
            }
            override fun onToast(msg: String) {
                Toast.makeText(this@OverlayService, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatCandidates(list: List<Candidate>): String =
        list.joinToString("\n") { it.format() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { removeOverlay(); controller.stop(); stopForeground(true); stopSelf(); return START_NOT_STICKY }
            else -> { addOverlayIfNeeded(); controller.start() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun addOverlayIfNeeded() {
        if (overlayView != null || !canDraw(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = OverlayPanelBinding.inflate(LayoutInflater.from(this))
        binding = b
        overlayView = b.root

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels * Const.OVERLAY_DEFAULT_X_RATIO).toInt()
            y = (resources.displayMetrics.heightPixels * Const.OVERLAY_DEFAULT_Y_RATIO).toInt()
        }

        setupInteractions(b)
        try { wm?.addView(overlayView, params) } catch (e: Exception) {
            Log.e("OverlayService", "addView failed", e)
        }
    }

    private fun setupInteractions(b: OverlayPanelBinding) {
        // 拖动：只处理标题栏区域的触摸，避免误触开关
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        b.tvTitle.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x; initialY = p.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    try { wm?.updateViewLayout(overlayView, p) } catch (e: Exception) {}
                    true
                }
                else -> false
            }
        }

        // 折叠/展开
        b.btnCollapse.setOnClickListener {
            collapsed = !collapsed
            b.contentContainer.visibility = if (collapsed) View.GONE else View.VISIBLE
            b.btnCollapse.text = if (collapsed) "+" else getString(R.string.overlay_collapse)
        }

        // 两个开关
        b.toggleAssist.setOnCheckedChangeListener { _, isChecked ->
            controller.autoAssist = isChecked
            if (!isChecked) controller.autoMove = false
            b.toggleMove.isChecked = controller.autoMove
        }
        b.toggleMove.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !controller.autoAssist) {
                b.toggleMove.isChecked = false
                Toast.makeText(this, "请先开启自动辅助", Toast.LENGTH_SHORT).show()
            } else {
                controller.autoMove = isChecked
            }
        }
    }

    private fun removeOverlay() {
        if (overlayView != null) {
            try { wm?.removeView(overlayView) } catch (e: Exception) {}
        }
        overlayView = null; binding = null; wm = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "JJ揭棋助手", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)
        return builder
            .setContentTitle("JJ揭棋助手")
            .setContentText("辅助服务运行中，点击返回设置")
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(android.app.PendingIntent.getActivity(
                this, 0, Intent(this, com.jjchess.helper.ui.MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            ))
            .setOngoing(true)
            .build()
    }

    fun setPower(power: AnalysisEngine.Power) { controller.power = power }

    override fun onDestroy() {
        controller.stop()
        removeOverlay()
        instance = null
        super.onDestroy()
    }
}
