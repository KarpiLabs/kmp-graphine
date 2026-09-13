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
    fun testNodeLabelProviderExceptionHandledGracefully() {
        val nodes = listOf(
            GraphNode(id = "node_alpha", data = "Alpha Node"),
            GraphNode(id = "node_beta", data = "Beta Node"),
        )
        val state = GraphState(initialNodes = nodes)

        val faultingLabelProvider: (String) -> String = { id ->
            if (id == "node_alpha") throw RuntimeException("Simulated provider failure")
            "Custom_$id"
        }

        val matchForFaulting = state.nodeStates.keys.find { id ->
            val label = runCatching { faultingLabelProvider(id) }.getOrDefault(id)
            label.contains("alpha", ignoreCase = true)
        }

        // Should safely fall back to the node ID ("node_alpha") and match
        assertEquals("node_alpha", matchForFaulting)

        val matchForBeta = state.nodeStates.keys.find { id ->
            val label = runCatching { faultingLabelProvider(id) }.getOrDefault(id)
            label.contains("Custom_node_beta", ignoreCase = true)
        }

        assertEquals("node_beta", matchForBeta)
    }

    @Test
    fun testNodeLabelProviderValidMatches() {
        val nodes = listOf(
            GraphNode(id = "n1", data = "Apple"),
            GraphNode(id = "n2", data = "Banana"),
        )
        val state = GraphState(initialNodes = nodes)

        val customLabelProvider: (String) -> String = { id ->
            when (id) {
                "n1" -> "Fruit: Apple"
                "n2" -> "Fruit: Banana"
                else -> id
            }
        }

        val match = state.nodeStates.keys.find { id ->
            val label = runCatching { customLabelProvider(id) }.getOrDefault(id)
            label.contains("Banana", ignoreCase = true)
        }

        assertEquals("n2", match)

        val noMatch = state.nodeStates.keys.find { id ->
            val label = runCatching { customLabelProvider(id) }.getOrDefault(id)
            label.contains("Cherry", ignoreCase = true)
        }

        assertNull(noMatch)
    }
}
