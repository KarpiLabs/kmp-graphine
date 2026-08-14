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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphStateTest {
    @Test
    fun testNodeDragging() {
        val node = GraphNode("1", "Data")
        val state = GraphState(initialNodes = listOf(node))

        state.onNodeDragged("1", Offset(10f, 20f))

        assertEquals(Offset(10f, 20f), state.nodeStates["1"]?.position)
    }

    @Test
    fun testNodeCentering() {
        val node = GraphNode("1", "Data")
        val state = GraphState(initialNodes = listOf(node))

        state.onNodeResized("1", IntSize(100, 50))
        state.onNodeDragged("1", Offset(100f, 100f))

        // Position (100, 100) + Half Size (50, 25) = (150, 125)
        assertEquals(Offset(150f, 125f), state.getNodeCenter("1"))
    }

    @Test
    fun testSnapToGrid() {
        val node = GraphNode("1", "Data")
        val state = GraphState(initialNodes = listOf(node))
        state.snapGridSize = 50f

        // Drag to 62, 22. Should snap to 50, 0 (closest 50-pixel marks)
        state.onNodeDragged("1", Offset(62f, 22f))

        assertEquals(Offset(50f, 0f), state.nodeStates["1"]?.position)
    }

    @Test
    fun testNodesInRectSelectsOverlappingNodes() {
        val n1 = GraphNode("1", "A")
        val n2 = GraphNode("2", "B")
        val n3 = GraphNode("3", "C")
        val state = GraphState(initialNodes = listOf(n1, n2, n3))

        state.onNodeDragged("1", Offset(0f, 0f))
        state.onNodeResized("1", IntSize(20, 20))
        state.onNodeDragged("2", Offset(100f, 100f))
        state.onNodeResized("2", IntSize(20, 20))
        state.onNodeDragged("3", Offset(500f, 500f))
        state.onNodeResized("3", IntSize(20, 20))

        val selected = state.nodesInRect(Rect(-10f, -10f, 130f, 130f))

        assertEquals(setOf("1", "2"), selected)
    }

    @Test
    fun testNodesInRectExcludesCollapsedDescendants() {
        val parent = GraphNode("p", "Parent")
        val child = GraphNode("c", "Child")
        val state = GraphState(
            initialNodes = listOf(parent, child),
            initialEdges = listOf(GraphEdge("p", "c")),
        )
        state.onNodeResized("p", IntSize(10, 10))
        state.onNodeResized("c", IntSize(10, 10))
        state.onNodeDragged("c", Offset(5f, 5f))
        state.toggleCollapse("p")

        val selected = state.nodesInRect(Rect(-100f, -100f, 100f, 100f))

        assertFalse(selected.contains("c"))
    }

    @Test
    fun testNodeDegreesBasic() {
        val n1 = GraphNode("1", "Hub")
        val n2 = GraphNode("2", "Leaf1")
        val n3 = GraphNode("3", "Leaf2")
        val n4 = GraphNode("4", "Isolated")

        val edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("1", "3"),
            GraphEdge("2", "3"),
        )

        val state = GraphState(
            initialNodes = listOf(n1, n2, n3, n4),
            initialEdges = edges,
        )

        // Node 1: connected to 2, 3 = degree 2
        // Node 2: connected from 1, to 3 = degree 2
        // Node 3: connected from 1, 2 = degree 2
        // Node 4: isolated = degree 0 (not in map)
        assertEquals(2, state.nodeDegrees["1"])
        assertEquals(2, state.nodeDegrees["2"])
        assertEquals(2, state.nodeDegrees["3"])
        assertEquals(null, state.nodeDegrees["4"])
    }

    @Test
    fun testNodeDegreesUpdate() {
        val n1 = GraphNode("1", "Node1")
        val n2 = GraphNode("2", "Node2")
        val n3 = GraphNode("3", "Node3")

        val state = GraphState(
            initialNodes = listOf(n1, n2, n3),
            initialEdges = listOf(GraphEdge("1", "2")),
        )

        assertEquals(1, state.nodeDegrees["1"])
        assertEquals(1, state.nodeDegrees["2"])

        // Add an edge
        state.edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("1", "3"),
        )

        assertEquals(2, state.nodeDegrees["1"])
        assertEquals(1, state.nodeDegrees["2"])
        assertEquals(1, state.nodeDegrees["3"])
    }

    @Test
    fun testIsNodeVisibleHandlesCycles() {
        // Ring graph: 1 → 2 → 3 → 1 (cyclic edges)
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
            GraphNode("3", "C"),
        )
        val edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("2", "3"),
            GraphEdge("3", "1"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Must terminate and report all nodes visible (nothing collapsed).
        assertTrue(state.isNodeVisible("1"))
        assertTrue(state.isNodeVisible("2"))
        assertTrue(state.isNodeVisible("3"))
    }

    @Test
    fun testIsNodeVisibleRespectsCollapseOnTree() {
        val nodes = listOf(
            GraphNode("1", "Root"),
            GraphNode("2", "Child"),
            GraphNode("3", "Grandchild"),
        )
        val edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("2", "3"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)
        state.toggleCollapse("1")

        assertTrue(state.isNodeVisible("1"))
        assertFalse(state.isNodeVisible("2"))
        assertFalse(state.isNodeVisible("3"))
    }

    @Test
    fun testHighlightPathHandlesCycles() {
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
            GraphNode("3", "C"),
        )
        val edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("2", "3"),
            GraphEdge("3", "1"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Must terminate on a cycle without hanging.
        state.highlightPath("1")
        assertTrue(state.highlightedNodeIds.contains("1"))
        assertTrue(state.highlightedNodeIds.isNotEmpty())
    }
}
