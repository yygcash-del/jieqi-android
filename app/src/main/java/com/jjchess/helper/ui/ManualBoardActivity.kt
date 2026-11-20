package com.jjchess.helper.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jjchess.helper.R
import com.jjchess.helper.core.BoardModel
import com.jjchess.helper.core.Piece
import com.jjchess.helper.core.PieceKind
import com.jjchess.helper.core.Side
import com.jjchess.helper.databinding.ActivityManualBoardBinding
import com.jjchess.helper.overlay.OverlayService

/**
 * 手动摆盘：逐点设置棋子状态（先红黑 -> 再明暗 -> 明子选具体棋字），
 * 保存到全局 BoardModel，后续流程与自动识别共用。
 */
class ManualBoardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualBoardBinding
    private val kindButtons: List<Button> by lazy {
        listOf(binding.k0, binding.k1, binding.k2, binding.k3, binding.k4, binding.k5, binding.k6)
    }
    private val kinds = PieceKind.entries.toList()

    private var selCol = -1
    private var selRow = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.manualBoard.onSelect = { c, r ->
            selCol = c; selRow = r
            binding.tvSelected.text = "已选 ($c,$r)，设置状态后棋子立即显示"
        }

        // 棋字按钮：点击时按当前红黑选择设置明子
        kindButtons.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                if (selCol < 0) { Toast.makeText(this, "请先点选交叉点", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val side = if (binding.rbRed.isChecked) Side.RED else Side.BLACK
                val kind = kinds.getOrNull(idx) ?: return@setOnClickListener
                binding.manualBoard.setPiece(selCol, selRow, Piece.open(side, kind))
                updateKindChars(side)
            }
        }

        binding.rbDark.setOnCheckedChangeListener { _, checked ->
            if (checked && selCol >= 0) {
                val side = if (binding.rbRed.isChecked) Side.RED else Side.BLACK
                binding.manualBoard.setPiece(selCol, selRow, Piece.dark(side))
            }
        }
        binding.rbRed.setOnCheckedChangeListener { _, _ -> updateKindChars(if (binding.rbRed.isChecked) Side.RED else Side.BLACK) }

        binding.btnClearCell.setOnClickListener {
            if (selCol >= 0) binding.manualBoard.setPiece(selCol, selRow, null)
        }

        binding.btnSaveBoard.setOnClickListener { saveBoard() }
    }

    /** 根据红黑更新棋字按钮上的汉字 */
    private fun updateKindChars(side: Side) {
        kinds.forEachIndexed { i, k -> kindButtons[i].text = k.charFor(side).toString() }
    }

    private fun saveBoard() {
        val svc = OverlayService.instance
        if (svc == null) {
            Toast.makeText(this, "请先启动辅助服务", Toast.LENGTH_SHORT).show()
            return
        }
        val board = BoardModel()
        for (c in 0 until 9) for (r in 0 until 10) {
            board.set(c, r, binding.manualBoard.pieces[c][r])
        }
        // 冲突检测
        val conflict = board.pool.validate(board.openKinds())
        if (conflict != null) {
            Toast.makeText(this, getString(R.string.toast_need_recalibrate) + "：$conflict", Toast.LENGTH_LONG).show()
            return
        }
        // 写入正在运行的局面（controller 持有 board，这里通过注入方式更新）
        svc.controller.applyManualBoard(board)
        svc.controller.autoAssist = true // 手动摆盘后开始分析
        Toast.makeText(this, "已保存到局面并开始分析", Toast.LENGTH_SHORT).show()
        finish()
    }
}
