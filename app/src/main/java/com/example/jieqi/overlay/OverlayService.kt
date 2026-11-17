package com.example.jieqi.overlay

import android.app.Service; import android.content.Intent; import android.graphics.PixelFormat
import android.os.IBinder; import android.view.View; import android.view.WindowManager

class OverlayService : Service() {
    private lateinit var wm: WindowManager

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // 全屏透明叠加层：游戏照常收触摸，浮层只画箭头/高亮/热区
        val overlay = View(this)   // TODO: 换成自定义 OverlayView
        wm.addView(overlay, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
