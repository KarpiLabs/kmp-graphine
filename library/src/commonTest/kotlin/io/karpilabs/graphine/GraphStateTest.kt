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
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
