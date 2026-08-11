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

package io.karpilabs.graphine.model

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphModelsTest {

    @Test
    fun testGraphNodeCreation() {
        val node = GraphNode(id = "node1", data = "Test Data")
        assertEquals("node1", node.id)
        assertEquals("Test Data", node.data)
    }

    @Test
    fun testGraphNodeEquality() {
        val node1 = GraphNode(id = "1", data = "Data")
        val node2 = GraphNode(id = "1", data = "Data")
        assertEquals(node1, node2)
    }

    @Test
    fun testGraphEdgeCreation() {
        val edge = GraphEdge(from = "node1", to = "node2")
        assertEquals("node1", edge.from)
        assertEquals("node2", edge.to)
    }

    @Test
    fun testGraphEdgeEquality() {
        val edge1 = GraphEdge(from = "1", to = "2")
        val edge2 = GraphEdge(from = "1", to = "2")
        assertEquals(edge1, edge2)
    }

    @Test
    fun testGraphGroupCreation() {
        val group = GraphGroup(
            id = "group1",
            label = "Test Group",
            nodeIds = listOf("n1", "n2"),
            color = Color.Red,
        )
        assertEquals("group1", group.id)
        assertEquals("Test Group", group.label)
        assertEquals(2, group.nodeIds.size)
    }

    @Test
    fun testGraphGroupEmptyNodes() {
        val group = GraphGroup(
            id = "empty_group",
            label = "Empty",
            nodeIds = emptyList(),
            color = Color.Blue,
        )
        assertTrue(group.nodeIds.isEmpty())
    }

    @Test
    fun testEdgeConfigDefaults() {
        val config = EdgeConfig()
        assertEquals(Color.Gray.copy(alpha = 0.3f), config.color)
        assertEquals(2f, config.width)
        assertTrue(config.showArrowheads)
    }

    @Test
    fun testEdgeConfigCustom() {
        val config = EdgeConfig(
            color = Color.Red,
            width = 4f,
            showArrowheads = false,
            style = EdgeStyle.DASHED,
        )
        assertEquals(Color.Red, config.color)
        assertEquals(4f, config.width)
        assertTrue(!config.showArrowheads)
        assertEquals(EdgeStyle.DASHED, config.style)
    }

    @Test
    fun testDotStyleDefaults() {
        val style = DotStyle<String>()
        assertEquals(6f, style.radius)
    }

    @Test
    fun testDotStyleCustom() {
        val style = DotStyle<String>(
            radius = 12f,
            color = { Color.Green },
        )
        assertEquals(12f, style.radius)
        assertEquals(Color.Green, style.color(GraphNode("1", "data")))
    }

    @Test
    fun testGraphConfigDefaults() {
        val config = GraphConfig()
        assertEquals(0.7f, config.detailZoomThreshold)
        assertEquals(40f, config.viewportPadding)
    }

    @Test
    fun testGraphConfigCustom() {
        val config = GraphConfig(
            detailZoomThreshold = 0.5f,
            viewportPadding = 100f,
        )
        assertEquals(0.5f, config.detailZoomThreshold)
        assertEquals(100f, config.viewportPadding)
    }

    @Test
    fun testNodeRenderModes() {
        assertEquals(NodeRenderMode.COMPOSABLE, NodeRenderMode.COMPOSABLE)
        assertEquals(NodeRenderMode.DOT, NodeRenderMode.DOT)
    }
}
