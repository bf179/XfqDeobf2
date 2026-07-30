package com.fanqie.xfqdeobf2

import android.graphics.Bitmap

/**
 * 小番茄图片解混淆 —— Gilbert(广义 Hilbert) 空间填充曲线
 *
 * 混淆: dst[curve[(i+offset)%n]] = src[curve[i]]
 * 解混淆(逆运算): dst[curve[i]] = src[curve[(i+offset)%n]]
 *
 * key 默认为黄金比例共轭 (sqrt(5)-1)/2 ≈ 0.618
 * offset = round(key * W * H)
 */
object GilbertCurve {

    /** 限制约 800 万像素避免 OOM */
    private const val MAX_PIXELS = 8_000_000

    data class DeobfResult(val bitmap: Bitmap, val width: Int, val height: Int)

    @Throws(IllegalArgumentException::class, OutOfMemoryError::class)
    fun deobfuscate(src: Bitmap, key: Double): DeobfResult {
        val w = src.width
        val h = src.height
        val n = w * h
        require(n in 1..MAX_PIXELS) { "图片过大 (${w}x${h})，最大支持 ${MAX_PIXELS} 像素" }

        val pixels = IntArray(n)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val curve = generateCurve(w, h)
        val offset = Math.round(key * n).toInt()
        val out = IntArray(n)
        for (i in 0 until n) {
            out[curve[i]] = pixels[curve[(i + offset) % n]]
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return DeobfResult(result, w, h)
    }

    private fun generateCurve(w: Int, h: Int): IntArray {
        val curve = IntArray(w * h)
        val idx = intArrayOf(0)
        if (w >= h) {
            gen(0, 0, w, 0, 0, h, w, curve, idx)
        } else {
            gen(0, 0, 0, h, w, 0, w, curve, idx)
        }
        return curve
    }

    private fun gen(
        x: Int, y: Int, ax: Int, ay: Int, bx: Int, by: Int,
        imgW: Int, curve: IntArray, idx: IntArray
    ) {
        val w = kotlin.math.abs(ax + ay)
        val h = kotlin.math.abs(bx + by)
        val dax = sgn(ax); val day = sgn(ay)
        val dbx = sgn(bx); val dby = sgn(by)

        if (h == 1) {
            var xx = x; var yy = y
            repeat(w) { curve[idx[0]++] = xx + yy * imgW; xx += dax; yy += day }
            return
        }
        if (w == 1) {
            var xx = x; var yy = y
            repeat(h) { curve[idx[0]++] = xx + yy * imgW; xx += dbx; yy += dby }
            return
        }
        var ax2 = Math.floorDiv(ax, 2); var ay2 = Math.floorDiv(ay, 2)
        var bx2 = Math.floorDiv(bx, 2); var by2 = Math.floorDiv(by, 2)
        val w2 = kotlin.math.abs(ax2 + ay2)
        val h2 = kotlin.math.abs(bx2 + by2)

        if (2 * w > 3 * h) {
            if (w2 % 2 != 0 && w > 2) { ax2 += dax; ay2 += day }
            gen(x, y, ax2, ay2, bx, by, imgW, curve, idx)
            gen(x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by, imgW, curve, idx)
        } else {
            if (h2 % 2 != 0 && h > 2) { bx2 += dbx; by2 += dby }
            gen(x, y, bx2, by2, ax2, ay2, imgW, curve, idx)
            gen(x + bx2, y + by2, ax, ay, bx - bx2, by - by2, imgW, curve, idx)
            gen(
                x + (ax - dax) + (bx2 - dbx),
                y + (ay - day) + (by2 - dby),
                -bx2, -by2, -(ax - ax2), -(ay - ay2),
                imgW, curve, idx
            )
        }
    }

    private fun sgn(v: Int) = if (v > 0) 1 else if (v < 0) -1 else 0
}
