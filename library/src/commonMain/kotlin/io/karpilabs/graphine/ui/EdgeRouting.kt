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

package io.karpilabs.graphine.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.karpilabs.graphine.model.NodePort
import kotlin.math.abs

private fun Float.isFiniteNumber(): Boolean = isFinite()
private fun Offset.isFiniteOffset(): Boolean = x.isFinite() && y.isFinite()

/** The point on [rect]'s boundary corresponding to a fixed [NodePort] side. */
internal fun portAnchor(rect: Rect, port: NodePort): Offset {
    if (!rect.left.isFinite() ||
        !rect.top.isFinite() ||
        !rect.right.isFinite() ||
        !rect.bottom.isFinite()
    ) {
        return Offset.Zero
    }
    return when (port) {
        NodePort.TOP -> Offset(rect.center.x, rect.top)
        NodePort.BOTTOM -> Offset(rect.center.x, rect.bottom)
        NodePort.LEFT -> Offset(rect.left, rect.center.y)
        NodePort.RIGHT -> Offset(rect.right, rect.center.y)
    }
}

/**
 * Clips [target]-facing point on [rect]'s boundary, so edges terminate at a node's edge
 * rather than its visual center. [rect] must be centered on [center] (e.g. a node's bounds).
 * Returns [center] unchanged if [rect] has no area (e.g. an unmeasured DOT-mode node).
 */
internal fun clipToRectBoundary(center: Offset, target: Offset, rect: Rect): Offset {
    // Security guard: Validate finite offsets and rect dimensions to prevent NaN propagation to UI canvas
    if (!center.isFiniteOffset()) return Offset.Zero
    if (!target.isFiniteOffset() ||
        !rect.width.isFinite() ||
        !rect.height.isFinite() ||
        rect.width <= 0f ||
        rect.height <= 0f
    ) {
        return center
    }

    val dx = target.x - center.x
    val dy = target.y - center.y
    if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return center

    val halfW = rect.width / 2f
    val halfH = rect.height / 2f
    val scaleX = if (dx != 0f) halfW / abs(dx) else Float.MAX_VALUE
    val scaleY = if (dy != 0f) halfH / abs(dy) else Float.MAX_VALUE
    val scale = minOf(scaleX, scaleY, 1f)

    if (!scale.isFinite()) return center
    val result = Offset(center.x + dx * scale, center.y + dy * scale)
    return if (result.isFiniteOffset()) result else center
}

/**
 * Heuristic single-bow obstacle avoidance: if the straight segment [from]-[to] crosses any
 * node rect in [nodeRects] (other than [fromId]/[toId]), returns a control point that bows the
 * path around the busiest side. Returns null when the direct line is already clear.
 *
 * This is not a full pathfinder (no A-star search or visibility graph) — it is a cheap, good-enough nudge
 * intended for modestly sized COMPOSABLE-mode graphs, not large DOT-mode ones.
 */
internal fun findObstacleBow(
    from: Offset,
    to: Offset,
    fromId: String,
    toId: String,
    nodeRects: Map<String, Rect>,
): Offset? {
    // Security guard: Validate finite inputs to prevent NaN/Infinity propagation to Bezier curve control points
    if (!from.isFiniteOffset() || !to.isFiniteOffset()) return null
    val dir = to - from
    val len = dir.getDistance()
    if (!len.isFinite() || len < 1f) return null
    val normal = Offset(-dir.y / len, dir.x / len)
    if (!normal.isFiniteOffset()) return null

    var maxPush = 0f
    var sideSum = 0f
    nodeRects.forEach { (id, rect) ->
        if (id == fromId || id == toId) return@forEach
        if (!rect.left.isFinite() || !rect.top.isFinite() || !rect.right.isFinite() || !rect.bottom.isFinite()) return@forEach
        if (segmentIntersectsRect(from, to, rect)) {
            val center = rect.center
            val cross = dir.x * (center.y - from.y) - dir.y * (center.x - from.x)
            sideSum += if (cross >= 0f) 1f else -1f
            val push = maxOf(rect.width, rect.height) / 2f + 24f
            if (push.isFinite() && push > maxPush) maxPush = push
        }
    }
    if (maxPush <= 0f) return null

    // Bow away from the side where most obstacles sit.
    val sign = if (sideSum >= 0f) -1f else 1f
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    val result = mid + normal * (maxPush * sign)
    return if (result.isFiniteOffset()) result else null
}

internal fun segmentIntersectsRect(p1: Offset, p2: Offset, rect: Rect): Boolean {
    if (rect.contains(p1) || rect.contains(p2)) return true
    return segmentsIntersect(p1, p2, rect.topLeft, rect.topRight) ||
        segmentsIntersect(p1, p2, rect.topRight, rect.bottomRight) ||
        segmentsIntersect(p1, p2, rect.bottomRight, rect.bottomLeft) ||
        segmentsIntersect(p1, p2, rect.bottomLeft, rect.topLeft)
}

private fun segmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
    fun cross(o: Offset, a: Offset, b: Offset): Float = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val d1 = cross(p3, p4, p1)
    val d2 = cross(p3, p4, p2)
    val d3 = cross(p1, p2, p3)
    val d4 = cross(p1, p2, p4)
    return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
        ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
}
