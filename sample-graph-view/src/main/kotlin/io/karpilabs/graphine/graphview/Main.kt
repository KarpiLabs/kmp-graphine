package io.karpilabs.graphine.graphview

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.karpilabs.graphine.layout.ForceSimulationConfig
import io.karpilabs.graphine.layout.rememberForceSimulation
import io.karpilabs.graphine.model.DotStyle
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.EdgeStyle
import io.karpilabs.graphine.model.GraphConfig
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphGroup
import io.karpilabs.graphine.model.GraphNode
import io.karpilabs.graphine.model.NodeRenderMode
import io.karpilabs.graphine.rememberGraphState
import io.karpilabs.graphine.ui.GraphSettingsPanel
import io.karpilabs.graphine.ui.GraphSurface
import io.karpilabs.graphine.ui.Minimap
import io.karpilabs.graphine.ui.ZoomControls
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight note payload for the Graph View demo.
 * Nodes are always rendered as canvas dots ([NodeRenderMode.DOT]) — never as Composables —
 * so this sample can host 1000+ nodes comfortably.
 */
data class Note(val title: String, val cluster: String)

private val CanvasBackground = Color(0xFF1E1E1E)
private val LinkEdgeColor = Color(0xFF8B3A3A).copy(alpha = 0.40f)

private data class ClusterSpec(
    val id: String,
    val label: String,
    val color: Color,
    val count: Int,
    /** Polar placement of the cluster center (angle radians, radius). */
    val angle: Float,
    val radius: Float,
    val spread: Float,
)

/**
 * Multi-cluster palette for a dense graph demo.
 * Counts sum to ~1100 nodes (+ ring) so the demo stresses DOT mode at 1000+ scale.
 */
private val clusterSpecs = listOf(
    ClusterSpec("hub", "Hub", Color(0xFFE67E22), 220, 0f, 0f, 180f),
    ClusterSpec("core-white", "Core", Color(0xFFE8E8E8), 110, 0.4f, 100f, 110f),
    ClusterSpec("cyan", "Cyan", Color(0xFF4FC3F7), 85, 2.2f, 280f, 90f),
    ClusterSpec("green", "Green", Color(0xFF66BB6A), 95, 4.0f, 340f, 100f),
    ClusterSpec("purple", "Purple", Color(0xFFAB47BC), 75, 5.2f, 300f, 80f),
    ClusterSpec("magenta", "Magenta", Color(0xFFEC407A), 70, 1.0f, 320f, 75f),
    ClusterSpec("blue", "Blue", Color(0xFF42A5F5), 80, 3.3f, 310f, 85f),
    ClusterSpec("amber", "Amber", Color(0xFFFFCA28), 65, 5.8f, 260f, 70f),
    ClusterSpec("red", "Red", Color(0xFFEF5350), 85, 0.9f, 380f, 90f),
    ClusterSpec("teal", "Teal", Color(0xFF26A69A), 75, 4.8f, 360f, 80f),
    ClusterSpec("lime", "Lime", Color(0xFFC0CA33), 60, 2.8f, 400f, 70f),
    ClusterSpec("indigo", "Indigo", Color(0xFF5C6BC0), 60, 1.6f, 350f, 70f),
)

private data class GraphData(
    val nodes: List<GraphNode<Note>>,
    val edges: List<GraphEdge>,
    val groups: List<GraphGroup>,
    val initialPositions: Map<String, Offset>,
)

/**
 * Build a dense, multi-cluster graph (~1200+ nodes) for the Graph View demo.
 */
private fun generateGraph(seed: Int = 42): GraphData {
    val rng = Random(seed)
    val nodes = mutableListOf<GraphNode<Note>>()
    val edges = mutableListOf<GraphEdge>()
    val groups = mutableListOf<GraphGroup>()
    val positions = mutableMapOf<String, Offset>()
    val clusterNodeIds = mutableMapOf<String, List<String>>()

    val originX = 1200f
    val originY = 900f

    clusterSpecs.forEach { spec ->
        val ids = mutableListOf<String>()
        val cx = originX + cos(spec.angle.toDouble()).toFloat() * spec.radius
        val cy = originY + sin(spec.angle.toDouble()).toFloat() * spec.radius

        repeat(spec.count) { i ->
            val id = "${spec.id}_$i"
            ids += id
            nodes += GraphNode(id = id, data = Note(title = "${spec.label} $i", cluster = spec.id))

            // Gaussian-ish blob around cluster center.
            val ox = (rng.nextFloat() - 0.5f) * spec.spread * 2f
            val oy = (rng.nextFloat() - 0.5f) * spec.spread * 2f
            positions[id] = Offset(cx + ox, cy + oy)
        }
        clusterNodeIds[spec.id] = ids
        groups += GraphGroup(
            id = spec.id,
            label = spec.label,
            nodeIds = ids,
            color = spec.color,
        )

        // Hub-and-spoke + sparse mesh (keep edge count reasonable at 1k+ nodes).
        val hub = ids.first()
        ids.drop(1).forEach { leaf ->
            if (rng.nextFloat() < 0.55f) {
                edges += GraphEdge(from = hub, to = leaf)
            }
        }
        val linkBudget = (ids.size * 0.9).toInt()
        repeat(linkBudget) {
            val a = ids[rng.nextInt(ids.size)]
            val b = ids[rng.nextInt(ids.size)]
            if (a != b) {
                edges += GraphEdge(from = a, to = b)
            }
        }
    }

    // Bridges between neighboring clusters.
    val ordered = clusterSpecs.map { it.id }
    for (i in ordered.indices) {
        val aIds = clusterNodeIds.getValue(ordered[i])
        val bIds = clusterNodeIds.getValue(ordered[(i + 1) % ordered.size])
        repeat(5 + rng.nextInt(4)) {
            edges += GraphEdge(
                from = aIds[rng.nextInt(aIds.size)],
                to = bIds[rng.nextInt(bIds.size)],
            )
        }
    }
    // Extra long-range bridges into the hub.
    val hubIds = clusterNodeIds.getValue("hub")
    clusterSpecs.filter { it.id != "hub" }.forEach { spec ->
        val ids = clusterNodeIds.getValue(spec.id)
        repeat(4) {
            edges += GraphEdge(
                from = hubIds[rng.nextInt(hubIds.size)],
                to = ids[rng.nextInt(ids.size)],
            )
        }
    }

    // Sparse outer ring of multicolored orphans (screenshot halo).
    val ringCount = 160
    val ringIds = mutableListOf<String>()
    repeat(ringCount) { i ->
        val id = "ring_$i"
        ringIds += id
        nodes += GraphNode(id = id, data = Note(title = "Orphan $i", cluster = "ring"))
        val angle = (i.toFloat() / ringCount) * (2f * PI.toFloat()) + rng.nextFloat() * 0.04f
        val r = 620f + rng.nextFloat() * 100f
        positions[id] = Offset(originX + cos(angle) * r, originY + sin(angle) * r)

        if (rng.nextFloat() < 0.28f) {
            val targetCluster = clusterSpecs[rng.nextInt(clusterSpecs.size)]
            val target = clusterNodeIds.getValue(targetCluster.id).random(rng)
            edges += GraphEdge(from = id, to = target)
        }
    }
    groups += GraphGroup(
        id = "ring",
        label = "Orphans",
        nodeIds = ringIds,
        color = Color(0xFF9E9E9E),
    )

    val uniqueEdges = edges
        .map { if (it.from <= it.to) it else GraphEdge(from = it.to, to = it.from) }
        .distinctBy { it.from to it.to }

    return GraphData(nodes, uniqueEdges, groups, positions)
}

/** High-contrast dark scheme so the settings panel labels stay legible. */
private val darkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFFA8C7FA),
    onSecondary = Color(0xFF0B1B33),
    surface = Color(0xFF2B2B2E),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF3A3A3E),
    onSurfaceVariant = Color(0xFFD0D0D4),
    background = CanvasBackground,
    onBackground = Color(0xFFF0F0F0),
    outline = Color(0xFF6B6B70),
    outlineVariant = Color(0xFF4A4A4E),
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KmpGraphine — Graph View",
        state = rememberWindowState(width = 1400.dp, height = 900.dp),
    ) {
        MaterialTheme(colorScheme = darkScheme) {
            GraphViewDemo()
        }
    }
}

@Composable
private fun GraphViewDemo() {
    val graph = remember { generateGraph() }
    val colorByNodeId = remember(graph.groups) {
        buildMap {
            graph.groups.forEach { group ->
                group.nodeIds.forEach { id -> put(id, group.color) }
            }
            val ringPalette = listOf(
                Color(0xFFE8E8E8), Color(0xFFAB47BC), Color(0xFF66BB6A),
                Color(0xFF4FC3F7), Color(0xFFEF5350), Color(0xFFFFCA28),
                Color(0xFFEC407A), Color(0xFF42A5F5), Color(0xFF26A69A),
            )
            graph.nodes.filter { it.data.cluster == "ring" }.forEachIndexed { index, node ->
                put(node.id, ringPalette[index % ringPalette.size])
            }
        }
    }

    // DOT-only: no groups in GraphState (avoids zone overlays). Colors come from [colorByNodeId].
    val state = rememberGraphState(
        nodes = graph.nodes,
        edges = graph.edges,
        config = GraphConfig(detailZoomThreshold = 1.5f),
    )

    var groups by remember { mutableStateOf(graph.groups) }
    var edgeConfig by remember {
        mutableStateOf(
            EdgeConfig(
                color = LinkEdgeColor,
                width = 0.7f,
                style = EdgeStyle.STRAIGHT,
                showArrowheads = false,
            ),
        )
    }
    var graphConfig by remember { mutableStateOf(state.config) }
    var forceConfig by remember {
        mutableStateOf(
            ForceSimulationConfig(
                centerStrength = 0.02f,
                repelStrength = 6_000f,
                linkStrength = 0.035f,
                linkDistance = 42f,
                alphaDecay = 0.015f,
                alphaMin = 0.001f,
                repelDistanceMax = 220f,
            ),
        )
    }
    var animationRunning by remember { mutableStateOf(true) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    // While hovering the settings panel, disable graph pan/zoom so trackpad
    // gestures scroll the menu instead of panning the canvas.
    val settingsInteraction = remember { MutableInteractionSource() }
    val settingsHovered by settingsInteraction.collectIsHoveredAsState()

    var baseRadius by remember { mutableStateOf(2.8f) }
    val dotStyle = remember(baseRadius, colorByNodeId) {
        DotStyle<Note>(
            baseRadius = baseRadius,
            radiusPerDegree = 0.12f,
            maxRadius = 9f,
            color = { node, _ -> colorByNodeId[node.id] ?: Color.Gray },
        )
    }

    var didInitialFit by remember { mutableStateOf(false) }

    // Seed the sim with the same layout the camera will frame.
    val simulation = rememberForceSimulation(
        state = state,
        nodes = graph.nodes,
        edges = graph.edges,
        config = forceConfig,
        // Don't run physics until the first camera fit is done.
        running = animationRunning && didInitialFit,
        viewportWidth = viewportSize.width.toFloat(),
        viewportHeight = viewportSize.height.toFloat(),
        seedPositions = graph.initialPositions,
    )

    // Place nodes and frame the main cluster as soon as the canvas has a real size.
    // (Previously positions and fit raced — fit often ran while everything was still at 0,0.)
    LaunchedEffect(viewportSize.width, viewportSize.height) {
        if (didInitialFit || viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect

        state.setNodePositions(graph.initialPositions)
        // Keep sim arrays in lockstep with GraphState before any ticks run.
        graph.initialPositions.forEach { (id, pos) ->
            simulation.setNodePosition(id, pos)
        }

        // Trim outer orphans/ring so zoom frames the dense clusters, not the halo.
        state.fitToScreenAnimated(
            viewportWidth = viewportSize.width.toFloat(),
            viewportHeight = viewportSize.height.toFloat(),
            padding = 64f,
            trimFraction = 0.08f,
            immediate = true,
        )
        didInitialFit = true

        println(
            "Graph View ready: ${graph.nodes.size} nodes, ${graph.edges.size} edges " +
                "(NodeRenderMode.DOT) · fitted to main cluster",
        )
    }

    LaunchedEffect(graphConfig) {
        state.config = graphConfig
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground),
    ) {
        GraphSettingsPanel(
            groups = groups,
            onGroupsChanged = { groups = it },
            edgeConfig = edgeConfig,
            onEdgeConfigChanged = { edgeConfig = it },
            graphConfig = graphConfig,
            onGraphConfigChanged = { graphConfig = it },
            dotStyle = dotStyle,
            onDotStyleChanged = { updated ->
                baseRadius = updated.baseRadius
            },
            forceSimulationConfig = forceConfig,
            onForceSimulationConfigChanged = {
                forceConfig = it
                simulation.reheat(0.6f)
            },
            animationRunning = animationRunning,
            onAnimationRunningChanged = { animationRunning = it },
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .hoverable(settingsInteraction),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { viewportSize = it.size },
        ) {
            // Always DOT — never COMPOSABLE. Required for 1000+ node performance.
            GraphSurface(
                state = state,
                modifier = Modifier.fillMaxSize(),
                edgeConfig = edgeConfig,
                showGrid = false,
                enableZoom = !settingsHovered,
                enablePanning = !settingsHovered,
                enablePathHighlighting = true,
                nodeRenderMode = NodeRenderMode.DOT,
                dotStyle = dotStyle,
                onNodeClick = { node ->
                    println("Clicked: ${node.data.title} (${node.data.cluster})")
                },
                onNodeDragged = { node ->
                    // Keep the sim in lockstep with the pointer and pin so physics
                    // does not fight (or explode) the node being dragged.
                    val pos = state.nodeStates[node.id]?.position ?: return@GraphSurface
                    simulation.pinNode(node.id)
                    simulation.setNodePosition(node.id, pos)
                    // Gentle reheat: only raises alpha if cooler — neighbors settle, no blast.
                    simulation.reheat(0.2f)
                },
                onNodeDragEnd = { node ->
                    val pos = state.nodeStates[node.id]?.position
                    if (pos != null) simulation.setNodePosition(node.id, pos)
                    simulation.unpinNode(node.id)
                    simulation.reheat(0.15f)
                },
                nodeContent = null,
            )

            if (viewportSize.width > 0) {
                Minimap(
                    state = state,
                    viewportWidth = viewportSize.width.toFloat(),
                    viewportHeight = viewportSize.height.toFloat(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .width(160.dp),
                )
                ZoomControls(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}
