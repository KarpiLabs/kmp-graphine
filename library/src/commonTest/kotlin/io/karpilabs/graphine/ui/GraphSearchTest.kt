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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphSearchTest {

    @Test
    fun findSearchMatch_returnsNullForShortQueries() {
        val nodeIds = setOf("node1", "node2")
        assertNull(findSearchMatch(nodeIds, "", { it }))
        assertNull(findSearchMatch(nodeIds, "a", { it }))
    }

    @Test
    fun findSearchMatch_matchesNodeLabels() {
        val nodeIds = setOf("node1", "node2")
        val labels = mapOf("node1" to "Alpha Corporation", "node2" to "Beta Systems")
        assertEquals("node1", findSearchMatch(nodeIds, "alpha") { labels[it] ?: it })
        assertEquals("node2", findSearchMatch(nodeIds, "SYSTEMS") { labels[it] ?: it })
        assertNull(findSearchMatch(nodeIds, "gamma") { labels[it] ?: it })
    }

    @Test
    fun findSearchMatch_handlesExceptionsInNodeLabelProviderSafely() {
        val nodeIds = setOf("node_apple", "node_banana")
        val failingProvider: (String) -> String = { id ->
            if (id == "node_apple") throw IllegalStateException("Callback error")
            "Banana Corp"
        }
        // "apple" matches node_apple via fallback to id "node_apple" when label provider throws an exception
        assertEquals("node_apple", findSearchMatch(nodeIds, "apple", failingProvider))
        // "banana" matches node_banana via provider return value
        assertEquals("node_banana", findSearchMatch(nodeIds, "banana", failingProvider))
    }
}
