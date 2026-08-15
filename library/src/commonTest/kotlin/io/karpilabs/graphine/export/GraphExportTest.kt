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

package io.karpilabs.graphine.export

import androidx.compose.ui.geometry.Offset
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.EdgeStyle
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphExportTest {
    private fun sampleState(): GraphState<String> {
        val nodes = listOf(GraphNode("1", "Alpha"), GraphNode("2", "Beta"))
        val state = GraphState(initialNodes = nodes, initialEdges = listOf(GraphEdge("1", "2")))
        state.onNodeDragged("1", Offset(0f, 0f))
        state.onNodeDragged("2", Offset(200f, 100f))
        return state
    }

    @Test
    fun testBuildModelIncludesAllVisibleNodesAndEdges() {
        val model = GraphExport.buildModel(sampleState())

        assertEquals(2, model.nodes.size)
        assertEquals(1, model.edges.size)
        assertTrue(model.width > 0f && model.height > 0f)
    }

    @Test
    fun testBuildModelEmptyGraphProducesMinimalCanvas() {
        val model = GraphExport.buildModel(GraphState<String>())

        assertTrue(model.nodes.isEmpty())
        assertTrue(model.edges.isEmpty())
        assertTrue(model.width > 0f && model.height > 0f)
    }

    @Test
    fun testToSvgContainsNodeCirclesAndEdgePaths() {
        val svg = GraphExport.toSvg(sampleState(), nodeLabel = { it.data })

        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("</svg>"))
        assertEquals(2, Regex("<circle").findAll(svg).count())
        assertEquals(1, Regex("<path").findAll(svg).count())
        assertTrue(svg.contains(">Alpha<"))
        assertTrue(svg.contains(">Beta<"))
    }

    @Test
    fun testToSvgEscapesLabelContent() {
        val nodes = listOf(GraphNode("1", "<script>&\"'"))
        val state = GraphState(initialNodes = nodes)
        state.onNodeDragged("1", Offset(0f, 0f))

        val svg = GraphExport.toSvg(state, nodeLabel = { it.data })

        assertTrue(!svg.contains("<script>"))
        assertTrue(svg.contains("&lt;script&gt;&amp;&quot;&apos;"))
    }

    @Test
    fun testToSvgStripsInvalidXmlControlCharacters() {
        // Includes null byte \u0000, control char \u0007, and surrogate pair \uD83D\uDE00 (😃)
        val nodes = listOf(GraphNode("1", "Node\u0000\u0007Test \uD83D\uDE00"))
        val state = GraphState(initialNodes = nodes)
        state.onNodeDragged("1", Offset(0f, 0f))

        val svg = GraphExport.toSvg(state, nodeLabel = { it.data })

        assertTrue(!svg.contains("\u0000"))
        assertTrue(!svg.contains("\u0007"))
        assertTrue(svg.contains("NodeTest \uD83D\uDE00"))
    }

    @Test
    fun testToSvgCurvedEdgeUsesCubicPath() {
        val svg = GraphExport.toSvg(sampleState(), edgeConfig = EdgeConfig(style = EdgeStyle.CURVED))

        assertTrue(svg.contains(" C "))
    }

    @Test
    fun testToSvgSkipsEdgesWithMissingEndpoints() {
        val nodes = listOf(GraphNode("1", "Alpha"))
        val state = GraphState(initialNodes = nodes, initialEdges = listOf(GraphEdge("1", "missing")))
        state.onNodeDragged("1", Offset(0f, 0f))

        val model = GraphExport.buildModel(state)

        assertTrue(model.edges.isEmpty())
        assertEquals(1, model.nodes.size)
    }
}
