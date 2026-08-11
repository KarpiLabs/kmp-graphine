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

package io.karpilabs.graphine

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.karpilabs.graphine.model.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The central state manager for KmpGraphine.
 *
 * Handles node positions, edge definitions, viewport transformations,
 * and user interactions like selection and folding.
 */
class GraphState<T>(
    initialNodes: List<GraphNode<T>> = emptyList(),
    initialEdges: List<GraphEdge> = emptyList(),
    initialGroups: List<GraphGroup> = emptyList(),
    initialConfig: GraphConfig = GraphConfig(),
) {
    private val _nodeStates = mutableStateMapOf<String, GraphNodeState<T>>().apply {
        initialNodes.forEach { put(it.id, GraphNodeState(it)) }
    }

    /**
     * Map of [GraphNode.id] to its current visual state (position, size).
     */
    val nodeStates: Map<String, GraphNodeState<T>> get() = _nodeStates

    /** The list of edges connecting nodes. */
    var edges by mutableStateOf(initialEdges)

    /** Logical clusters of nodes. */
    var groups by mutableStateOf(initialGroups)

    /** Global visual / interaction settings (zoom thresholds, padding, etc.). */
    var config by mutableStateOf(initialConfig)

    /** The current zoom level (0.1 to 5.0). */
    var scale by mutableFloatStateOf(1f)

    /** The current pan offset of the canvas. */
    var offset by mutableStateOf(Offset.Zero)

    /**
     * Map of [GraphNode.id] to the node's degree (total in + out edges).
     * Recomputed whenever edges change.
     */
    val nodeDegrees: Map<String, Int> by derivedStateOf { computeNodeDegrees() }

    // Animation controllers
    internal val scaleAnim = Animatable(1f)
    internal val offsetAnim = Animatable(Offset.Zero, Offset.VectorConverter)

    /**
     * Instantly set the camera. Animatables are snapped first so a concurrent
     * composition cannot read stale anim values and clobber the new frame.
     */
    suspend fun snapTo(targetScale: Float, targetOffset: Offset) {
        val s = targetScale.coerceIn(config.minScale, config.maxScale)
        scaleAnim.snapTo(s)
        offsetAnim.snapTo(targetOffset)
        scale = s
        offset = targetOffset
    }

    /**
     * Calculates the bounding box of all visible nodes.
     */
    fun getContentBounds(): Rect {
        if (_nodeStates.isEmpty()) return Rect(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        _nodeStates.values.forEach { state ->
            minX = minOf(minX, state.position.x)
            minY = minOf(minY, state.position.y)
            maxX = maxOf(maxX, state.position.x + state.size.width)
            maxY = maxOf(maxY, state.position.y + state.size.height)
        }
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Constraints an offset to ensure the content remains within the viewport.
     */
    fun coerceOffset(offset: Offset, viewportWidth: Float, viewportHeight: Float, scale: Float): Offset {
        val bounds = getContentBounds()
        if (bounds.isEmpty) return offset

        // The content area in transformed space
        val contentLeft = bounds.left * scale + offset.x
        val contentRight = bounds.right * scale + offset.x
        val contentTop = bounds.top * scale + offset.y
        val contentBottom = bounds.bottom * scale + offset.y

        val padding = config.viewportPadding // Use dynamic padding

        var newX = offset.x
        var newY = offset.y

        // X constraints: Ensure at least some of the content is visible
        if (contentRight < padding) {
            newX = padding - (bounds.right * scale)
        } else if (contentLeft > viewportWidth - padding) {
            newX = (viewportWidth - padding) - (bounds.left * scale)
        }

        // Y constraints
        if (contentBottom < padding) {
            newY = padding - (bounds.bottom * scale)
        } else if (contentTop > viewportHeight - padding) {
            newY = (viewportHeight - padding) - (bounds.top * scale)
        }

        return Offset(newX, newY)
    }

    /** Optional grid size for snapping (0 = disabled). */
    var snapGridSize by mutableFloatStateOf(0f)

    /** The primary node ID for focus/centering. */
    var targetId: String? by mutableStateOf(null)

    /** IDs of currently selected nodes. */
    var selectedNodeIds by mutableStateOf(setOf<String>())

    /** IDs of nodes currently in a highlighted path. */
    var highlightedNodeIds by mutableStateOf(setOf<String>())

    /** IDs of edges currently highlighted. */
    var highlightedEdgeIds by mutableStateOf(setOf<Pair<String, String>>())

    /** IDs of nodes whose children are hidden. */
    var collapsedNodeIds by mutableStateOf(setOf<String>())

    /**
     * Updates the position of a node, respecting [snapGridSize] if enabled.
     */
    fun onNodeDragged(nodeId: String, delta: Offset) {
        val current = _nodeStates[nodeId] ?: return
        var newPos = current.position + delta

        if (snapGridSize > 0) {
            newPos = Offset(
                (newPos.x / snapGridSize).roundToInt() * snapGridSize,
                (newPos.y / snapGridSize).roundToInt() * snapGridSize,
            )
        }

        _nodeStates[nodeId] = current.copy(position = newPos)
    }

    /**
     * Records the rendered size of a node.
     */
    fun onNodeResized(nodeId: String, size: IntSize) {
        val current = _nodeStates[nodeId] ?: return
        if (current.size != size) {
            _nodeStates[nodeId] = current.copy(size = size)
        }
    }

    /**
     * Calculates the absolute center of a node in canvas space.
     */
    fun getNodeCenter(nodeId: String): Offset {
        val state = _nodeStates[nodeId] ?: return Offset.Zero
        return state.position + Offset(
            state.size.width / 2f,
            state.size.height / 2f,
        )
    }

    /**
     * Toggles the visibility of a node's descendants.
     */
    fun toggleCollapse(nodeId: String) {
        collapsedNodeIds = if (collapsedNodeIds.contains(nodeId)) {
            collapsedNodeIds - nodeId
        } else {
            collapsedNodeIds + nodeId
        }
    }

    /**
     * Checks if a node is currently hidden due to an ancestor being collapsed.
     *
     * Walks the "parent" chain via edges (`to == current`). Safe for cyclic graphs:
     * visited nodes are tracked so a cycle cannot cause an infinite loop.
     */
    fun isNodeVisible(nodeId: String): Boolean {
        // Fast path: nothing collapsed → every node is visible (critical for 1000+ node DOT graphs).
        if (collapsedNodeIds.isEmpty()) return true

        val visited = mutableSetOf<String>()
        var current: String? = edges.find { it.to == nodeId }?.from
        while (current != null) {
            if (!visited.add(current)) break // cycle detected
            if (collapsedNodeIds.contains(current)) return false
            current = edges.find { it.to == current }?.from
        }
        return true
    }

    /**
     * Animates the camera to focus on a specific node.
     */
    suspend fun centerOnNodeAnimated(nodeId: String, viewportWidth: Float, viewportHeight: Float) {
        val nodePos = _nodeStates[nodeId]?.position ?: return
        val targetScale = 1.2f
        val targetOffset = Offset(
            (viewportWidth / 2) - (nodePos.x * targetScale),
            (viewportHeight / 2) - (nodePos.y * targetScale),
        )
        animateTo(targetScale, targetOffset, viewportWidth, viewportHeight)
    }

    /**
     * Animates the camera to a multi-step "flight" to a specific node.
     */
    suspend fun flyToNodeAnimated(
        nodeId: String,
        viewportWidth: Float,
        viewportHeight: Float,
    ) = coroutineScope {
        animateTo(0.8f, offset, viewportWidth, viewportHeight) // Zoom out for context
        centerOnNodeAnimated(nodeId, viewportWidth, viewportHeight)
    }

    /**
     * Smoothly transitions scale and offset over time.
     */
    suspend fun animateTo(targetScale: Float, targetOffset: Offset, viewportWidth: Float = 0f, viewportHeight: Float = 0f) = coroutineScope {
        val finalOffset = if (viewportWidth > 0 && viewportHeight > 0) {
            coerceOffset(targetOffset, viewportWidth, viewportHeight, targetScale)
        } else {
            targetOffset
        }

        launch {
            scaleAnim.animateTo(targetScale.coerceIn(0.1f, 5f), tween(500)) {
                scale = value
            }
        }
        launch {
            offsetAnim.animateTo(finalOffset, tween(500)) {
                offset = value
            }
        }
    }

    /**
     * Adjusts the viewport so the graph content fits and is centered.
     *
     * @param viewportWidth Visible canvas width in pixels.
     * @param viewportHeight Visible canvas height in pixels.
     * @param padding Extra space around the fitted bounds (content units after scale).
     * @param trimFraction Fraction of extreme positions to ignore on each side (0–0.45).
     *   Use a small value (e.g. 0.05–0.1) to frame the main cluster while discarding
     *   sparse outer outliers (orphan rings, far leaves). 0 keeps full min/max bounds.
     * @param immediate When true, snaps the camera with no animation (best for first paint).
     */
    suspend fun fitToScreenAnimated(
        viewportWidth: Float,
        viewportHeight: Float,
        padding: Float = config.fitToScreenPadding,
        trimFraction: Float = 0f,
        immediate: Boolean = false,
    ) {
        if (_nodeStates.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return

        val bounds = computeFitBounds(trimFraction) ?: return
        if (bounds.width <= 0f && bounds.height <= 0f) return

        // Ensure degenerate / single-point layouts still get a usable frame.
        val minSpan = 80f
        val spanX = bounds.width.coerceAtLeast(minSpan)
        val spanY = bounds.height.coerceAtLeast(minSpan)
        val contentWidth = spanX + padding * 2f
        val contentHeight = spanY + padding * 2f

        val targetScale = minOf(
            viewportWidth / contentWidth,
            viewportHeight / contentHeight,
        ).coerceIn(config.minScale, config.maxScale.coerceAtMost(3f))

        val centerX = bounds.left + bounds.width / 2f
        val centerY = bounds.top + bounds.height / 2f
        val targetOffset = Offset(
            (viewportWidth / 2f) - (centerX * targetScale),
            (viewportHeight / 2f) - (centerY * targetScale),
        )

        // Don't run coerceOffset here — it uses full content bounds (including outliers)
        // and can undo a carefully trimmed, centered fit.
        if (immediate) {
            snapTo(targetScale, targetOffset)
        } else {
            coroutineScope {
                launch {
                    scaleAnim.animateTo(targetScale, tween(500)) { scale = value }
                }
                launch {
                    offsetAnim.animateTo(targetOffset, tween(500)) { offset = value }
                }
            }
        }
    }

    /**
     * Bounding box used by [fitToScreenAnimated].
     * When [trimFraction] > 0, uses independent X/Y percentiles so sparse outer nodes
     * (e.g. a decorative ring) do not force the camera to zoom out too far.
     */
    fun computeFitBounds(trimFraction: Float = 0f): Rect? {
        val points = _nodeStates.values.map { it.position }
        if (points.isEmpty()) return null

        val trim = trimFraction.coerceIn(0f, 0.45f)
        if (trim <= 0f || points.size < 8) {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            points.forEach { p ->
                minX = minOf(minX, p.x)
                minY = minOf(minY, p.y)
                maxX = maxOf(maxX, p.x)
                maxY = maxOf(maxY, p.y)
            }
            return Rect(minX, minY, maxX, maxY)
        }

        val xs = points.map { it.x }.sorted()
        val ys = points.map { it.y }.sorted()
        val last = xs.lastIndex
        val lo = (trim * last).toInt().coerceIn(0, last)
        val hi = ((1f - trim) * last).toInt().coerceIn(lo, last)
        return Rect(xs[lo], ys[lo], xs[hi], ys[hi])
    }

    /**
     * Highlights the unique ownership path (ancestors/descendants) for a node.
     */
    fun highlightPath(nodeId: String?) {
        if (nodeId == null) {
            highlightedNodeIds = emptySet()
            highlightedEdgeIds = emptySet()
            return
        }

        val nodes = mutableSetOf<String>()
        val edges = mutableSetOf<Pair<String, String>>()
        nodes.add(nodeId)

        // Walk ancestors; stop if we re-visit a node (handles cyclic graphs).
        var current: String? = nodeId
        while (current != null) {
            val parentEdge = this.edges.find { it.to == current }
            if (parentEdge != null && nodes.add(parentEdge.from)) {
                edges.add(parentEdge.from to parentEdge.to)
                current = parentEdge.from
            } else {
                current = null
            }
        }

        fun findDescendants(id: String) {
            this.edges.filter { it.from == id }.forEach { edge ->
                if (nodes.add(edge.to)) {
                    edges.add(edge.from to edge.to)
                    findDescendants(edge.to)
                }
            }
        }
        findDescendants(nodeId)

        highlightedNodeIds = nodes
        highlightedEdgeIds = edges
    }

    /**
     * Resets all visual focus, highlights, and selections.
     */
    fun clearInteractions() {
        selectedNodeIds = emptySet()
        highlightedNodeIds = emptySet()
        highlightedEdgeIds = emptySet()
    }

    /**
     * Manually update the position of multiple nodes.
     * Skips entries whose position is unchanged to reduce snapshot churn on large graphs.
     */
    fun setNodePositions(positions: Map<String, Offset>) {
        positions.forEach { (id, pos) ->
            val current = _nodeStates[id] ?: return@forEach
            if (current.position != pos) {
                _nodeStates[id] = current.copy(position = pos)
            }
        }
    }

    /**
     * Computes the degree (in + out edge count) for each node.
     */
    private fun computeNodeDegrees(): Map<String, Int> {
        val degrees = mutableMapOf<String, Int>()
        edges.forEach { edge ->
            degrees[edge.from] = (degrees[edge.from] ?: 0) + 1
            degrees[edge.to] = (degrees[edge.to] ?: 0) + 1
        }
        return degrees
    }
}

/**
 * Creates and remembers a [GraphState] instance.
 */
@Composable
fun <T> rememberGraphState(
    nodes: List<GraphNode<T>> = emptyList(),
    edges: List<GraphEdge> = emptyList(),
    groups: List<GraphGroup> = emptyList(),
    config: GraphConfig = GraphConfig(),
): GraphState<T> = remember(nodes, edges, groups) {
    GraphState(nodes, edges, groups, config)
}
