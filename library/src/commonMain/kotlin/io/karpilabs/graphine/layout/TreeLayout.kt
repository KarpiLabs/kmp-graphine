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

package io.karpilabs.graphine.layout

import androidx.compose.ui.geometry.Offset
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import kotlin.math.*

enum class TreeLayoutMode {
    STRAIGHT,
    RADIAL,
}

class TreeLayout(
    private val horizontalSpacing: Float = 450f,
    private val verticalSpacing: Float = 550f,
    private val mode: TreeLayoutMode = TreeLayoutMode.STRAIGHT,
) : GraphLayout {

    private val safeHSpacing = if (horizontalSpacing.isFinite() && horizontalSpacing > 0f) horizontalSpacing else 450f
    private val safeVSpacing = if (verticalSpacing.isFinite() && verticalSpacing > 0f) verticalSpacing else 550f

    override fun <T> calculatePositions(
        nodes: List<GraphNode<T>>,
        edges: List<GraphEdge>,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Map<String, Offset> {
        val safeViewportWidth = if (viewportWidth.isFinite() && viewportWidth > 0f) viewportWidth else 1000f
        val positions = mutableMapOf<String, Offset>()
        val adjacency = edges.groupBy({ it.from }, { it.to })
        val roots = nodes.filter { node -> edges.none { it.to == node.id } }
        val startNodes = if (roots.isNotEmpty()) roots else listOf(nodes.firstOrNull() ?: return emptyMap())

        startNodes.forEachIndexed { index, root ->
            val startX = (safeViewportWidth / (startNodes.size + 1)) * (index + 1)
            layoutNode(root.id, startX, 100f, adjacency, positions, 0)
        }
        return positions
    }

    private fun layoutNode(
        id: String,
        x: Float,
        y: Float,
        adj: Map<String, List<String>>,
        pos: MutableMap<String, Offset>,
        depth: Int,
    ) {
        if (pos.containsKey(id)) return
        val safeX = if (x.isFinite()) x else 0f
        val safeY = if (y.isFinite()) y else 0f
        pos[id] = Offset(safeX, safeY)

        val children = adj[id] ?: return
        if (children.isEmpty()) return

        if (mode == TreeLayoutMode.RADIAL) {
            layoutRadial(id, x, y, children, adj, pos, depth)
        } else {
            layoutStraight(id, x, y, children, adj, pos, depth)
        }
    }

    private fun layoutStraight(
        id: String,
        x: Float,
        y: Float,
        children: List<String>,
        adj: Map<String, List<String>>,
        pos: MutableMap<String, Offset>,
        depth: Int,
    ) {
        // Dynamic maxPerRow: For small clusters, keep them wide. For large clusters, make them square-ish.
        val maxPerRow = if (children.size <= 3) {
            children.size
        } else {
            ceil(sqrt(children.size.toDouble())).toInt().coerceAtLeast(3)
        }

        val currentHSpacing = safeHSpacing * (1f / (depth * 0.1f + 1f)).coerceAtLeast(0.7f)
        val currentVSpacing = safeVSpacing

        if (children.size > maxPerRow) {
            val gridYStart = y + currentVSpacing
            children.forEachIndexed { index, childId ->
                val row = index / maxPerRow
                val col = index % maxPerRow
                val rowSize = minOf(maxPerRow, children.size - (row * maxPerRow))
                val rowWidth = (rowSize - 1) * currentHSpacing
                val startX = x - rowWidth / 2
                val childX = startX + (col * currentHSpacing)
                val childY = gridYStart + (row * currentVSpacing)
                layoutNode(childId, childX, childY, adj, pos, depth + 1)
            }
        } else {
            val totalWidth = (children.size - 1) * currentHSpacing
            var currentX = x - totalWidth / 2
            children.forEach { childId ->
                layoutNode(childId, currentX, y + currentVSpacing, adj, pos, depth + 1)
                currentX += currentHSpacing
            }
        }
    }

    private fun layoutRadial(
        id: String,
        x: Float,
        y: Float,
        children: List<String>,
        adj: Map<String, List<String>>,
        pos: MutableMap<String, Offset>,
        depth: Int,
    ) {
        // Radial strategy: Spread nodes along an arc below the parent
        // As rows increase, radius increases and more nodes fit.
        val baseRadius = safeVSpacing
        val nodesPerArc = 5 + (depth * 2) // Outer arcs can hold more nodes

        children.chunked(nodesPerArc).forEachIndexed { arcIndex, arcChildren ->
            val radius = baseRadius + (arcIndex * safeVSpacing * 0.6f)
            val angleRange = PI * 0.8 // 144 degree arc centered downwards
            val startAngle = (PI - angleRange) / 2 + PI // Start angle to point down

            val angleStep = if (arcChildren.size > 1) angleRange / (arcChildren.size - 1) else 0.0

            arcChildren.forEachIndexed { index, childId ->
                val angle = startAngle + (index * angleStep)
                val childX = x + (radius * cos(angle)).toFloat()
                val childY = y - (radius * sin(angle)).toFloat() // Y grows down, but sin is up, so subtract

                // Adjusting childY to ensure it's always below parent
                val finalChildY = max(y + 100f, childY)

                layoutNode(childId, childX, finalChildY, adj, pos, depth + 1)
            }
        }
    }
}
