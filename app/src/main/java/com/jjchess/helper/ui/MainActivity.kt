package com.jjchess.helper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jjchess.helper.R
import com.jjchess.helper.capture.CaptureService
import com.jjchess.helper.databinding.ActivityMainBinding
import com.jjchess.helper.engine.AnalysisEngine
import com.jjchess.helper.overlay.OverlayService

/**
 * 主界面：授权引导 + 参数选择 + 服务启停。
 * 首次运行顺序：无障碍服务 -> 悬浮窗权限 -> 通知权限 -> 校准 -> 打开JJ象棋开局 -> 启动辅助。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        binding.btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        binding.btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        binding.btnManual.setOnClickListener {
            startActivity(Intent(this, ManualBoardActivity::class.java))
        }
        binding.btnStart.setOnClickListener { startOverlay() }
        binding.btnStop.setOnClickListener { stopOverlay() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val a11y = CaptureService.isEnabled()
        binding.tvA11y.text = getString(if (a11y) R.string.a11y_on else R.string.a11y_off)
        binding.tvA11y.setTextColor(ContextCompat.getColor(this,
            if (a11y) R.color.accent else R.color.primary_dark))

        val overlay = OverlayService.canDraw(this)
        binding.tvOverlay.text = getString(if (overlay) R.string.overlay_on else R.string.overlay_off)
        binding.tvOverlay.setTextColor(ContextCompat.getColor(this,
            if (overlay) R.color.accent else R.color.primary_dark))

        val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        binding.tvNotification.text = if (notif) "通知：已授权" else "通知：未授权"

        binding.tvService.text = getString(if (OverlayService.instance != null)
            R.string.service_running else R.string.service_stopped)
    }

    private fun startOverlay() {
        if (!CaptureService.isEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }
        if (!OverlayService.canDraw(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        val power = when (binding.rgPower.checkedRadioButtonId) {
            R.id.rbSave -> AnalysisEngine.Power.SAVE
            R.id.rbFull -> AnalysisEngine.Power.FULL
            else -> AnalysisEngine.Power.STD
        }
        val side = if (binding.rbRed.isChecked) com.jjchess.helper.core.Side.RED
                   else com.jjchess.helper.core.Side.BLACK

        val intent = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)

        // 参数是 later 通过 controller 设置的；服务启动是异步的，用短暂延时设置
        binding.root.postDelayed({
            OverlayService.instance?.let {
                it.setPower(power)
                it.controller.ourSide = side
            }
        }, 300)
        Toast.makeText(this, "辅助已启动，请在悬浮窗中开启自动辅助", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP }
        startService(intent)
        Toast.makeText(this, "辅助已停止", Toast.LENGTH_SHORT).show()
        binding.root.postDelayed({ refreshStatus() }, 300)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }
}
