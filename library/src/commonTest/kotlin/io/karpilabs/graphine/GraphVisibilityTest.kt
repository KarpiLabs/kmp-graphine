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
import androidx.compose.ui.unit.IntSize
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphVisibilityTest {

    @Test
    fun testNodeVisibilityWithCollapsedParent() {
        // Create a hierarchy: 1 -> 2 -> 3
        val nodes = listOf(
            GraphNode(id = "1", data = "Parent"),
            GraphNode(id = "2", data = "Child"),
            GraphNode(id = "3", data = "Grandchild"),
        )
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "2", to = "3"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // All nodes visible initially
        assertTrue(state.isNodeVisible("1"))
        assertTrue(state.isNodeVisible("2"))
        assertTrue(state.isNodeVisible("3"))

        // Collapse node 1
        state.toggleCollapse("1")

        // Nodes 2 and 3 should now be hidden
        assertTrue(state.isNodeVisible("1")) // Parent is still visible
        assertFalse(state.isNodeVisible("2"))
        assertFalse(state.isNodeVisible("3"))
    }

    @Test
    fun testNodeVisibilityMultipleLevels() {
        // Tree structure:
        //     1
        //    / \
        //   2   3
        //  /
        // 4
        val nodes = (1..4).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "1", to = "3"),
            GraphEdge(from = "2", to = "4"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Collapse the root
        state.toggleCollapse("1")
        assertFalse(state.isNodeVisible("2"))
        assertFalse(state.isNodeVisible("3"))
        assertFalse(state.isNodeVisible("4"))

        // Expand the root
        state.toggleCollapse("1")
        assertTrue(state.isNodeVisible("2"))
        assertTrue(state.isNodeVisible("3"))

        // Now collapse node 2
        state.toggleCollapse("2")
        assertFalse(state.isNodeVisible("4"))
        assertTrue(state.isNodeVisible("2")) // Parent still visible
    }

    @Test
    fun testNodeVisibilityCyclicGraph() {
        // Cyclic: 1 -> 2 -> 3 -> 1
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "2", to = "3"),
            GraphEdge(from = "3", to = "1"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Should not hang or crash; cycles are handled
        assertTrue(state.isNodeVisible("1"))
        assertTrue(state.isNodeVisible("2"))
        assertTrue(state.isNodeVisible("3"))

        state.toggleCollapse("1")
        assertFalse(state.isNodeVisible("2"))
    }

    @Test
    fun testGetVisibleNodes() {
        val nodes = (1..4).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "1", to = "3"),
            GraphEdge(from = "2", to = "4"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        state.toggleCollapse("1")
        val visibleNodes = state.getVisibleNodes()

        // Only node 1 should be visible after collapsing it
        assertEquals(1, visibleNodes.size)
        assertTrue(visibleNodes.any { it.id == "1" })
    }

    @Test
    fun testIsRootNode() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "1", to = "3"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        assertTrue(state.isRootNode("1"))
        assertFalse(state.isRootNode("2"))
        assertFalse(state.isRootNode("3"))
    }

    @Test
    fun testGetDescendants() {
        val nodes = (1..5).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "1", to = "3"),
            GraphEdge(from = "2", to = "4"),
            GraphEdge(from = "3", to = "5"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        val descendants = state.getDescendants("1")
        assertEquals(4, descendants.size)
        assertTrue(descendants.contains("2"))
        assertTrue(descendants.contains("3"))
        assertTrue(descendants.contains("4"))
        assertTrue(descendants.contains("5"))
    }

    @Test
    fun testGetDescendantsNoChildren() {
        val nodes = listOf(
            GraphNode(id = "1", data = "Leaf"),
            GraphNode(id = "2", data = "Other"),
        )
        val state = GraphState(initialNodes = nodes)

        val descendants = state.getDescendants("1")
        assertEquals(0, descendants.size)
    }
}

class GraphInteractionTest {

    @Test
    fun testNodeSelection() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val state = GraphState(initialNodes = nodes)

        state.selectedNodeIds = setOf("1", "2")
        assertEquals(2, state.selectedNodeIds.size)
        assertTrue(state.selectedNodeIds.contains("1"))

        state.selectedNodeIds = setOf()
        assertEquals(0, state.selectedNodeIds.size)
    }

    @Test
    fun testNodeHighlighting() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge(from = "1", to = "2"),
            GraphEdge(from = "2", to = "3"),
        )
        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        state.highlightedNodeIds = setOf("1", "2", "3")
        state.highlightedEdgeIds = setOf(Pair("1", "2"), Pair("2", "3"))

        assertEquals(3, state.highlightedNodeIds.size)
        assertEquals(2, state.highlightedEdgeIds.size)
    }

    @Test
    fun testClearInteractionsCompletely() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val state = GraphState(initialNodes = nodes)

        state.selectedNodeIds = setOf("1", "2")
        state.highlightedNodeIds = setOf("1")
        state.highlightedEdgeIds = setOf(Pair("1", "2"))
        state.targetId = "1"

        state.clearInteractions()

        assertTrue(state.selectedNodeIds.isEmpty())
        assertTrue(state.highlightedNodeIds.isEmpty())
        assertTrue(state.highlightedEdgeIds.isEmpty())
    }

    @Test
    fun testTargetNodeSelection() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val state = GraphState(initialNodes = nodes)

        state.targetId = "2"
        assertEquals("2", state.targetId)

        state.targetId = null
        assertTrue(state.targetId == null)
    }
}

class GraphBoundsTest {

    @Test
    fun testContentBoundsEmpty() {
        val state = GraphState<String>()
        val bounds = state.getContentBounds()
        assertTrue(bounds.isEmpty)
    }

    @Test
    fun testContentBoundsSingleNode() {
        val nodes = listOf(GraphNode(id = "1", data = "Node"))
        val state = GraphState(initialNodes = nodes)

        state.onNodeDragged("1", Offset(100f, 200f))
        state.onNodeResized("1", IntSize(50, 50))

        val bounds = state.getContentBounds()
        assertEquals(100f, bounds.left)
        assertEquals(200f, bounds.top)
        assertEquals(150f, bounds.right)
        assertEquals(250f, bounds.bottom)
    }

    @Test
    fun testContentBoundsMultipleNodes() {
        val nodes = listOf(
            GraphNode(id = "1", data = "Node1"),
            GraphNode(id = "2", data = "Node2"),
            GraphNode(id = "3", data = "Node3"),
        )
        val state = GraphState(initialNodes = nodes)

        state.onNodeDragged("1", Offset(0f, 0f))
        state.onNodeResized("1", IntSize(100, 100))

        state.onNodeDragged("2", Offset(150f, 150f))
        state.onNodeResized("2", IntSize(100, 100))

        state.onNodeDragged("3", Offset(50f, 200f))
        state.onNodeResized("3", IntSize(100, 100))

        val bounds = state.getContentBounds()
        assertEquals(0f, bounds.left)
        assertEquals(0f, bounds.top)
        assertEquals(250f, bounds.right)
        assertEquals(300f, bounds.bottom)
    }

    @Test
    fun testZoomAndPan() {
        val nodes = listOf(GraphNode(id = "1", data = "Node"))
        val state = GraphState(initialNodes = nodes)

        state.scale = 2f
        state.offset = Offset(100f, 50f)

        assertEquals(2f, state.scale)
        assertEquals(Offset(100f, 50f), state.offset)

        // Zoom out
        state.scale = 0.5f
        assertEquals(0.5f, state.scale)
    }
}
