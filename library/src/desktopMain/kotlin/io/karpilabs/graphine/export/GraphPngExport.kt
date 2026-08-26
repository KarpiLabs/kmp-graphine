/*
 * Copyright 2026 KarpiLabs LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.karpilabs.graphine.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import java.awt.BasicStroke
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import java.awt.Color as AwtColor

/**
 * Rasterizes this export model to PNG bytes via `java.awt`/`ImageIO`. Desktop (JVM) only —
 * the other KmpGraphine targets (Android, iOS, JS) don't share a common raster API in
 * commonMain, so this lives in `desktopMain` rather than as a cross-platform `expect`/`actual`.
 *
 * @param scale Supersampling factor applied before encoding (e.g. 2f for a sharper export).
 */
fun GraphExportModel.toPngBytes(scale: Float = 2f): ByteArray {
    val safeScale = if (scale.isFinite() && scale > 0f) scale.coerceIn(0.1f, 10f) else 2f
    val safeWidth = if (width.isFinite() && width > 0f) width else 1f
    val safeHeight = if (height.isFinite() && height > 0f) height else 1f
    val maxDimension = 8192
    val pixelWidth = (safeWidth * safeScale).toInt().coerceIn(1, maxDimension)
    val pixelHeight = (safeHeight * safeScale).toInt().coerceIn(1, maxDimension)
    val image = BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.scale(safeScale.toDouble(), safeScale.toDouble())

        g.color = AwtColor(backgroundColor.toArgb(), true)
        g.fillRect(0, 0, safeWidth.toInt() + 1, safeHeight.toInt() + 1)

        edges.forEach { edge ->
            if (edge.points.size < 2) return@forEach
            g.color = AwtColor(edge.color.toArgb(), true)
            g.stroke = BasicStroke(edge.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

            val path = Path2D.Float()
            val pts = edge.points
            path.moveTo(pts[0].x.toDouble(), pts[0].y.toDouble())
            if (edge.isCubicBezier && pts.size == 4) {
                path.curveTo(
                    pts[1].x.toDouble(),
                    pts[1].y.toDouble(),
                    pts[2].x.toDouble(),
                    pts[2].y.toDouble(),
                    pts[3].x.toDouble(),
                    pts[3].y.toDouble(),
                )
            } else {
                for (i in 1 until pts.size) path.lineTo(pts[i].x.toDouble(), pts[i].y.toDouble())
            }
            g.draw(path)

            if (edge.arrowhead) {
                val to = pts.last()
                val from = pts[pts.size - 2]
                drawArrowhead(g, from, to)
            }
        }

        val labelFont = Font("SansSerif", Font.BOLD, 12)
        nodes.forEach { node ->
            g.color = AwtColor(node.color.toArgb(), true)
            val r = node.radius
            g.fill(Ellipse2D.Float(node.center.x - r, node.center.y - r, r * 2, r * 2))

            if (node.label.isNotEmpty()) {
                g.color = AwtColor.WHITE
                g.font = labelFont.deriveFont((r * 0.6f).coerceAtLeast(8f))
                val fm = g.fontMetrics
                val textWidth = fm.stringWidth(node.label)
                g.drawString(node.label, node.center.x - textWidth / 2f, node.center.y + fm.ascent / 2f - 2f)
            }
        }
    } finally {
        g.dispose()
    }

    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

private fun drawArrowhead(g: java.awt.Graphics2D, from: Offset, to: Offset, size: Float = 10f) {
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val p1x = to.x - size * cos(angle - 0.5).toFloat()
    val p1y = to.y - size * sin(angle - 0.5).toFloat()
    val p2x = to.x - size * cos(angle + 0.5).toFloat()
    val p2y = to.y - size * sin(angle + 0.5).toFloat()

    val triangle = Path2D.Float()
    triangle.moveTo(to.x.toDouble(), to.y.toDouble())
    triangle.lineTo(p1x.toDouble(), p1y.toDouble())
    triangle.lineTo(p2x.toDouble(), p2y.toDouble())
    triangle.closePath()
    g.fill(triangle)
}
