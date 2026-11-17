package com.example.jieqi.capture

import android.app.*; import android.content.Context; import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager; import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection; import android.media.projection.MediaProjectionManager
import android.os.*; import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var vdisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        startForegroundWithNotification()

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(
            intent.getIntExtra(EXTRA_CODE, -1),
            intent.getParcelableExtra<Intent>(EXTRA_DATA)!!)

        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { img ->
                // TODO: 裁棋盘区域 → Detector 推理 → Socket 发给桌面计算端
            }
        }, null)

        vdisplay = projection!!.createVirtualDisplay(
            "jieqi-capture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null)

        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, null)
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val chId = "jieqi_capture"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(chId, "采集服务", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("揭棋助手").setContentText("屏幕采集中")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        vdisplay?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"; const val EXTRA_DATA = "data"
        fun start(ctx: Context, code: Int, data: Intent) =
            ContextCompat.startForegroundService(ctx,
                Intent(ctx, CaptureService::class.java)
                    .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
    }
}
