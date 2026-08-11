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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeLayoutTest {

    @Test
    fun testSimpleTreeLayout() {
        val nodes = listOf(
            GraphNode("root", "Root"),
            GraphNode("child1", "Child 1"),
            GraphNode("child2", "Child 2")
        )
        val edges = listOf(
            GraphEdge("root", "child1"),
            GraphEdge("root", "child2")
        )
        
        val layout = TreeLayout(horizontalSpacing = 100f, verticalSpacing = 200f)
        val positions = layout.calculatePositions(nodes, edges, 1000f, 1000f)
        
        // Root should be at the top center
        assertEquals(Offset(500f, 100f), positions["root"])
        
        // Children should be below the root
        val y1 = positions["child1"]?.y ?: 0f
        val y2 = positions["child2"]?.y ?: 0f
        assertTrue(y1 > 100f)
        assertTrue(y2 > 100f)
        
        // Children should be horizontally separated
        val x1 = positions["child1"]?.x ?: 0f
        val x2 = positions["child2"]?.x ?: 0f
        assertTrue(x1 < x2)
    }

    @Test
    fun testClusteredLayout() {
        val nodes = (0..10).map { GraphNode(it.toString(), "Node $it") }
        val edges = (1..10).map { GraphEdge("0", it.toString()) }
        
        // TreeLayout now handles maxNodesPerRow intelligently
        val layout = TreeLayout()
        val positions = layout.calculatePositions(nodes, edges, 2000f, 2000f)
        
        // There should be at least two rows for the children
        val yCoords = (1..10).map { positions[it.toString()]?.y ?: 0f }.distinct()
        assertTrue(yCoords.size >= 2)
    }

    @Test
    fun testMultipleRoots() {
        val nodes = listOf(
            GraphNode("r1", "Root 1"),
            GraphNode("r2", "Root 2"),
            GraphNode("c1", "Child")
        )
        val edges = listOf(
            GraphEdge("r1", "c1")
        )
        
        val layout = TreeLayout()
        val positions = layout.calculatePositions(nodes, edges, 1000f, 1000f)
        
        // Both r1 and r2 should be identified as roots
        assertTrue(positions.containsKey("r1"))
        assertTrue(positions.containsKey("r2"))
        assertEquals(positions["r1"]?.y, positions["r2"]?.y)
    }
}
