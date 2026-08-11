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

import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ForceDirectedLayoutTest {

    @Test
    fun testForceDirectedLayoutPositions() {
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
            GraphNode("3", "C")
        )
        val edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("2", "3")
        )
        
        val layout = ForceDirectedLayout(iterations = 10)
        val positions = layout.calculatePositions(nodes, edges, 1000f, 1000f)
        
        assertEquals(3, positions.size)
        
        // Ensure nodes aren't all at the same spot (repulsion worked)
        val pos1 = positions["1"]!!
        val pos2 = positions["2"]!!
        val pos3 = positions["3"]!!
        
        assertNotEquals(pos1, pos2)
        assertNotEquals(pos2, pos3)
        assertNotEquals(pos1, pos3)
    }
}
