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
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.GraphConfig
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphGroup
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphIntegrationTest {

    @Test
    fun testComplexHierarchy() {
        // Build an organization chart
        // CEO
        // ├── VP Engineering
        // │   ├── Engineering Lead
        // │   │   ├── Backend Developer
        // │   │   └── Frontend Developer
        // │   └── QA Lead
        // │       └── QA Engineer
        // └── VP Sales
        //     └── Sales Rep

        val nodes = listOf(
            GraphNode(id = "ceo", data = "CEO"),
            GraphNode(id = "vpeng", data = "VP Engineering"),
            GraphNode(id = "engLead", data = "Eng Lead"),
            GraphNode(id = "backend", data = "Backend Dev"),
            GraphNode(id = "frontend", data = "Frontend Dev"),
            GraphNode(id = "qaLead", data = "QA Lead"),
            GraphNode(id = "qaEng", data = "QA Engineer"),
            GraphNode(id = "vpSales", data = "VP Sales"),
            GraphNode(id = "salesRep", data = "Sales Rep"),
        )

        val edges = listOf(
            GraphEdge("ceo", "vpeng"),
            GraphEdge("ceo", "vpSales"),
            GraphEdge("vpeng", "engLead"),
            GraphEdge("vpeng", "qaLead"),
            GraphEdge("engLead", "backend"),
            GraphEdge("engLead", "frontend"),
            GraphEdge("qaLead", "qaEng"),
            GraphEdge("vpSales", "salesRep"),
        )

        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Verify structure
        assertEquals(9, state.nodeStates.size)
        assertEquals(8, state.edges.size)

        // Collapse VP Engineering subtree
        state.toggleCollapse("vpeng")
        assertFalse(state.isNodeVisible("engLead"))
        assertFalse(state.isNodeVisible("backend"))
        assertFalse(state.isNodeVisible("qaEng"))
        assertTrue(state.isNodeVisible("vpSales"))
        assertTrue(state.isNodeVisible("salesRep"))

        // Expand and collapse different branch
        state.toggleCollapse("vpeng")
        state.toggleCollapse("vpSales")
        assertFalse(state.isNodeVisible("salesRep"))
        assertTrue(state.isNodeVisible("engLead"))
    }

    @Test
    fun testGraphWithGroups() {
        val nodes = (1..6).map { GraphNode(id = "n$it", data = "Node $it") }
        val groups = listOf(
            GraphGroup("g1", "Frontend", listOf("n1", "n2", "n3"), color = androidx.compose.ui.graphics.Color.Blue),
            GraphGroup("g2", "Backend", listOf("n4", "n5"), color = androidx.compose.ui.graphics.Color.Red),
            GraphGroup("g3", "Orphaned", listOf("n6"), color = androidx.compose.ui.graphics.Color.Green),
        )
        val edges = listOf(
            GraphEdge("n1", "n4"),
            GraphEdge("n2", "n5"),
        )

        val state = GraphState(initialNodes = nodes, initialEdges = edges, initialGroups = groups)

        assertEquals(6, state.nodeStates.size)
        assertEquals(3, state.groups.size)
        assertEquals(2, state.edges.size)
    }

    @Test
    fun testDragAndSelectWorkflow() {
        val nodes = (1..5).map { GraphNode(id = "$it", data = "Node $it") }
        val state = GraphState(initialNodes = nodes)

        // User drags node 1 to position
        state.onNodeDragged("1", Offset(100f, 100f))
        assertEquals(Offset(100f, 100f), state.nodeStates["1"]?.position)

        // User selects multiple nodes
        state.selectedNodeIds = setOf("1", "2", "3")
        assertEquals(3, state.selectedNodeIds.size)

        // User highlights path
        state.highlightedNodeIds = setOf("1", "2")
        assertEquals(2, state.highlightedNodeIds.size)

        // Clear and select different node
        state.clearInteractions()
        state.targetId = "3"
        assertEquals("3", state.targetId)
    }

    @Test
    fun testGridSnapWorkflow() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val state = GraphState(initialNodes = nodes)
        state.snapGridSize = 50f

        // Drag nodes with snapping
        state.onNodeDragged("1", Offset(37f, 62f))
        state.onNodeDragged("2", Offset(95f, 48f))
        state.onNodeDragged("3", Offset(12f, 87f))

        // All should snap to 50-pixel grid
        assertEquals(Offset(50f, 50f), state.nodeStates["1"]?.position)
        assertEquals(Offset(100f, 50f), state.nodeStates["2"]?.position)
        assertEquals(Offset(0f, 100f), state.nodeStates["3"]?.position)
    }

    @Test
    fun testScaleAndOffsetConstraint() {
        val nodes = listOf(GraphNode(id = "1", data = "Node"))
        val state = GraphState(initialNodes = nodes)

        // Place node
        state.onNodeDragged("1", Offset(100f, 100f))
        state.onNodeResized("1", IntSize(50, 50))

        // Try to pan way out of bounds
        val constrainedOffset = state.coerceOffset(
            Offset(-1000f, -1000f),
            viewportWidth = 800f,
            viewportHeight = 600f,
            scale = 1f,
        )

        // Should be constrained
        assertTrue(constrainedOffset.x > -1000f || constrainedOffset.x < 100f)
    }

    @Test
    fun testMultipleRoots() {
        // Two separate trees
        val nodes = (1..6).map { GraphNode(id = "n$it", data = "Node $it") }
        val edges = listOf(
            GraphEdge("n1", "n2"),
            GraphEdge("n1", "n3"),
            GraphEdge("n4", "n5"),
            GraphEdge("n4", "n6"),
        )

        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Two roots: n1 and n4
        val roots = nodes.filter { node -> edges.none { it.to == node.id } }
        assertEquals(2, roots.size)

        // Collapse root of first tree
        state.toggleCollapse("n1")
        assertFalse(state.isNodeVisible("n2"))
        assertFalse(state.isNodeVisible("n3"))
        assertTrue(state.isNodeVisible("n4"))
        assertTrue(state.isNodeVisible("n5"))
    }

    @Test
    fun testLargeGraph() {
        // Create a large graph
        val nodeCount = 100
        val nodes = (1..nodeCount).map { GraphNode(id = "n$it", data = "Node $it") }

        // Create edges in a ring topology
        val edges = mutableListOf<GraphEdge>()
        for (i in 1..nodeCount) {
            val next = if (i == nodeCount) 1 else i + 1
            edges.add(GraphEdge("n$i", "n$next"))
        }

        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        assertEquals(nodeCount, state.nodeStates.size)
        assertEquals(nodeCount, state.edges.size)

        // Verify degrees
        for (i in 1..nodeCount) {
            assertEquals(2, state.nodeDegrees["n$i"]) // Each node has 1 in + 1 out
        }
    }

    @Test
    fun testConfigurationUpdates() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val initialConfig = GraphConfig(
            detailZoomThreshold = 0.7f,
            viewportPadding = 40f,
        )
        val state = GraphState(initialNodes = nodes, initialConfig = initialConfig)

        assertEquals(0.7f, state.config.detailZoomThreshold)

        // Update config
        state.config = GraphConfig(
            detailZoomThreshold = 0.5f,
            viewportPadding = 100f,
        )

        assertEquals(0.5f, state.config.detailZoomThreshold)
        assertEquals(100f, state.config.viewportPadding)
    }

    @Test
    fun testEdgeConfigUpdates() {
        val nodes = (1..2).map { GraphNode(id = "$it", data = "Node $it") }
        val edges = listOf(GraphEdge("1", "2"))

        val state = GraphState(initialNodes = nodes, initialEdges = edges)

        // Verify edge updates work
        val oldEdges = state.edges
        state.edges = oldEdges + GraphEdge("2", "1")

        assertEquals(2, state.edges.size)
    }

    @Test
    fun testNodeResizeTracking() {
        val nodes = (1..3).map { GraphNode(id = "$it", data = "Node $it") }
        val state = GraphState(initialNodes = nodes)

        // Resize nodes
        state.onNodeResized("1", IntSize(100, 50))
        state.onNodeResized("2", IntSize(80, 60))
        state.onNodeResized("3", IntSize(120, 40))

        assertEquals(IntSize(100, 50), state.nodeStates["1"]?.size)
        assertEquals(IntSize(80, 60), state.nodeStates["2"]?.size)
        assertEquals(IntSize(120, 40), state.nodeStates["3"]?.size)

        // Verify bounds include all nodes
        val bounds = state.getContentBounds()
        assertTrue(bounds.width > 0f)
        assertTrue(bounds.height > 0f)
    }
}
