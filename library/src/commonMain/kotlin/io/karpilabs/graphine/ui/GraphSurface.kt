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

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.DotStyle
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.EdgeStyle
import io.karpilabs.graphine.model.GraphGroup
import io.karpilabs.graphine.model.GraphNode
import io.karpilabs.graphine.model.NodePort
import io.karpilabs.graphine.model.NodeRenderMode
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Live drag-to-link state: dragging from [fromId]'s [port] toward [current] (content-space). */
private data class PendingLink(val fromId: String, val port: NodePort, val anchor: Offset, val current: Offset)

private val PORT_HANDLE_SIZE = 12.dp
private const val PORT_HANDLE_RADIUS_PX = 6f

/**
 * The main UI entry point for KmpGraphine.
 *
 * Provides a high-performance, zoomable, and interactive canvas for visualizing nodes and edges.
 * Supports inertia panning, group zones, and semantic zoom.
 *
 * @param nodeRenderMode Determines how nodes are rendered: [NodeRenderMode.COMPOSABLE] for rich content,
 *                       [NodeRenderMode.DOT] for lightweight canvas-drawn dots.
 * @param dotStyle Visual styling for dots in [NodeRenderMode.DOT] mode. Ignored in [NodeRenderMode.COMPOSABLE] mode.
 * @param onNodeDragged Optional callback while a node is being dragged (each move). Useful for pinning/syncing physics.
 * @param onNodeDragEnd Optional callback when a node drag gesture ends. Useful for unpinning a simulated node.
 * @param selectionMode When true, dragging on empty canvas draws a box-select rectangle instead of
 *                       panning; nodes whose bounds overlap the box are added to [GraphState.selectedNodeIds]
 *                       live as the drag proceeds. Only applies to [NodeRenderMode.COMPOSABLE]; ignored in
 *                       [NodeRenderMode.DOT] (where empty-space drags always pan).
 * @param enablePortConnections When true (and [nodeRenderMode] is [NodeRenderMode.COMPOSABLE]), renders small
 *                               drag handles on each node's TOP/BOTTOM/LEFT/RIGHT edges. Dragging from a handle
 *                               onto another node invokes [onCreateEdge]; the library does not mutate [GraphState.edges]
 *                               itself, so callers add the edge (typically with the dragged-from [NodePort] as
 *                               [io.karpilabs.graphine.model.GraphEdge.fromPort]) in that callback.
 * @param onCreateEdge Invoked when a port drag is released over another node: `(fromNodeId, fromPort, toNodeId)`.
 * @param nodeContent Composable content for each node. Required for [NodeRenderMode.COMPOSABLE], ignored for [NodeRenderMode.DOT].
 */
@Composable
fun <T> GraphSurface(
    state: GraphState<T>,
    modifier: Modifier = Modifier,
    edgeConfig: EdgeConfig = EdgeConfig(
        color = Color.Gray.copy(alpha = 0.3f),
        width = 2f,
    ),
    showGrid: Boolean = true,
    gridColor: Color? = null,
    zoomFromCenter: Boolean = true,
    enableZoom: Boolean = true,
    enablePanning: Boolean = true,
    enablePathHighlighting: Boolean = true,
    selectionMode: Boolean = false,
    enablePortConnections: Boolean = false,
    nodeRenderMode: NodeRenderMode = NodeRenderMode.COMPOSABLE,
    dotStyle: DotStyle<T> = DotStyle(),
    onNodeClick: ((GraphNode<T>) -> Unit)? = null,
    onNodeLongClick: ((GraphNode<T>) -> Unit)? = null,
    onNodeDragged: ((GraphNode<T>) -> Unit)? = null,
    onNodeDragEnd: ((GraphNode<T>) -> Unit)? = null,
    onCreateEdge: ((fromNodeId: String, fromPort: NodePort, toNodeId: String) -> Unit)? = null,
    nodeContent: (@Composable (node: GraphNode<T>, isDetailVisible: Boolean) -> Unit)? = null,
) {
    require(nodeRenderMode != NodeRenderMode.COMPOSABLE || nodeContent != null) {
        "nodeContent is required when nodeRenderMode is COMPOSABLE"
    }
    val scope = rememberCoroutineScope()
    var surfaceSize by remember { mutableStateOf(Size.Zero) }
    var selectionBoxScreen by remember { mutableStateOf<Rect?>(null) }
    var pendingLink by remember { mutableStateOf<PendingLink?>(null) }
    val resolvedGridColor = gridColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    // IMPORTANT: do NOT assign `state.offset/scale = anim.value` every composition.
    // That races with fitToScreen/snapTo and silently undoes the initial camera frame
    // (animatables lag one frame behind the state fields). Camera fields on GraphState
    // are the source of truth for rendering; animatables follow for inertia/animation.

    val transformState = rememberTransformableState { centroid, zoomChange, offsetChange, _ ->
        val pivot = if (zoomFromCenter || surfaceSize == Size.Zero) {
            Offset(surfaceSize.width / 2f, surfaceSize.height / 2f)
        } else {
            centroid
        }

        val oldScale = state.scale
        val newScale = (state.scale * zoomChange).coerceIn(
            state.config.minScale,
            state.config.maxScale,
        )

        // Calculate translation needed to keep the pivot point stationary in content space
        val contentPivotX = (pivot.x - state.offset.x) / oldScale
        val contentPivotY = (pivot.y - state.offset.y) / oldScale

        var newOffset = Offset(
            pivot.x - (contentPivotX * newScale),
            pivot.y - (contentPivotY * newScale),
        )

        // Add incremental panning
        newOffset += offsetChange

        // Apply boundaries
        newOffset = state.coerceOffset(newOffset, surfaceSize.width, surfaceSize.height, newScale)

        // Update state synchronously to prevent jumps
        state.scale = newScale
        state.offset = newOffset

        scope.launch {
            state.scaleAnim.snapTo(newScale)
            state.offsetAnim.snapTo(newOffset)
        }
    }

    val isDetailVisible = state.scale > state.config.detailZoomThreshold

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onGloballyPositioned { surfaceSize = it.size.toSize() }
            .then(
                if (enableZoom) {
                    Modifier.transformable(state = transformState)
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { state.clearInteractions() })
            }
            .pointerInput(enablePanning, selectionMode) {
                if (selectionMode) {
                    var startScreen = Offset.Zero
                    detectDragGestures(
                        onDragStart = { pos ->
                            startScreen = pos
                            selectionBoxScreen = Rect(pos, pos)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val box = normalizeRect(startScreen, change.position)
                            selectionBoxScreen = box
                            val contentRect = normalizeRect(
                                (box.topLeft - state.offset) / state.scale,
                                (box.bottomRight - state.offset) / state.scale,
                            )
                            state.selectedNodeIds = state.nodesInRect(contentRect)
                        },
                        onDragEnd = { selectionBoxScreen = null },
                        onDragCancel = { selectionBoxScreen = null },
                    )
                    return@pointerInput
                }
                if (!enablePanning) return@pointerInput
                val velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { scope.launch { state.offsetAnim.stop() } },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        val newOffset = state.coerceOffset(
                            state.offset + dragAmount,
                            surfaceSize.width,
                            surfaceSize.height,
                            state.scale,
                        )

                        state.offset = newOffset
                        scope.launch {
                            state.offsetAnim.snapTo(newOffset)
                        }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity()
                        scope.launch {
                            state.offsetAnim.animateDecay(
                                Offset(velocity.x, velocity.y),
                                exponentialDecay(),
                            ) {
                                // Keep public camera state in lockstep with inertia.
                                state.offset = state.coerceOffset(
                                    value,
                                    surfaceSize.width,
                                    surfaceSize.height,
                                    state.scale,
                                )
                            }
                        }
                    },
                )
            },
    ) {
        if (showGrid) {
            GraphBackground(state = state, dotColor = resolvedGridColor)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = state.scale,
                    scaleY = state.scale,
                    translationX = state.offset.x,
                    translationY = state.offset.y,
                ),
        ) {
            // Obstacle-avoidance routing and port anchors only apply in COMPOSABLE mode: computing
            // per-node rects is an O(nodes) scan that's fine for rich-content graphs but wasted
            // work for the 1000+ node DOT-mode graphs this library also targets.
            val routingRects: Map<String, Rect> = if (nodeRenderMode == NodeRenderMode.COMPOSABLE) {
                state.nodeStates.filterKeys { state.isNodeVisible(it) }
                    .mapValues { (_, ns) -> Rect(ns.position, ns.size.toSize()) }
            } else {
                emptyMap()
            }

            // 1. Draw Group Zones
            Canvas(modifier = Modifier.fillMaxSize()) {
                state.groups.forEach { group -> drawGroupZone(group, state) }
            }

            // 2. Draw Edges
            Canvas(modifier = Modifier.fillMaxSize()) {
                state.edges.forEach { edge ->
                    if (!state.isNodeVisible(edge.from) || !state.isNodeVisible(edge.to)) return@forEach
                    val fromCenter = state.getNodeCenter(edge.from)
                    val toCenter = state.getNodeCenter(edge.to)
                    if (fromCenter == Offset.Zero || toCenter == Offset.Zero) return@forEach

                    val bow = if (routingRects.isNotEmpty()) {
                        findObstacleBow(fromCenter, toCenter, edge.from, edge.to, routingRects)
                    } else {
                        null
                    }
                    val fromRect = routingRects[edge.from]
                    val toRect = routingRects[edge.to]
                    val fromPos = when {
                        edge.fromPort != null && fromRect != null -> portAnchor(fromRect, edge.fromPort)
                        fromRect != null -> clipToRectBoundary(fromCenter, bow ?: toCenter, fromRect)
                        else -> fromCenter
                    }
                    val toPos = when {
                        edge.toPort != null && toRect != null -> portAnchor(toRect, edge.toPort)
                        toRect != null -> clipToRectBoundary(toCenter, bow ?: fromCenter, toRect)
                        else -> toCenter
                    }

                    val isHighlighted = state.highlightedEdgeIds.isEmpty() ||
                        state.highlightedEdgeIds.contains(edge.from to edge.to)

                    val pathAlpha = if (isHighlighted) 1.0f else 0.1f
                    val baseColor = edgeConfig.color.copy(alpha = edgeConfig.color.alpha * pathAlpha)

                    when (edgeConfig.style) {
                        EdgeStyle.STRAIGHT -> {
                            if (bow != null) {
                                val path = Path().apply {
                                    moveTo(fromPos.x, fromPos.y)
                                    quadraticTo(bow.x, bow.y, toPos.x, toPos.y)
                                }
                                drawPath(
                                    path = path,
                                    color = baseColor,
                                    style = Stroke(
                                        width = edgeConfig.width,
                                        cap = edgeConfig.strokeCap,
                                        pathEffect = edgeConfig.pathEffect,
                                    ),
                                )
                            } else {
                                drawLine(
                                    color = baseColor,
                                    start = fromPos,
                                    end = toPos,
                                    strokeWidth = edgeConfig.width,
                                    cap = edgeConfig.strokeCap,
                                    pathEffect = edgeConfig.pathEffect,
                                )
                            }
                        }
                        EdgeStyle.CURVED -> {
                            val path = Path().apply {
                                moveTo(fromPos.x, fromPos.y)
                                if (bow != null) {
                                    quadraticTo(bow.x, bow.y, toPos.x, toPos.y)
                                } else {
                                    cubicTo(
                                        fromPos.x,
                                        (fromPos.y + toPos.y) / 2,
                                        toPos.x,
                                        (fromPos.y + toPos.y) / 2,
                                        toPos.x,
                                        toPos.y,
                                    )
                                }
                            }
                            val brush = Brush.linearGradient(
                                colors = listOf(baseColor, baseColor.copy(alpha = 0.1f * pathAlpha)),
                                start = fromPos,
                                end = toPos,
                            )
                            drawPath(
                                path = path,
                                brush = brush,
                                style = Stroke(
                                    width = edgeConfig.width,
                                    cap = edgeConfig.strokeCap,
                                    pathEffect = edgeConfig.pathEffect,
                                ),
                            )
                        }
                        EdgeStyle.ORTHOGONAL -> {
                            val path = Path().apply {
                                moveTo(fromPos.x, fromPos.y)
                                if (kotlin.math.abs(toPos.y - fromPos.y) > kotlin.math.abs(toPos.x - fromPos.x)) {
                                    // Vertical flow: middle-Y split
                                    val midY = (fromPos.y + toPos.y) / 2f
                                    lineTo(fromPos.x, midY)
                                    lineTo(toPos.x, midY)
                                } else {
                                    // Horizontal flow: middle-X split
                                    val midX = (fromPos.x + toPos.x) / 2f
                                    lineTo(midX, fromPos.y)
                                    lineTo(midX, toPos.y)
                                }
                                lineTo(toPos.x, toPos.y)
                            }
                            drawPath(
                                path = path,
                                color = baseColor,
                                style = Stroke(
                                    width = edgeConfig.width,
                                    cap = edgeConfig.strokeCap,
                                    pathEffect = edgeConfig.pathEffect,
                                ),
                            )
                        }
                    }
                    if (edgeConfig.showArrowheads) {
                        // When bowed, point the arrowhead along the curve's incoming tangent (bow -> toPos)
                        // rather than the straight line from fromPos, which would look wrong.
                        val arrowFrom = if (bow != null && edgeConfig.style != EdgeStyle.ORTHOGONAL) bow else fromPos
                        drawArrowhead(toPos, arrowFrom, edgeConfig, pathAlpha)
                    }
                }
            }

            // 3. Nodes
            if (nodeRenderMode == NodeRenderMode.COMPOSABLE) {
                state.nodeStates.forEach { (id, nodeState) ->
                    if (!state.isNodeVisible(id)) return@forEach
                    val isHighlighted = state.highlightedNodeIds.isEmpty() || state.highlightedNodeIds.contains(id)
                    val nodeAlpha = if (isHighlighted) 1.0f else 0.2f

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(nodeState.position.x.roundToInt(), nodeState.position.y.roundToInt()) }
                            .graphicsLayer { alpha = nodeAlpha }
                            .onGloballyPositioned { state.onNodeResized(id, it.size) }
                            .pointerInput(id) {
                                detectTapGestures(
                                    onTap = {
                                        if (enablePathHighlighting) state.highlightPath(id)
                                        onNodeClick?.invoke(nodeState.node)
                                    },
                                    onLongPress = {
                                        state.toggleCollapse(id)
                                        onNodeLongClick?.invoke(nodeState.node)
                                    },
                                )
                            }
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragEnd = { onNodeDragEnd?.invoke(nodeState.node) },
                                    onDragCancel = { onNodeDragEnd?.invoke(nodeState.node) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        state.onNodeDragged(id, dragAmount / state.scale)
                                        onNodeDragged?.invoke(nodeState.node)
                                    },
                                )
                            },
                    ) {
                        nodeContent?.invoke(nodeState.node, isDetailVisible)
                    }
                }

                if (enablePortConnections) {
                    val portColor = MaterialTheme.colorScheme.primary
                    state.nodeStates.forEach { (id, nodeState) ->
                        if (!state.isNodeVisible(id)) return@forEach
                        val rect = routingRects[id] ?: return@forEach
                        NodePort.entries.forEach { port ->
                            val anchor = portAnchor(rect, port)
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (anchor.x - PORT_HANDLE_RADIUS_PX).roundToInt(),
                                            (anchor.y - PORT_HANDLE_RADIUS_PX).roundToInt(),
                                        )
                                    }
                                    .size(PORT_HANDLE_SIZE)
                                    .background(portColor, CircleShape)
                                    .pointerInput(id, port) {
                                        detectDragGestures(
                                            onDragStart = { pendingLink = PendingLink(id, port, anchor, anchor) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                pendingLink = pendingLink?.let {
                                                    it.copy(current = it.current + dragAmount / state.scale)
                                                }
                                            },
                                            onDragEnd = {
                                                val link = pendingLink
                                                pendingLink = null
                                                val target = link?.let { l ->
                                                    routingRects.entries.firstOrNull { (rid, r) ->
                                                        rid != id && r.contains(l.current)
                                                    }?.key
                                                }
                                                if (link != null && target != null) {
                                                    onCreateEdge?.invoke(id, port, target)
                                                }
                                            },
                                            onDragCancel = { pendingLink = null },
                                        )
                                    },
                            )
                        }
                    }

                    pendingLink?.let { link ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = portColor,
                                start = link.anchor,
                                end = link.current,
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                            )
                        }
                    }
                }
            } else {
                // DOT mode: single Canvas pass for all dots.
                // Pointer handling must NOT consume empty-space drags — those belong to the
                // parent pan handler. Only claim the gesture when the pointer is on a node.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(dotStyle, enablePathHighlighting, enablePanning) {
                            // This Canvas sits above the parent pan handler and would otherwise
                            // swallow every drag. Handle both cases here:
                            //  - pointer on a node  → drag that node
                            //  - empty space        → pan the viewport
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val hit = findNearestNodeAtPosition(down.position, state, dotStyle)

                                if (hit == null) {
                                    if (!enablePanning) return@awaitEachGesture
                                    // Empty-space pan. Local deltas are in content space
                                    // (inside graphicsLayer); convert to screen space for offset.
                                    scope.launch { state.offsetAnim.stop() }
                                    val velocityTracker = VelocityTracker()
                                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.changedToUpIgnoreConsumed()) {
                                            val velocity = velocityTracker.calculateVelocity()
                                            // Velocity is in content space; convert to screen space.
                                            val screenVel = Offset(
                                                velocity.x * state.scale,
                                                velocity.y * state.scale,
                                            )
                                            scope.launch {
                                                state.offsetAnim.animateDecay(screenVel, exponentialDecay())
                                            }
                                            break
                                        }
                                        if (change.pressed && change.positionChanged()) {
                                            change.consume()
                                            val contentDelta = change.position - change.previousPosition
                                            val screenDelta = contentDelta * state.scale
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val newOffset = state.coerceOffset(
                                                state.offset + screenDelta,
                                                surfaceSize.width,
                                                surfaceSize.height,
                                                state.scale,
                                            )
                                            state.offset = newOffset
                                            scope.launch { state.offsetAnim.snapTo(newOffset) }
                                        }
                                    }
                                    return@awaitEachGesture
                                }

                                val (nodeId, node) = hit
                                var dragging = false
                                val touchSlop = viewConfiguration.touchSlop
                                var totalDrag = 0f

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (change.changedToUpIgnoreConsumed()) {
                                        if (dragging) {
                                            onNodeDragEnd?.invoke(node)
                                        } else {
                                            if (enablePathHighlighting) state.highlightPath(nodeId)
                                            onNodeClick?.invoke(node)
                                        }
                                        break
                                    }

                                    if (change.pressed && change.positionChanged()) {
                                        val delta = change.position - change.previousPosition
                                        totalDrag += delta.getDistance()
                                        if (!dragging && totalDrag >= touchSlop) {
                                            dragging = true
                                        }
                                        if (dragging) {
                                            change.consume()
                                            // Local coords are content-space (inside graphicsLayer).
                                            state.onNodeDragged(nodeId, delta)
                                            onNodeDragged?.invoke(node)
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    // Draw all dots in this Canvas pass
                    state.nodeStates.forEach { (id, nodeState) ->
                        if (!state.isNodeVisible(id)) return@forEach

                        val degree = state.nodeDegrees[id] ?: 0
                        val radius = dotStyle.computeRadius(degree)
                        val dotColor = dotStyle.color(nodeState.node, degree)

                        val isHighlighted = state.highlightedNodeIds.isEmpty() || state.highlightedNodeIds.contains(id)
                        val nodeAlpha = if (isHighlighted) 1.0f else 0.2f

                        drawCircle(
                            color = dotColor.copy(alpha = dotColor.alpha * nodeAlpha),
                            radius = radius,
                            center = nodeState.position,
                        )
                    }
                }
            }
        }

        selectionBoxScreen?.let { box ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val selectionColor = Color(0xFF2196F3)
                drawRect(color = selectionColor.copy(alpha = 0.12f), topLeft = box.topLeft, size = box.size)
                drawRect(color = selectionColor, topLeft = box.topLeft, size = box.size, style = Stroke(width = 1.5f))
            }
        }
    }
}

/** Normalizes two arbitrary corner points into a [Rect] with non-negative width/height. */
private fun normalizeRect(a: Offset, b: Offset): Rect = Rect(
    left = minOf(a.x, b.x),
    top = minOf(a.y, b.y),
    right = maxOf(a.x, b.x),
    bottom = maxOf(a.y, b.y),
)

private fun DrawScope.drawArrowhead(to: Offset, from: Offset, config: EdgeConfig, alpha: Float = 1f) {
    // Security guard: Validate finite offsets and non-zero direction vectors to prevent corrupted path drawing
    if (!to.x.isFinite() || !to.y.isFinite() || !from.x.isFinite() || !from.y.isFinite()) return
    val dx = to.x - from.x
    val dy = to.y - from.y
    if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return
    val angle = atan2(dy, dx)
    if (!angle.isFinite()) return
    val size = if (config.arrowheadSize.isFinite() && config.arrowheadSize > 0f) config.arrowheadSize else 10f
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - size * cos(angle - 0.5f), to.y - size * sin(angle - 0.5f))
        lineTo(to.x - size * cos(angle + 0.5f), to.y - size * sin(angle + 0.5f))
        close()
    }
    drawPath(path, config.color.copy(alpha = config.color.alpha * alpha))
}

private fun DrawScope.drawGroupZone(group: GraphGroup, state: GraphState<*>) {
    val nodePositions = group.nodeIds
        .filter { state.isNodeVisible(it) }
        .mapNotNull { state.nodeStates[it]?.position }
        .filter { it.x.isFinite() && it.y.isFinite() }
    if (nodePositions.isEmpty()) return

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    nodePositions.forEach { pos ->
        minX = minOf(minX, pos.x)
        minY = minOf(minY, pos.y)
        maxX = maxOf(maxX, pos.x)
        maxY = maxOf(maxY, pos.y)
    }

    val padding = 120f
    val rect = Rect(minX - padding, minY - padding, maxX + padding, maxY + padding)

    drawRoundRect(color = group.color.copy(alpha = 0.03f), topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(32f))
    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.2f),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(32f),
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)),
    )
}

/**
 * Finds the nearest node to a pointer position in DOT mode.
 *
 * The dot [Canvas] lives inside the content [graphicsLayer], so [localPosition] is already
 * in content space (node positions). No inverse scale/offset is required.
 *
 * @param localPosition Pointer position in content coordinates.
 * @param state The graph state.
 * @param dotStyle The dot styling configuration.
 * @return A pair of (nodeId, GraphNode) if a node is within hit distance, or null.
 */
private fun <T> findNearestNodeAtPosition(
    localPosition: Offset,
    state: GraphState<T>,
    dotStyle: DotStyle<T>,
): Pair<String, GraphNode<T>>? {
    var nearestNode: Pair<String, GraphNode<T>>? = null
    var nearestDistance = Float.MAX_VALUE

    state.nodeStates.forEach { (id, nodeState) ->
        if (!state.isNodeVisible(id)) return@forEach

        val degree = state.nodeDegrees[id] ?: 0
        val radius = dotStyle.computeRadius(degree)

        val dx = localPosition.x - nodeState.position.x
        val dy = localPosition.y - nodeState.position.y
        val distance = sqrt(dx * dx + dy * dy)

        // Tight enough that empty space remains pannable in dense graphs.
        val hitDistance = (radius * 2.5f).coerceAtLeast(8f)

        if (distance < hitDistance && distance < nearestDistance) {
            nearestDistance = distance
            nearestNode = id to nodeState.node
        }
    }

    return nearestNode
}
