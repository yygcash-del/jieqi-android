package com.jjchess.helper.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jjchess.helper.core.Const
import com.jjchess.helper.core.PieceKind
import com.jjchess.helper.core.Side

/**
 * 棋字模板匹配器。
 * 模板放在 assets/templates/ 下，命名：R_车.png / B_車.png 等（对应 PieceKind.redChar/blackChar）。
 * 模板可为空（仓库不含任何模板），此时 match 恒返回 null，调用方按"未知暗子"处理。
 * 匹配算法：把棋子中心裁剪为灰度图并缩放到统一尺寸，与每个模板做归一化互相关(NCC)，
 * 取相似度最高且超过阈值的棋字。
 */
class TemplateMatcher(context: Context) {

    private data class Tpl(val kind: PieceKind, val side: Side, val gray: FloatArray, val size: Int)
    private val templates: List<Tpl> = loadTemplates(context)
    private val size = 24

    val hasTemplates: Boolean get() = templates.isNotEmpty()

    private fun loadTemplates(context: Context): List<Tpl> {
        val list = mutableListOf<Tpl>()
        val names = context.assets.list(Const.TEMPLATE_DIR) ?: return list
        for (name in names) {
            if (!name.endsWith(".png", true)) continue
            try {
                val side = if (name.startsWith("R_")) Side.RED else if (name.startsWith("B_")) Side.BLACK else continue
                val ch = name.removePrefix("R_").removePrefix("B_").removeSuffix(".png").firstOrNull() ?: continue
                val kind = if (side == Side.RED) PieceKind.fromRedChar(ch) else PieceKind.fromBlackChar(ch)
                if (kind == null) continue
                val bmp = context.assets.open("${Const.TEMPLATE_DIR}/$name").use { BitmapFactory.decodeStream(it) }
                    ?: continue
                list.add(Tpl(kind, side, toGray(bmp, size), size))
            } catch (e: Throwable) {
                // 单个模板损坏则跳过，不影响其他
            }
        }
        return list
    }

    /** 在 (cx,cy) 处裁剪半径 radius 的区域，转灰度并缩放到 size x size */
    private fun cropGray(px: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): FloatArray {
        val out = FloatArray(size * size)
        val srcSize = radius * 2
        for (y in 0 until size) for (x in 0 until size) {
            val sx = cx - radius + (x * srcSize / size)
            val sy = cy - radius + (y * srcSize / size)
            val xx = sx.coerceIn(0, w - 1); val yy = sy.coerceIn(0, h - 1)
            val color = px[yy * w + xx]
            val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
            out[y * size + x] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
        return out
    }

    private fun toGray(bmp: Bitmap, s: Int): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bmp, s, s, true)
        val px = IntArray(s * s)
        scaled.getPixels(px, 0, s, 0, 0, s, s)
        return FloatArray(s * s) { i ->
            val r = (px[i] shr 16) and 0xFF; val g = (px[i] shr 8) and 0xFF; val b = px[i] and 0xFF
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
    }

    /** NCC 归一化互相关 */
    private fun ncc(a: FloatArray, b: FloatArray): Float {
        val n = a.size
        var sa = 0f; var sb = 0f
        for (i in 0 until n) { sa += a[i]; sb += b[i] }
        val ma = sa / n; val mb = sb / n
        var num = 0f; var da = 0f; var db = 0f
        for (i in 0 until n) {
            val x = a[i] - ma; val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        val den = sqrt(da * db.toDouble()).toFloat()
        return if (den < 1e-6f) 0f else num / den
    }

    /** 返回匹配到的棋字；无模板或无高相似匹配返回 null */
    fun match(px: IntArray, w: Int, h: Int, cx: Int, cy: Int, side: Side, radius: Int): PieceKind? {
        if (templates.isEmpty()) return null
        val gray = cropGray(px, w, h, cx, cy, radius)
        var best: PieceKind? = null
        var bestScore = Const.TEMPLATE_MATCH_THRESHOLD
        for (t in templates) {
            // 只匹配对应颜色的模板（红方模板 R_ 对应汉字，黑方模板 B_ 对应汉字）
            if (t.side != side) continue
            val score = ncc(gray, t.gray)
            if (score > bestScore) { bestScore = score; best = t.kind }
        }
        return best
    }
}
