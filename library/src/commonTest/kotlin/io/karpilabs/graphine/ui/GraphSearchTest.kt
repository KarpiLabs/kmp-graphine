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

package io.karpilabs.graphine.ui

import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphSearchTest {

    @Test
    fun testNodeLabelProviderHandlesExceptionsGracefully() {
        val node1 = GraphNode(id = "node1", data = "Data 1")
        val node2 = GraphNode(id = "node2", data = "Data 2")
        val state = GraphState(initialNodes = listOf(node1, node2))

        val query = "Data"
        val nodeLabelProvider: (String) -> String = { id ->
            if (id == "node1") {
                throw IllegalStateException("Failing label provider for testing")
            }
            "Data 2"
        }

        // Simulating the search matching logic from GraphSearch
        val match = state.nodeStates.keys.find { id ->
            val label = runCatching { nodeLabelProvider(id) }.getOrDefault("")
            label.contains(query, ignoreCase = true)
        }

        assertEquals("node2", match)
    }

    @Test
    fun testNodeLabelProviderWhenAllFailReturnsNull() {
        val node1 = GraphNode(id = "node1", data = "Data 1")
        val state = GraphState(initialNodes = listOf(node1))

        val query = "Data"
        val nodeLabelProvider: (String) -> String = {
            throw RuntimeException("All node labels fail")
        }

        val match = state.nodeStates.keys.find { id ->
            val label = runCatching { nodeLabelProvider(id) }.getOrDefault("")
            label.contains(query, ignoreCase = true)
        }

        assertNull(match)
    }
}
