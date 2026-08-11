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
import kotlin.test.assertTrue

class ForceSimulationTest {
    @Test
    fun testAlphaDecaysToMinimum() {
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
        )
        val edges = listOf(GraphEdge("1", "2"))
        val config = ForceSimulationConfig(alphaDecay = 0.1f, alphaMin = 0.001f)
        val initialPositions = mapOf(
            "1" to Offset(0f, 0f),
            "2" to Offset(100f, 0f),
        )

        val sim = ForceSimulation(nodes, edges, config, initialPositions)

        var isActive = true
        var tickCount = 0
        val maxTicks = 1000
        while (isActive && tickCount < maxTicks) {
            isActive = sim.tick(500f, 500f)
            tickCount++
        }

        assertTrue(tickCount < maxTicks, "Simulation should cool down within reasonable ticks")
        assertTrue(tickCount > 10, "Simulation should run for several ticks before cooling")
    }

    @Test
    fun testConnectedNodesApproach() {
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
        )
        val edges = listOf(GraphEdge("1", "2"))
        val config = ForceSimulationConfig(
            centerStrength = 0f,
            repelStrength = 1000f,
            linkStrength = 0.5f,
            linkDistance = 50f,
            alphaDecay = 0.01f,
        )
        val initialPositions = mutableMapOf(
            "1" to Offset(0f, 0f),
            "2" to Offset(200f, 0f),
        )

        val sim = ForceSimulation(nodes, edges, config, initialPositions)

        val initialDist = 200f
        repeat(50) { sim.tick(500f, 500f) }
        val finalPositions = sim.getPositions()
        val finalDist = (finalPositions["2"]!! - finalPositions["1"]!!).getDistance()

        assertTrue(finalDist < initialDist, "Connected nodes should move closer")
    }

    @Test
    fun testReheat() {
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
        )
        val edges = emptyList<GraphEdge>()
        val config = ForceSimulationConfig(alphaDecay = 0.5f, alphaMin = 0.001f)
        val initialPositions = mapOf(
            "1" to Offset(0f, 0f),
            "2" to Offset(100f, 0f),
        )

        val sim = ForceSimulation(nodes, edges, config, initialPositions)

        // Cool it down
        repeat(10) { sim.tick(500f, 500f) }

        // Should be inactive now
        val wasActive = sim.tick(500f, 500f)
        assertTrue(!wasActive, "Simulation should be cooled after many ticks")

        // Reheat
        sim.reheat()
        val afterReheat = sim.tick(500f, 500f)
        assertTrue(afterReheat, "Simulation should be active after reheat")
    }

    @Test
    fun testPinnedNodeDoesNotMove() {
        val nodes = listOf(
            GraphNode("1", "A"),
            GraphNode("2", "B"),
            GraphNode("3", "C"),
        )
        val edges = listOf(
            GraphEdge("1", "2"),
            GraphEdge("2", "3"),
        )
        val config = ForceSimulationConfig(
            centerStrength = 0.05f,
            repelStrength = 5000f,
            linkStrength = 0.1f,
            linkDistance = 50f,
            alphaDecay = 0.01f,
        )
        val initialPositions = mapOf(
            "1" to Offset(0f, 0f),
            "2" to Offset(100f, 0f),
            "3" to Offset(200f, 0f),
        )
        val sim = ForceSimulation(nodes, edges, config, initialPositions)
        val pinnedPos = Offset(50f, 50f)
        sim.setNodePosition("2", pinnedPos)
        sim.pinNode("2")

        repeat(30) { sim.tick(500f, 500f) }

        val pos = sim.getPositions()["2"]!!
        assertTrue(
            kotlin.math.abs(pos.x - pinnedPos.x) < 0.01f &&
                kotlin.math.abs(pos.y - pinnedPos.y) < 0.01f,
            "Pinned node must stay fixed; was $pos",
        )
    }
}
