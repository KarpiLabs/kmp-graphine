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
import androidx.compose.ui.graphics.Color
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.EdgeStyle
import io.karpilabs.graphine.model.GraphNode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Exports a [GraphState] snapshot as a standalone image, independent of the live Composable tree.
 *
 * Since [GraphState] is generic over arbitrary node data (`T`) rendered by consumer-supplied
 * Composables, export can't capture rich node UI directly. Instead nodes are drawn as labeled
 * circles — the same structural representation graph tools like Graphviz use — via [nodeColor]
 * and [nodeLabel] callbacks.
 *
 * [toSvg] works on every KmpGraphine target (it's pure string generation). PNG rasterization
 * (`GraphExportModel.toPngBytes()`) is Desktop/JVM-only — see the `export` package in the
 * `desktopMain` source set.
 */
object GraphExport {

    private val DEFAULT_NODE_COLOR = Color(0xFF3B82F6)

    /** Builds a flat, renderer-agnostic snapshot of [state]'s currently visible nodes/edges. */
    fun <T> buildModel(
        state: GraphState<T>,
        edgeConfig: EdgeConfig = EdgeConfig(),
        nodeRadius: Float = 24f,
        padding: Float = 40f,
        backgroundColor: Color = Color.White,
        nodeColor: (GraphNode<T>) -> Color = { DEFAULT_NODE_COLOR },
        nodeLabel: (GraphNode<T>) -> String = { it.id },
    ): GraphExportModel {
        val safeNodeRadius = if (nodeRadius.isFinite() && nodeRadius > 0f) nodeRadius else 24f
        val safePadding = if (padding.isFinite() && padding >= 0f) padding else 40f
        val visibleIds = state.getVisibleNodes().map { it.id }
        if (visibleIds.isEmpty()) {
            return GraphExportModel(safePadding * 2f, safePadding * 2f, backgroundColor, emptyList(), emptyList())
        }

        val rawPositions = visibleIds.associateWith { id -> state.nodeStates.getValue(id).position }
            .filterValues { it.x.isFinite() && it.y.isFinite() }
        if (rawPositions.isEmpty()) {
            return GraphExportModel(safePadding * 2f, safePadding * 2f, backgroundColor, emptyList(), emptyList())
        }
        val minX = rawPositions.values.minOf { it.x }
        val minY = rawPositions.values.minOf { it.y }
        val maxX = rawPositions.values.maxOf { it.x }
        val maxY = rawPositions.values.maxOf { it.y }
        val shift = Offset(safePadding - minX, safePadding - minY)
        val width = (maxX - minX) + safePadding * 2f
        val height = (maxY - minY) + safePadding * 2f

        val nodes = visibleIds.map { id ->
            val node = state.nodeStates.getValue(id).node
            val pos = rawPositions.getValue(id)
            ExportNode(
                id = id,
                center = pos + shift,
                radius = safeNodeRadius,
                color = nodeColor(node),
                label = nodeLabel(node),
            )
        }
        val nodeById = nodes.associateBy { it.id }

        val edges = state.edges.mapNotNull { edge ->
            val from = nodeById[edge.from] ?: return@mapNotNull null
            val to = nodeById[edge.to] ?: return@mapNotNull null
            routeExportEdge(from.center, to.center, from.radius, to.radius, edgeConfig.style, edgeConfig)
        }

        return GraphExportModel(width, height, backgroundColor, nodes, edges)
    }

    /** Convenience overload: builds the model and renders it to SVG in one call. */
    fun <T> toSvg(
        state: GraphState<T>,
        edgeConfig: EdgeConfig = EdgeConfig(),
        nodeRadius: Float = 24f,
        padding: Float = 40f,
        backgroundColor: Color = Color.White,
        nodeColor: (GraphNode<T>) -> Color = { DEFAULT_NODE_COLOR },
        nodeLabel: (GraphNode<T>) -> String = { it.id },
    ): String = toSvg(buildModel(state, edgeConfig, nodeRadius, padding, backgroundColor, nodeColor, nodeLabel))

    /** Renders a pre-built [GraphExportModel] as a standalone SVG document string. */
    fun toSvg(model: GraphExportModel): String {
        val sb = StringBuilder()
        sb.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${fmt(model.width)}\" height=\"${fmt(model.height)}\" " +
                "viewBox=\"0 0 ${fmt(model.width)} ${fmt(model.height)}\">\n",
        )
        sb.append(
            "  <rect x=\"0\" y=\"0\" width=\"${fmt(model.width)}\" height=\"${fmt(model.height)}\" " +
                "fill=\"${model.backgroundColor.toCssColor()}\"/>\n",
        )
        model.edges.forEach { sb.append(edgeToSvg(it)) }
        model.nodes.forEach { sb.append(nodeToSvg(it)) }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun edgeToSvg(edge: ExportEdge): String {
        val pts = edge.points
        if (pts.size < 2) return ""
        val d = StringBuilder("M ${fmt(pts[0].x)} ${fmt(pts[0].y)} ")
        if (edge.isCubicBezier && pts.size == 4) {
            d.append("C ${fmt(pts[1].x)} ${fmt(pts[1].y)}, ${fmt(pts[2].x)} ${fmt(pts[2].y)}, ${fmt(pts[3].x)} ${fmt(pts[3].y)}")
        } else {
            for (i in 1 until pts.size) d.append("L ${fmt(pts[i].x)} ${fmt(pts[i].y)} ")
        }
        val sb = StringBuilder()
        sb.append(
            "  <path d=\"$d\" fill=\"none\" stroke=\"${edge.color.toCssColor()}\" " +
                "stroke-width=\"${fmt(edge.strokeWidth)}\" stroke-linecap=\"round\"/>\n",
        )
        if (edge.arrowhead) {
            val to = pts.last()
            val from = pts[pts.size - 2]
            sb.append(arrowheadToSvg(from, to, edge.color))
        }
        return sb.toString()
    }

    private fun arrowheadToSvg(from: Offset, to: Offset, color: Color, size: Float = 10f): String {
        val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
        val p1x = to.x - size * cos(angle - 0.5).toFloat()
        val p1y = to.y - size * sin(angle - 0.5).toFloat()
        val p2x = to.x - size * cos(angle + 0.5).toFloat()
        val p2y = to.y - size * sin(angle + 0.5).toFloat()
        return "  <polygon points=\"${fmt(to.x)},${fmt(to.y)} ${fmt(p1x)},${fmt(p1y)} ${fmt(p2x)},${fmt(p2y)}\" " +
            "fill=\"${color.toCssColor()}\"/>\n"
    }

    private fun nodeToSvg(node: ExportNode): String {
        val sb = StringBuilder()
        val idAttr = escapeXml(node.id)
        sb.append(
            "  <circle id=\"$idAttr\" cx=\"${fmt(node.center.x)}\" cy=\"${fmt(node.center.y)}\" r=\"${fmt(node.radius)}\" " +
                "fill=\"${node.color.toCssColor()}\"/>\n",
        )
        if (node.label.isNotEmpty()) {
            sb.append(
                "  <text x=\"${fmt(node.center.x)}\" y=\"${fmt(node.center.y + node.radius / 3f)}\" " +
                    "text-anchor=\"middle\" font-size=\"${fmt(node.radius * 0.6f)}\" " +
                    "font-family=\"sans-serif\" fill=\"#FFFFFF\">${escapeXml(node.label)}</text>\n",
            )
        }
        return sb.toString()
    }

    private fun fmt(value: Float): String {
        val rounded = round(value * 100f) / 100f
        return if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
    }

    /**
     * Escapes XML entity characters and strips invalid XML 1.0 control characters
     * in a single pass to ensure generated SVG documents remain well-formed and safe against injection.
     */
    internal fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\t' || c == '\n' || c == '\r' || (c in '\u0020'..'\uD7FF') || (c in '\uE000'..'\uFFFD')) {
                when (c) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&apos;")
                    else -> sb.append(c)
                }
                i++
            } else if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                sb.append(c)
                sb.append(text[i + 1])
                i += 2
            } else {
                // Strip invalid XML control characters and lone surrogates
                i++
            }
        }
        return sb.toString()
    }
}

private fun Color.toCssColor(): String {
    fun channel(v: Float): String {
        val i = (v * 255f).toInt().coerceIn(0, 255)
        val hex = i.toString(16)
        return if (hex.length == 1) "0$hex" else hex
    }
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

private fun routeExportEdge(
    from: Offset,
    to: Offset,
    fromRadius: Float,
    toRadius: Float,
    style: EdgeStyle,
    edgeConfig: EdgeConfig,
): ExportEdge {
    val clippedFrom = clipToCircle(from, to, fromRadius)
    val clippedTo = clipToCircle(to, from, toRadius)
    val (points, isCubic) = when (style) {
        EdgeStyle.STRAIGHT -> listOf(clippedFrom, clippedTo) to false
        EdgeStyle.CURVED -> {
            val midY = (clippedFrom.y + clippedTo.y) / 2f
            listOf(clippedFrom, Offset(clippedFrom.x, midY), Offset(clippedTo.x, midY), clippedTo) to true
        }
        EdgeStyle.ORTHOGONAL -> {
            val bends = if (abs(clippedTo.y - clippedFrom.y) > abs(clippedTo.x - clippedFrom.x)) {
                val midY = (clippedFrom.y + clippedTo.y) / 2f
                listOf(Offset(clippedFrom.x, midY), Offset(clippedTo.x, midY))
            } else {
                val midX = (clippedFrom.x + clippedTo.x) / 2f
                listOf(Offset(midX, clippedFrom.y), Offset(midX, clippedTo.y))
            }
            (listOf(clippedFrom) + bends + clippedTo) to false
        }
    }
    return ExportEdge(points, isCubic, edgeConfig.color, edgeConfig.width, edgeConfig.showArrowheads)
}

private fun clipToCircle(center: Offset, target: Offset, radius: Float): Offset {
    val dx = target.x - center.x
    val dy = target.y - center.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= radius || len == 0f) return center
    return Offset(center.x + dx / len * radius, center.y + dy / len * radius)
}
