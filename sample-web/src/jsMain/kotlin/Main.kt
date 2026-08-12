import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import io.karpilabs.graphine.ui.GraphSurface
import io.karpilabs.graphine.ui.Minimap
import io.karpilabs.graphine.ui.ZoomControls
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.compose.web.renderComposable

data class Note(val title: String, val cluster: String)

private val CanvasBackground = Color(0xFF1E1E1E)
private val LinkEdgeColor = Color(0xFF8B3A3A).copy(alpha = 0.40f)

private data class ClusterSpec(
    val id: String,
    val label: String,
    val color: Color,
    val count: Int,
    val angle: Float,
    val radius: Float,
    val spread: Float,
)

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

    val simulation = rememberForceSimulation(
        state = state,
        nodes = graph.nodes,
        edges = graph.edges,
        config = forceConfig,
        running = animationRunning && didInitialFit,
        viewportWidth = viewportSize.width.toFloat(),
        viewportHeight = viewportSize.height.toFloat(),
        seedPositions = graph.initialPositions,
    )

    LaunchedEffect(viewportSize.width, viewportSize.height) {
        if (didInitialFit || viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect

        state.setNodePositions(graph.initialPositions)
        graph.initialPositions.forEach { (id, pos) ->
            simulation.setNodePosition(id, pos)
        }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .onGloballyPositioned { viewportSize = it.size },
    ) {
        GraphSurface(
            state = state,
            modifier = Modifier.fillMaxSize(),
            edgeConfig = edgeConfig,
            showGrid = false,
            enableZoom = true,
            enablePanning = true,
            enablePathHighlighting = true,
            nodeRenderMode = NodeRenderMode.DOT,
            dotStyle = dotStyle,
            onNodeClick = { node ->
                println("Clicked: ${node.data.title} (${node.data.cluster})")
            },
            onNodeDragged = { node ->
                val pos = state.nodeStates[node.id]?.position ?: return@GraphSurface
                simulation.pinNode(node.id)
                simulation.setNodePosition(node.id, pos)
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
                    .padding(16.dp),
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

fun main() {
    renderComposable(rootElementId = "root") {
        MaterialTheme(colorScheme = darkScheme) {
            GraphViewDemo()
        }
    }
}
