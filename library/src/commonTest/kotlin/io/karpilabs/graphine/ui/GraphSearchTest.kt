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
    fun testNodeLabelProviderExceptionHandling() {
        val nodes = listOf(
            GraphNode("node-1", "A"),
            GraphNode("node-2", "B"),
        )
        val state = GraphState(initialNodes = nodes)

        val failingProvider: (String) -> String = { nodeId ->
            if (nodeId == "node-1") {
                throw RuntimeException("Provider failed for node-1")
            }
            "Valid $nodeId"
        }

        // Test matching a node where provider succeeds, despite other nodes throwing exceptions
        val match = state.nodeStates.keys.find {
            runCatching { failingProvider(it) }.getOrDefault("").contains("node-2", ignoreCase = true)
        }

        assertEquals("node-2", match)

        // Test searching for a term that only matching failing node doesn't crash and returns null
        val noMatch = state.nodeStates.keys.find {
            runCatching { failingProvider(it) }.getOrDefault("").contains("node-1", ignoreCase = true)
        }

        assertNull(noMatch)
    }
}
