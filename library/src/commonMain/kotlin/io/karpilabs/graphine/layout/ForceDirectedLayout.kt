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
import kotlin.random.Random

/**
 * A layout that uses physical forces to position nodes.
 * Repulsion pushes all nodes apart.
 * Attraction pulls connected nodes together (springs).
 */
class ForceDirectedLayout(
    private val iterations: Int = 100,
    private val k: Float = 400f, // Increased ideal distance
    private val repulsionStrength: Float = 15000f, // Increased repulsion
    private val springStrength: Float = 0.05f,
) : GraphLayout {
    override fun <T> calculatePositions(
        nodes: List<GraphNode<T>>,
        edges: List<GraphEdge>,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Map<String, Offset> {
        if (nodes.isEmpty()) return emptyMap()

        // Start with random positions or a simple circle
        val random = Random(42) // Fixed seed for stability
        val positions =
            nodes
                .associate { node ->
                    node.id to
                        Offset(
                            (viewportWidth / 2) + (random.nextFloat() - 0.5f) * 100,
                            (viewportHeight / 2) + (random.nextFloat() - 0.5f) * 100,
                        )
                }.toMutableMap()

        repeat(iterations) {
            val displacements =
                mutableMapOf<String, Offset>().apply {
                    nodes.forEach { put(it.id, Offset.Zero) }
                }

            // 1. Repulsion between all pairs
            for (i in nodes.indices) {
                for (j in i + 1 until nodes.indices.last + 1) {
                    val u = nodes[i].id
                    val v = nodes[j].id
                    val delta = positions[u]!! - positions[v]!!
                    val distance = delta.getDistance().coerceAtLeast(1f)
                    val force = repulsionStrength / (distance * distance)
                    val move = (delta / distance) * force
                    displacements[u] = displacements[u]!! + move
                    displacements[v] = displacements[v]!! - move
                }
            }

            // 2. Attraction between connected nodes (springs)
            edges.forEach { edge ->
                val u = edge.from
                val v = edge.to
                val delta = positions[u]!! - positions[v]!!
                val distance = delta.getDistance().coerceAtLeast(1f)
                val force = (distance - k) * springStrength
                val move = (delta / distance) * force
                displacements[u] = displacements[u]!! - move
                displacements[v] = displacements[v]!! + move
            }

            // 3. Apply displacements
            nodes.forEach { node ->
                positions[node.id] = positions[node.id]!! + displacements[node.id]!!
            }
        }

        return positions
    }
}
