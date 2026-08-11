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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.GraphGroup
import io.karpilabs.graphine.model.GraphNode
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The main UI entry point for KmpGraphine.
 *
 * Provides a high-performance, zoomable, and interactive canvas for visualizing nodes and edges.
 * Supports inertia panning, group zones, and semantic zoom.
 */
@Composable
fun <T> GraphSurface(
    state: GraphState<T>,
    modifier: Modifier = Modifier,
    edgeConfig: EdgeConfig = EdgeConfig(
        color = Color.Gray.copy(alpha = 0.3f),
        width = 2f
    ),
    showGrid: Boolean = true,
    gridColor: Color? = null,
    zoomFromCenter: Boolean = true,
    enableZoom: Boolean = true,
    enablePanning: Boolean = true,
    enablePathHighlighting: Boolean = true,
    onNodeClick: ((GraphNode<T>) -> Unit)? = null,
    onNodeLongClick: ((GraphNode<T>) -> Unit)? = null,
    nodeContent: @Composable (node: GraphNode<T>, isDetailVisible: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var surfaceSize by remember { mutableStateOf(Size.Zero) }
    val resolvedGridColor = gridColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    
    // Sync state values with animatables
    state.offset = state.offsetAnim.value
    state.scale = state.scaleAnim.value

    val transformState = rememberTransformableState { centroid, zoomChange, offsetChange, _ ->
        val pivot = if (zoomFromCenter || surfaceSize == Size.Zero) {
            Offset(surfaceSize.width / 2f, surfaceSize.height / 2f)
        } else {
            centroid
        }
        
        val oldScale = state.scale
        val newScale = (state.scale * zoomChange).coerceIn(0.1f, 5f)

        // Calculate translation needed to keep the pivot point stationary in content space
        val contentPivotX = (pivot.x - state.offset.x) / oldScale
        val contentPivotY = (pivot.y - state.offset.y) / oldScale
        
        var newOffset = Offset(
            pivot.x - (contentPivotX * newScale),
            pivot.y - (contentPivotY * newScale)
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

    val isDetailVisible = state.scale > 0.6f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onGloballyPositioned { surfaceSize = it.size.toSize() }
            .then(
                if (enableZoom) Modifier.transformable(state = transformState)
                else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { state.clearInteractions() })
            }
            .pointerInput(enablePanning) {
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
                            state.scale
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
                                exponentialDecay()
                            )
                        }
                    }
                )
            }
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
                    translationY = state.offset.y
                )
        ) {
            // 1. Draw Group Zones
            Canvas(modifier = Modifier.fillMaxSize()) {
                state.groups.forEach { group -> drawGroupZone(group, state) }
            }

            // 2. Draw Edges
            Canvas(modifier = Modifier.fillMaxSize()) {
                state.edges.forEach { edge ->
                    if (!state.isNodeVisible(edge.from) || !state.isNodeVisible(edge.to)) return@forEach
                    val fromPos = state.getNodeCenter(edge.from); val toPos = state.getNodeCenter(edge.to)
                    if (fromPos == Offset.Zero || toPos == Offset.Zero) return@forEach

                    val isHighlighted = state.highlightedEdgeIds.isEmpty() || 
                                       state.highlightedEdgeIds.contains(edge.from to edge.to)
                    
                    val pathAlpha = if (isHighlighted) 1.0f else 0.1f
                    val path = Path().apply {
                        moveTo(fromPos.x, fromPos.y)
                        cubicTo(fromPos.x, (fromPos.y + toPos.y) / 2, toPos.x, (fromPos.y + toPos.y) / 2, toPos.x, toPos.y)
                    }

                    val baseColor = edgeConfig.color.copy(alpha = edgeConfig.color.alpha * pathAlpha)
                    val brush = Brush.linearGradient(
                        colors = listOf(baseColor, baseColor.copy(alpha = 0.1f * pathAlpha)),
                        start = fromPos, end = toPos
                    )

                    drawPath(path = path, brush = brush, style = Stroke(width = edgeConfig.width, cap = StrokeCap.Round, pathEffect = edgeConfig.pathEffect))
                    if (edgeConfig.showArrowheads) drawArrowhead(toPos, fromPos, edgeConfig, pathAlpha)
                }
            }

            // 3. Nodes
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
                                }
                            )
                        }
                        .pointerInput(id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                state.onNodeDragged(id, dragAmount / state.scale)
                            }
                        }
                ) {
                    nodeContent(nodeState.node, isDetailVisible)
                }
            }
        }
    }
}

private fun DrawScope.drawArrowhead(to: Offset, from: Offset, config: EdgeConfig, alpha: Float = 1f) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val size = config.arrowheadSize
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - size * cos(angle - 0.5f), to.y - size * sin(angle - 0.5f))
        lineTo(to.x - size * cos(angle + 0.5f), to.y - size * sin(angle + 0.5f))
        close()
    }
    drawPath(path, config.color.copy(alpha = config.color.alpha * alpha))
}

private fun DrawScope.drawGroupZone(group: GraphGroup, state: GraphState<*>) {
    val nodePositions = group.nodeIds.filter { state.isNodeVisible(it) }.mapNotNull { state.nodeStates[it]?.position }
    if (nodePositions.isEmpty()) return

    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE

    nodePositions.forEach { pos ->
        minX = minOf(minX, pos.x); minY = minOf(minY, pos.y)
        maxX = maxOf(maxX, pos.x); maxY = maxOf(maxY, pos.y)
    }

    val padding = 120f
    val rect = Rect(minX - padding, minY - padding, maxX + padding, maxY + padding)

    drawRoundRect(color = group.color.copy(alpha = 0.03f), topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(32f))
    drawRoundRect(color = Color.Gray.copy(alpha = 0.2f), topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(32f),
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)))
}
