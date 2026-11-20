package com.jjchess.helper.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jjchess.helper.R
import com.jjchess.helper.capture.CaptureService
import com.jjchess.helper.databinding.ActivityCalibrationBinding
import com.jjchess.helper.vision.BoardRecognizer
import com.jjchess.helper.vision.CalibrationStore

/**
 * 校准界面：截一帧棋盘 -> 自动检测边界 -> 叠加可拖动点阵 -> 微调保存 -> 逐点识别预览(异常标红)。
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var store: CalibrationStore
    private lateinit var recognizer: BoardRecognizer
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = CalibrationStore(this)
        recognizer = BoardRecognizer(this)

        binding.btnDetect.setOnClickListener { detectBoard() }
        binding.btnPreview.setOnClickListener { showPreview() }
        binding.btnSave.setOnClickListener { save() }

        // 进入后自动抓一帧
        captureFrame()
    }

    private fun captureFrame() {
        val svc = CaptureService.instance
        if (svc == null) {
            Toast.makeText(this, "请先开启无障碍服务，再回到此页截图", Toast.LENGTH_LONG).show()
            return
        }
        // captureOnce 内部会等待截图回调，必须在后台线程调用
        Thread {
            val b = svc.captureOnce()
            runOnUiThread {
                if (b == null) {
                    Toast.makeText(this@CalibrationActivity, "截图失败，请确保JJ象棋在前台且无障碍已开启", Toast.LENGTH_SHORT).show()
                } else {
                    bitmap = b
                    binding.calibView.setBitmap(b)
                    binding.calibView.setCalib(store.effective())
                    Toast.makeText(this@CalibrationActivity, "已截图，可拖动点阵微调", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun detectBoard() {
        val b = bitmap
        if (b == null) { Toast.makeText(this, "请先截图", Toast.LENGTH_SHORT).show(); return }
        val c = recognizer.detectBoard(b)
        if (c == null) {
            Toast.makeText(this, "未检测到棋盘，请手动拖动点阵校准", Toast.LENGTH_SHORT).show()
            return
        }
        binding.calibView.setCalib(c)
        Toast.makeText(this, "已自动检测边界，请微调后保存", Toast.LENGTH_SHORT).show()
    }

    private fun showPreview() {
        val b = bitmap
        if (b == null) { Toast.makeText(this, "请先截图", Toast.LENGTH_SHORT).show(); return }
        // 用当前点阵坐标识别每点，标记低置信度点为红
        binding.calibView.setCalib(binding.calibView.getCalib())
        val cells = recognizer.recognize(b)
        val abnormal = Array(9) { BooleanArray(10) }
        var badCount = 0
        for (c in 0 until 9) for (r in 0 until 10) {
            val bad = recognizer.isCellAbnormal(cells[c][r])
            abnormal[c][r] = bad
            if (bad) badCount++
        }
        binding.calibView.setPreview(abnormal)
        binding.tvPreviewInfo.text = "识别预览：红点为异常/低置信度（$badCount/90）。" +
            "正常应只有90个交叉点全亮；请按真实棋盘核对并拖动微调。"
    }

    private fun save() {
        store.save(binding.calibView.getCalib())
        Toast.makeText(this, R.string.toast_calib_saved, Toast.LENGTH_SHORT).show()
        finish()
    }
}
