package com.example.jieqi

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val reqOverlay = 41
    private val reqCapture = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(pad, pad, pad, pad)

        status = TextView(this)
        status.text = "JieQi Assistant - capture side"
        status.textSize = 18f
        root.addView(status)

        val b1 = Button(this)
        b1.text = "Enable overlay"
        b1.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java))
            } else {
                val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName))
                startActivityForResult(i, reqOverlay)
            }
        }
        root.addView(b1)

        val b2 = Button(this)
        b2.text = "Start capture"
        b2.setOnClickListener {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
        }
        root.addView(b2)

        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture && resultCode == RESULT_OK && data != null) {
            CaptureService.start(this, resultCode, data)
            status.text = "Capturing..."
        }
    }
}
