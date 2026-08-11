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
    val config: GraphConfig = GraphConfig()
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

    /** The current zoom level (0.1 to 5.0). */
    var scale by mutableFloatStateOf(1f)
    
    /** The current pan offset of the canvas. */
    var offset by mutableStateOf(Offset.Zero)

    // Animation controllers
    internal val scaleAnim = Animatable(1f)
    internal val offsetAnim = Animatable(Offset.Zero, Offset.VectorConverter)

    suspend fun snapTo(targetScale: Float, targetOffset: Offset) {
        scale = targetScale
        offset = targetOffset
        coroutineScope {
            launch { scaleAnim.snapTo(targetScale) }
            launch { offsetAnim.snapTo(targetOffset) }
        }
    }

    /**
     * Calculates the bounding box of all visible nodes.
     */
    fun getContentBounds(): Rect {
        if (_nodeStates.isEmpty()) return Rect(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE

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
                (newPos.y / snapGridSize).roundToInt() * snapGridSize
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
            state.size.height / 2f
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
     */
    fun isNodeVisible(nodeId: String): Boolean {
        var current: String? = edges.find { it.to == nodeId }?.from
        while (current != null) {
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
            (viewportHeight / 2) - (nodePos.y * targetScale)
        )
        animateTo(targetScale, targetOffset, viewportWidth, viewportHeight)
    }

    /**
     * Animates the camera to a multi-step "flight" to a specific node.
     */
    suspend fun flyToNodeAnimated(
        nodeId: String,
        viewportWidth: Float,
        viewportHeight: Float
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
     * Adjusts the viewport to show all nodes with optional padding.
     */
    suspend fun fitToScreenAnimated(
        viewportWidth: Float, 
        viewportHeight: Float, 
        padding: Float = config.fitToScreenPadding
    ) {
        if (_nodeStates.isEmpty()) return
        
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE

        _nodeStates.values.forEach { 
            minX = minOf(minX, it.position.x)
            minY = minOf(minY, it.position.y)
            maxX = maxOf(maxX, it.position.x)
            maxY = maxOf(maxY, it.position.y)
        }

        val contentWidth = (maxX - minX) + (padding * 2)
        val contentHeight = (maxY - minY) + (padding * 2)
        
        val targetScale = minOf(viewportWidth / contentWidth, viewportHeight / contentHeight).coerceIn(0.1f, 2.0f)
        val centerX = (minX + maxX) / 2
        val centerY = (minY + maxY) / 2
        
        val targetOffset = Offset(
            (viewportWidth / 2) - (centerX * targetScale),
            (viewportHeight / 2) - (centerY * targetScale)
        )
        
        animateTo(targetScale, targetOffset, viewportWidth, viewportHeight)
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

        val nodes = mutableSetOf<String>(); val edges = mutableSetOf<Pair<String, String>>()
        nodes.add(nodeId)

        var current: String? = nodeId
        while (current != null) {
            val parentEdge = this.edges.find { it.to == current }
            if (parentEdge != null) {
                nodes.add(parentEdge.from)
                edges.add(parentEdge.from to parentEdge.to)
                current = parentEdge.from
            } else { current = null }
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
     */
    fun setNodePositions(positions: Map<String, Offset>) {
        positions.forEach { (id, pos) ->
            val current = _nodeStates[id] ?: return@forEach
            _nodeStates[id] = current.copy(position = pos)
        }
    }
}

/**
 * Creates and remembers a [GraphState] instance.
 */
@Composable
fun <T> rememberGraphState(
    nodes: List<GraphNode<T>> = emptyList(),
    edges: List<GraphEdge> = emptyList(),
    groups: List<GraphGroup> = emptyList()
): GraphState<T> {
    return remember(nodes, edges, groups) { GraphState(nodes, edges, groups) }
}
