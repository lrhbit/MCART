package com.axolotl.mcart.core

import com.axolotl.mcart.i18n.I18n
import com.axolotl.mcart.model.BlockColor
import com.axolotl.mcart.model.BlockTag
import com.axolotl.mcart.model.PaletteService
import com.axolotl.mcart.model.Project
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

/**
 * 质量检查：空选区、色域偏差、创造专属、自定义方块、超大工程等警告。
 */
object QualityChecker {

    const val GAMUT_THRESHOLD: Double = 12.0
    const val GAMUT_WARN_RATIO: Double = 0.15
    const val LARGE_BUILD: Long = 262144L

    @JvmStatic
    fun check(p: Project?): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (p == null) {
            issues.add(Issue(Level.INFO, I18n.t("warn.noProject")))
            return issues
        }

        if (p.selectedBlockIds.isEmpty()) {
            issues.add(Issue(Level.ERROR, I18n.t("warn.emptyBlocks")))
        }

        if (!p.hasImage()) {
            issues.add(Issue(Level.INFO, I18n.t("warn.noImage")))
            return issues
        }

        val r = p.result
        if (r == null) {
            issues.add(Issue(Level.INFO, I18n.t("warn.noResult")))
            return issues
        }

        val badRatio = gamutBadRatio(p)
        if (badRatio > GAMUT_WARN_RATIO) {
            issues.add(
                Issue(
                    Level.WARN,
                    I18n.t(
                        "warn.gamut",
                        Math.round(badRatio * 100.0),
                        Math.round(GAMUT_THRESHOLD),
                    ),
                )
            )
        }

        var creative = 0L
        var custom = 0L
        for (row in MaterialList.of(r)) {
            val bc: BlockColor? = PaletteService.byId(row.blockId)
            if (bc != null) {
                if (BlockTag.tagsOf(bc).contains(BlockTag.CREATIVE_ONLY)) {
                    creative += row.count
                }
                if (bc.category == BlockColor.Category.SPECIAL && bc.colorId >= 1000) {
                    custom += row.count
                }
            }
        }

        if (creative > 0) issues.add(Issue(Level.WARN, I18n.t("warn.creative", creative)))
        if (custom > 0) issues.add(Issue(Level.WARN, I18n.t("warn.custom", custom)))

        val total = MaterialList.total(MaterialList.of(r))
        if (total > LARGE_BUILD) issues.add(Issue(Level.WARN, I18n.t("warn.tooLarge", total)))

        return issues
    }

    @JvmStatic
    fun gamutBadRatio(p: Project): Double {
        val r = p.result ?: return 0.0
        if (!p.hasImage()) return 0.0
        try {
            val tw = p.targetWidth()
            val th = p.targetHeight()
            var src = p.getSourceImage()!!
            val roi: Rectangle2D.Float? = p.roi()
            if (roi != null) src = cropRoi(src, roi)

            val scaled = BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = scaled.createGraphics()
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.drawImage(src, 0, 0, tw, th, null)
            g.dispose()

            val blocks = p.selectedBlockIds.mapNotNull(PaletteService::byId)
            if (blocks.isEmpty()) return 0.0

            val matcher = ColorMatcher(blocks, p.mode)
            val pixels = ImagePreprocessor.toPixels(scaled, p.preprocess, matcher.getPalette())
            var bad = 0L
            var total = 0L
            val step = maxOf(1, tw * th / 120000)

            for (y in 0 until th step step) {
                for (x in 0 until tw step step) {
                    val rgb = pixels[y][x]
                    val pr = (rgb shr 16) and 0xFF
                    val pg = (rgb shr 8) and 0xFF
                    val pb = rgb and 0xFF
                    val e = matcher.match(pr, pg, pb)
                    val lab = ColorConverter.rgbToLab(pr, pg, pb)
                    if (ColorConverter.deltaE(lab, e.lab) > GAMUT_THRESHOLD) bad++
                    total++
                }
            }
            return if (total == 0L) 0.0 else bad.toDouble() / total.toDouble()
        } catch (e: Exception) {
            return 0.0
        }
    }

    @JvmStatic
    fun gamutHeatmap(p: Project, scale: Int): BufferedImage? {
        if (p.result == null || !p.hasImage()) return null
        try {
            val tw = p.targetWidth()
            val th = p.targetHeight()
            var src = p.getSourceImage()!!
            val roi: Rectangle2D.Float? = p.roi()
            if (roi != null) src = cropRoi(src, roi)

            val scaled = BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = scaled.createGraphics()
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.drawImage(src, 0, 0, tw, th, null)
            g.dispose()

            val blocks = p.selectedBlockIds.mapNotNull(PaletteService::byId)
            if (blocks.isEmpty()) return null

            val matcher = ColorMatcher(blocks, p.mode)
            val pixels = ImagePreprocessor.toPixels(scaled, p.preprocess, matcher.getPalette())
            /*
             * 直接写 int[] 像素缓冲：一次算好颜色，按缩放倍数块拷贝，
             * 全程零图形管线调用、零临时对象。
             */
            val ow = tw * scale
            val oh = th * scale
            val buf = IntArray(ow * oh)
            val lab = DoubleArray(3)

            for (y in 0 until th) {
                val rowPx = pixels[y]
                for (x in 0 until tw) {
                    val rgb = rowPx[x]
                    val pr = (rgb shr 16) and 0xFF
                    val pg = (rgb shr 8) and 0xFF
                    val pb = rgb and 0xFF
                    val e = matcher.match(pr, pg, pb)
                    ColorConverter.rgbToLab(pr, pg, pb, lab)
                    val de = ColorConverter.deltaE(lab, e.lab)
                    var t = (de - GAMUT_THRESHOLD) / 25.0
                    if (t <= 0.01) continue
                    if (t > 1.0) t = 1.0
                    val alpha = (minOf(0.85, 0.25 + t * 0.6) * 255.0).toInt()
                    val argb = (alpha shl 24) or (255 shl 16) or (40 shl 8) or 40
                    val ox = x * scale
                    val oy = y * scale
                    for (yy in 0 until scale) {
                        val base = (oy + yy) * ow + ox
                        for (xx in 0 until scale) buf[base + xx] = argb
                    }
                }
            }

            // 必须带 alpha：热力图之后会 drawImage 叠到底图上，透明处要能看到底图
            val heat = BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB)
            heat.setRGB(0, 0, ow, oh, buf, 0, ow)
            return heat
        } catch (e: Exception) {
            return null
        }
    }

    /** 按 ROI 裁剪源图（与原逐方法内联的坐标计算一致） */
    private fun cropRoi(src: BufferedImage, roi: Rectangle2D.Float): BufferedImage {
        val sw = src.width
        val sh = src.height
        val cx = maxOf(0, Math.round(roi.x * sw))
        val cy = maxOf(0, Math.round(roi.y * sh))
        val cw = maxOf(1, minOf(sw - cx, Math.round(roi.width * sw)))
        val ch = maxOf(1, minOf(sh - cy, Math.round(roi.height * sh)))
        return src.getSubimage(cx, cy, cw, ch)
    }

    /** 质量问题条目，字段 @JvmField 供 Java 直接访问 */
    class Issue(
        @JvmField val level: Level,
        @JvmField val message: String,
    )

    enum class Level { INFO, WARN, ERROR }
}
