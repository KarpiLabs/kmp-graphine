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
    fun nodeLabelProvider_safeMatching() {
        val nodes = listOf(
            GraphNode("node-1", "Alpha"),
            GraphNode("node-2", "Beta"),
        )
        val state = GraphState(nodes)

        val nodeLabelProvider: (String) -> String = { id ->
            if (id == "node-1") throw IllegalStateException("Custom label provider failure")
            "Label for $id"
        }

        val query = "Beta"
        val match = state.nodeStates.keys.find { key ->
            runCatching { nodeLabelProvider(key) }
                .getOrNull()
                ?.contains(query, ignoreCase = true) == true
        }

        assertEquals("node-2", match)
    }

    @Test
    fun nodeLabelProvider_handlesAllFailuresGracefully() {
        val nodes = listOf(GraphNode("node-1", "Alpha"))
        val state = GraphState(nodes)

        val nodeLabelProvider: (String) -> String = {
            throw RuntimeException("All node labels fail")
        }

        val query = "Alpha"
        val match = state.nodeStates.keys.find { key ->
            runCatching { nodeLabelProvider(key) }
                .getOrNull()
                ?.contains(query, ignoreCase = true) == true
        }

        assertNull(match)
    }
}
