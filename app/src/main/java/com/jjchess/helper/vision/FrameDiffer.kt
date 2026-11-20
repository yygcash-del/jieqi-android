package com.jjchess.helper.vision

import android.graphics.Bitmap
import com.jjchess.helper.core.Const

/**
 * 帧差检测：判断两帧画面是否有显著变化。
 * 用于"画面稳定后识别、识别中画面变化就中断重来"的触发策略。
 */
object FrameDiffer {

    /**
     * 计算两帧之间的像素差异比例（0..1）。
     * 采用隔步采样降低开销。
     */
    fun diffRatio(a: Bitmap?, b: Bitmap?): Float {
        if (a == null || b == null) return 1f
        if (a.width != b.width || a.height != b.height) return 1f
        val w = a.width; val h = a.height
        val pa = IntArray(w * h); val pb = IntArray(w * h)
        a.getPixels(pa, 0, w, 0, 0, w, h)
        b.getPixels(pb, 0, w, 0, 0, w, h)
        var changed = 0; var total = 0
        val step = Const.CAPTURE_DIFF_SAMPLE_STEP
        for (y in 0 until h step step) for (x in 0 until w step step) {
            total++
            val ca = pa[y * w + x]; val cb = pb[y * w + x]
            val dr = kotlin.math.abs(((ca shr 16) and 0xFF) - ((cb shr 16) and 0xFF))
            val dg = kotlin.math.abs(((ca shr 8) and 0xFF) - ((cb shr 8) and 0xFF))
            val db = kotlin.math.abs((ca and 0xFF) - (cb and 0xFF))
            if (dr + dg + db > 45) changed++
        }
        return if (total == 0) 0f else changed.toFloat() / total
    }

    /** 画面是否在变化 */
    fun isChanging(a: Bitmap?, b: Bitmap?): Boolean =
        diffRatio(a, b) > Const.CAPTURE_DIFF_RATIO_THRESHOLD
}
