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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Configuration for continuous force simulation.
 * Tunable via Center, Repel, and Link force parameters.
 *
 * @param centerStrength Pull toward the graph centroid (prevents drift).
 * @param repelStrength Pairwise node repulsion.
 * @param linkStrength Attraction along edges (springs).
 * @param linkDistance Ideal spring rest distance.
 * @param alphaDecay Cooling schedule: alpha *= (1 - alphaDecay) each tick.
 * @param alphaMin Simulation stops when alpha < alphaMin (fully cooled).
 * @param repelDistanceMax Max distance for repulsion interactions. Larger values
 * are more accurate but costlier; spatial hashing only considers neighbors within this.
 * @param maxVelocity Caps per-tick movement so a reheat cannot explode the graph.
 */
data class ForceSimulationConfig(
    val centerStrength: Float = 0.1f,
    val repelStrength: Float = 15000f,
    val linkStrength: Float = 0.05f,
    val linkDistance: Float = 400f,
    val alphaDecay: Float = 0.025f,
    val alphaMin: Float = 0.001f,
    val repelDistanceMax: Float = 280f,
    val maxVelocity: Float = 40f,
)

/**
 * Continuous, incremental force simulation driven by frame-by-frame ticks.
 *
 * Optimized for large graphs (1000+ nodes):
 * - Flat [FloatArray] storage (no per-tick map/Offset allocations in the hot path)
 * - Spatial hashing for repulsion so cost is ~O(n · k) instead of O(n²)
 * - [pinNode] / [unpinNode] so a user-dragged node is not fought by the integrator
 *
 * Call [tick] each frame. Returns true while still "hot" (alpha > alphaMin).
 * Call [reheat] on manual drag to gently re-energize neighbors.
 */
class ForceSimulation<T>(
    nodes: List<GraphNode<T>>,
    edges: List<GraphEdge>,
    private var config: ForceSimulationConfig,
    initialPositions: Map<String, Offset>,
) {
    private val n = nodes.size
    private val ids = Array(n) { nodes[it].id }
    private val indexOf: Map<String, Int> = buildMap(n) {
        for (i in 0 until n) put(ids[i], i)
    }

    private val x = FloatArray(n)
    private val y = FloatArray(n)
    private val vx = FloatArray(n)
    private val vy = FloatArray(n)
    private val fx = FloatArray(n)
    private val fy = FloatArray(n)
    private val pinned = BooleanArray(n)

    private val edgeFrom: IntArray
    private val edgeTo: IntArray

    private var alpha = 1f

    // Reused spatial-hash scratch (cleared each tick; avoids per-tick allocations of cell lists).
    private val cellBuckets = HashMap<Long, MutableList<Int>>(n)
    private val bucketPool = ArrayList<MutableList<Int>>(n)

    init {
        for (i in 0 until n) {
            val pos = initialPositions[ids[i]] ?: Offset.Zero
            x[i] = pos.x
            y[i] = pos.y
        }

        val from = ArrayList<Int>(edges.size)
        val to = ArrayList<Int>(edges.size)
        edges.forEach { edge ->
            val a = indexOf[edge.from] ?: return@forEach
            val b = indexOf[edge.to] ?: return@forEach
            from += a
            to += b
        }
        edgeFrom = from.toIntArray()
        edgeTo = to.toIntArray()
    }

    fun getPositions(): Map<String, Offset> = buildMap(n) {
        for (i in 0 until n) {
            put(ids[i], Offset(x[i], y[i]))
        }
    }

    /**
     * Writes current positions into [out] (cleared first). Prefer this over [getPositions]
     * when calling every frame to reuse the map instance.
     */
    fun copyPositionsTo(out: MutableMap<String, Offset>) {
        out.clear()
        for (i in 0 until n) {
            out[ids[i]] = Offset(x[i], y[i])
        }
    }

    /** Whether the simulation is still hot enough to move nodes. */
    fun isActive(): Boolean = alpha >= config.alphaMin

    /**
     * Update a single node position (e.g. after a user drag) so the next tick
     * does not overwrite the drag with a stale simulated position.
     */
    fun setNodePosition(id: String, position: Offset) {
        val i = indexOf[id] ?: return
        x[i] = position.x
        y[i] = position.y
        vx[i] = 0f
        vy[i] = 0f
    }

    /**
     * Pin a node so the integrator leaves its position alone (still exerts forces on others).
     * Call while the user is dragging; pair with [unpinNode] on drag end.
     */
    fun pinNode(id: String) {
        val i = indexOf[id] ?: return
        pinned[i] = true
        vx[i] = 0f
        vy[i] = 0f
    }

    /** Release a previously [pinNode]d node so physics can move it again. */
    fun unpinNode(id: String) {
        val i = indexOf[id] ?: return
        pinned[i] = false
        vx[i] = 0f
        vy[i] = 0f
    }

    fun isPinned(id: String): Boolean {
        val i = indexOf[id] ?: return false
        return pinned[i]
    }

    /** Replace simulation parameters without resetting node positions. */
    fun updateConfig(config: ForceSimulationConfig) {
        this.config = config
    }

    /**
     * Advance simulation by one tick.
     * @return true if still active (alpha > alphaMin), false if fully cooled.
     */
    fun tick(viewportWidth: Float, viewportHeight: Float): Boolean {
        if (alpha < config.alphaMin) return false
        if (viewportWidth <= 0f || viewportHeight <= 0f) return true
        if (n == 0) return false

        val a = alpha
        val repel = config.repelStrength * a
        val linkStr = config.linkStrength * a
        val linkDist = config.linkDistance
        val centerStr = config.centerStrength * a
        val repelMax = config.repelDistanceMax
        val repelMaxSq = repelMax * repelMax
        val maxVel = config.maxVelocity

        // Center toward live graph centroid (content space), not raw viewport pixels.
        var sumX = 0f
        var sumY = 0f
        for (i in 0 until n) {
            sumX += x[i]
            sumY += y[i]
        }
        val centerX = sumX / n
        val centerY = sumY / n

        // Zero forces
        fx.fill(0f)
        fy.fill(0f)

        // 1. Local repulsion via spatial hash (scales to 1000+ nodes)
        applyRepulsion(repel, repelMax, repelMaxSq)

        // 2. Edge springs
        for (e in edgeFrom.indices) {
            val i = edgeFrom[e]
            val j = edgeTo[e]
            val dx = x[i] - x[j]
            val dy = y[i] - y[j]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val force = (dist - linkDist) * linkStr
            val mx = (dx / dist) * force
            val my = (dy / dist) * force
            fx[i] -= mx
            fy[i] -= my
            fx[j] += mx
            fy[j] += my
        }

        // 3. Center pull + integrate (skip pinned nodes)
        val damping = 0.6f
        for (i in 0 until n) {
            if (pinned[i]) {
                vx[i] = 0f
                vy[i] = 0f
                continue
            }

            fx[i] += (centerX - x[i]) * centerStr
            fy[i] += (centerY - y[i]) * centerStr

            var newVx = (vx[i] + fx[i]) * damping
            var newVy = (vy[i] + fy[i]) * damping

            // Clamp velocity so a reheat cannot fling the whole graph.
            val speed = sqrt(newVx * newVx + newVy * newVy)
            if (speed > maxVel && speed > 0f) {
                val s = maxVel / speed
                newVx *= s
                newVy *= s
            }

            vx[i] = newVx
            vy[i] = newVy
            x[i] += newVx
            y[i] += newVy
        }

        // 4. Cool down
        alpha *= (1f - config.alphaDecay)
        return alpha >= config.alphaMin
    }

    private fun applyRepulsion(repel: Float, repelMax: Float, repelMaxSq: Float) {
        // For small graphs exact O(n²) is cheap and more stable.
        if (n < 250) {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    accumulateRepel(i, j, repel, repelMaxSq)
                }
            }
            return
        }

        val cellSize = (repelMax * 0.5f).coerceAtLeast(40f)
        val invCell = 1f / cellSize

        // Return lists to pool and clear buckets
        cellBuckets.values.forEach { list ->
            list.clear()
            bucketPool.add(list)
        }
        cellBuckets.clear()

        fun bucketFor(key: Long): MutableList<Int> {
            cellBuckets[key]?.let { return it }
            val list = if (bucketPool.isNotEmpty()) {
                bucketPool.removeAt(bucketPool.lastIndex)
            } else {
                ArrayList(8)
            }
            cellBuckets[key] = list
            return list
        }

        for (i in 0 until n) {
            val cx = (x[i] * invCell).toInt()
            val cy = (y[i] * invCell).toInt()
            val key = packCell(cx, cy)
            bucketFor(key).add(i)
        }

        // Interact with own cell + 8 neighbors
        val neighborOffsets = intArrayOf(
            -1, -1, 0, -1, 1, -1,
            -1, 0, 0, 0, 1, 0,
            -1, 1, 0, 1, 1, 1,
        )

        // Snapshot keys to avoid concurrent modification if any
        val keys = cellBuckets.keys.toList()
        for (key in keys) {
            val cx = unpackCellX(key)
            val cy = unpackCellY(key)
            val cell = cellBuckets[key] ?: continue

            // Within cell
            for (a in cell.indices) {
                for (b in a + 1 until cell.size) {
                    accumulateRepel(cell[a], cell[b], repel, repelMaxSq)
                }
            }

            // Neighbor cells (only half of the neighborhood to avoid double-counting)
            for (o in neighborOffsets.indices step 2) {
                val nx = cx + neighborOffsets[o]
                val ny = cy + neighborOffsets[o + 1]
                if (nx < cx || (nx == cx && ny <= cy)) continue // only "forward" neighbors
                val other = cellBuckets[packCell(nx, ny)] ?: continue
                for (i in cell) {
                    for (j in other) {
                        accumulateRepel(i, j, repel, repelMaxSq)
                    }
                }
            }
        }
    }

    private fun accumulateRepel(i: Int, j: Int, repel: Float, repelMaxSq: Float) {
        val dx = x[i] - x[j]
        val dy = y[i] - y[j]
        val distSq = dx * dx + dy * dy
        if (distSq > repelMaxSq || distSq < 1e-8f) return
        val dist = sqrt(distSq).coerceAtLeast(1f)
        val force = (repel / (dist * dist))
        val mx = (dx / dist) * force
        val my = (dy / dist) * force
        fx[i] += mx
        fy[i] += my
        fx[j] -= mx
        fy[j] -= my
    }

    /**
     * Gently re-energize the simulation (e.g. after a drag).
     * Only raises alpha if it is currently cooler than [alpha] — never restarts at full blast
     * on every pointer move.
     */
    fun reheat(alpha: Float = 0.25f) {
        val target = alpha.coerceIn(config.alphaMin, 1f)
        this.alpha = min(1f, maxOf(this.alpha, target))
    }

    private companion object {
        fun packCell(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)

        fun unpackCellX(key: Long): Int = (key shr 32).toInt()

        fun unpackCellY(key: Long): Int = key.toInt()
    }
}

/**
 * Composable that creates and manages a ForceSimulation with a frame-loop animation.
 * Physics runs on [Dispatchers.Default]; positions are applied on the UI dispatcher.
 *
 * @param state Graph state to update with new positions.
 * @param nodes List of nodes to simulate.
 * @param edges List of edges (connections).
 * @param config Force simulation parameters.
 * @param running Whether the simulation frame loop is active. Set to false to pause.
 * @param viewportWidth Canvas width for center-attraction calculations.
 * @param viewportHeight Canvas height for center-attraction calculations.
 * @return The ForceSimulation instance for external control (e.g., [ForceSimulation.reheat]).
 */
@Composable
fun <T> rememberForceSimulation(
    state: GraphState<T>,
    nodes: List<GraphNode<T>>,
    edges: List<GraphEdge>,
    config: ForceSimulationConfig = ForceSimulationConfig(),
    running: Boolean = true,
    viewportWidth: Float,
    viewportHeight: Float,
    /**
     * Optional seed positions. Preferred over reading [state] at first composition
     * (state often still has zeros then). Ensures the sim starts aligned with layout.
     */
    seedPositions: Map<String, Offset> = emptyMap(),
): ForceSimulation<T> {
    val simulation = remember(nodes, edges) {
        val centerX = (viewportWidth / 2f).takeIf { it > 0f } ?: 400f
        val centerY = (viewportHeight / 2f).takeIf { it > 0f } ?: 300f
        val initialPositions = nodes.associate { node ->
            val seeded = seedPositions[node.id]
            val existing = state.nodeStates[node.id]?.position
            val pos = when {
                seeded != null -> seeded
                existing != null && existing != Offset.Zero -> existing
                else -> Offset(
                    centerX + (node.id.hashCode() % 200) - 100f,
                    centerY + ((node.id.hashCode() / 7) % 200) - 100f,
                )
            }
            node.id to pos
        }
        ForceSimulation(nodes, edges, config, initialPositions)
    }

    // Keep live slider tweaks without resetting positions.
    simulation.updateConfig(config)

    // Keep looping while [running] is true so [reheat] after cooldown still animates.
    // Physics runs off the UI thread; a fresh positions map is produced each hot frame.
    // Pinned nodes keep their sim coordinates (set via [ForceSimulation.setNodePosition]).
    LaunchedEffect(running, viewportWidth, viewportHeight, config) {
        if (!running) return@LaunchedEffect
        try {
            while (running) {
                val framePositions = withContext(Dispatchers.Default) {
                    if (simulation.tick(viewportWidth, viewportHeight)) {
                        simulation.getPositions()
                    } else {
                        null
                    }
                }
                if (framePositions != null) {
                    // Do not clobber a node the user is currently dragging.
                    val movable = framePositions.filterKeys { id -> !simulation.isPinned(id) }
                    state.setNodePositions(movable)
                    delay(16) // ~60fps while hot
                } else {
                    delay(50) // idle while cooled; reheat will resume motion
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    return simulation
}
