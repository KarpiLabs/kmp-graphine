package io.karpilabs.graphine.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.layout.ForceDirectedLayout
import io.karpilabs.graphine.layout.GraphLayout
import io.karpilabs.graphine.layout.TreeLayout
import io.karpilabs.graphine.layout.TreeLayoutMode
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphGroup
import io.karpilabs.graphine.model.GraphNode
import io.karpilabs.graphine.rememberGraphState
import io.karpilabs.graphine.ui.GraphControls
import io.karpilabs.graphine.ui.GraphSearch
import io.karpilabs.graphine.ui.GraphSurface
import io.karpilabs.graphine.ui.Minimap
import io.karpilabs.graphine.ui.ZoomControls
import kotlinx.coroutines.launch

enum class Department(val color: Color) {
    EXECUTIVE(Color(0xFF9E9E9E)),
    ENGINEERING(Color(0xFF3B82F6)),
    FINANCE(Color(0xFF22C55E)),
    PRODUCT(Color(0xFFA855F7)),
}

data class Employee(val name: String, val title: String, val department: Department)

private val nodes = listOf(
    GraphNode(id = "1", data = Employee("Alice Johnson", "CEO", Department.EXECUTIVE)),
    GraphNode(id = "2", data = Employee("Bob Smith", "CTO", Department.ENGINEERING)),
    GraphNode(id = "3", data = Employee("Carol Davis", "CFO", Department.FINANCE)),
    GraphNode(id = "4", data = Employee("David Lee", "CPO", Department.PRODUCT)),
    GraphNode(id = "5", data = Employee("Eve Martinez", "Senior Engineer", Department.ENGINEERING)),
    GraphNode(id = "6", data = Employee("Frank Wu", "Backend Engineer", Department.ENGINEERING)),
    GraphNode(id = "7", data = Employee("Grace Kim", "Frontend Engineer", Department.ENGINEERING)),
    GraphNode(id = "8", data = Employee("Hank Osei", "Financial Analyst", Department.FINANCE)),
    GraphNode(id = "9", data = Employee("Ivy Chen", "Accountant", Department.FINANCE)),
    GraphNode(id = "10", data = Employee("Jack Ryan", "Product Manager", Department.PRODUCT)),
    GraphNode(id = "11", data = Employee("Kara Singh", "UX Designer", Department.PRODUCT)),
)

private val edges = listOf(
    GraphEdge(from = "1", to = "2"),
    GraphEdge(from = "1", to = "3"),
    GraphEdge(from = "1", to = "4"),
    GraphEdge(from = "2", to = "5"),
    GraphEdge(from = "2", to = "6"),
    GraphEdge(from = "2", to = "7"),
    GraphEdge(from = "3", to = "8"),
    GraphEdge(from = "3", to = "9"),
    GraphEdge(from = "4", to = "10"),
    GraphEdge(from = "4", to = "11"),
)

private val groups = listOf(
    GraphGroup(id = "eng", label = "Engineering", nodeIds = listOf("2", "5", "6", "7"), color = Department.ENGINEERING.color),
    GraphGroup(id = "fin", label = "Finance", nodeIds = listOf("3", "8", "9"), color = Department.FINANCE.color),
    GraphGroup(id = "prod", label = "Product", nodeIds = listOf("4", "10", "11"), color = Department.PRODUCT.color),
)

private enum class LayoutChoice(val label: String, val layout: GraphLayout) {
    TREE("Tree", TreeLayout(mode = TreeLayoutMode.STRAIGHT)),
    RADIAL("Radial", TreeLayout(mode = TreeLayoutMode.RADIAL)),
    FORCE("Force", ForceDirectedLayout()),
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KmpGraphine Showcase") {
        MaterialTheme {
            Surface {
                Showcase()
            }
        }
    }
}

@Composable
private fun Showcase() {
    val state: GraphState<Employee> = rememberGraphState(nodes = nodes, edges = edges, groups = groups)
    val scope = rememberCoroutineScope()

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var layoutChoice by remember { mutableStateOf(LayoutChoice.TREE) }

    LaunchedEffect(layoutChoice, viewportSize) {
        if (viewportSize == IntSize.Zero) return@LaunchedEffect
        val positions = layoutChoice.layout.calculatePositions(
            nodes = nodes,
            edges = edges,
            viewportWidth = viewportSize.width.toFloat(),
            viewportHeight = viewportSize.height.toFloat(),
        )
        state.setNodePositions(positions)
        state.targetId = "1"
        state.fitToScreenAnimated(viewportSize.width.toFloat(), viewportSize.height.toFloat())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LayoutPicker(
            selected = layoutChoice,
            onSelect = { layoutChoice = it },
            modifier = Modifier.padding(12.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewportSize = it.size },
        ) {
            GraphSurface(
                state = state,
                edgeConfig = EdgeConfig(showArrowheads = true),
                onNodeClick = { node ->
                    scope.launch {
                        state.centerOnNodeAnimated(node.id, viewportSize.width.toFloat(), viewportSize.height.toFloat())
                    }
                },
            ) { node, isDetailVisible ->
                EmployeeCard(node.data, isDetailVisible)
            }

            GraphSearch(
                state = state,
                viewportWidth = viewportSize.width.toFloat(),
                viewportHeight = viewportSize.height.toFloat(),
                nodeLabelProvider = { id -> nodes.find { it.id == id }?.data?.name ?: id },
                modifier = Modifier.align(Alignment.TopCenter).width(360.dp),
            )

            Minimap(
                state = state,
                viewportWidth = viewportSize.width.toFloat(),
                viewportHeight = viewportSize.height.toFloat(),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )

            GraphControls(
                state = state,
                viewportWidth = viewportSize.width.toFloat(),
                viewportHeight = viewportSize.height.toFloat(),
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )

            ZoomControls(
                state = state,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun LayoutPicker(selected: LayoutChoice, onSelect: (LayoutChoice) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        LayoutChoice.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = LayoutChoice.entries.size),
            ) {
                Text(choice.label)
            }
        }
    }
}

@Composable
private fun EmployeeCard(employee: Employee, isDetailVisible: Boolean) {
    Card(modifier = Modifier.width(190.dp)) {
        Row {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 56.dp)
                    .background(employee.department.color, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
            )
            Column(modifier = Modifier.padding(PaddingValues(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp))) {
                Text(employee.name, style = MaterialTheme.typography.titleSmall)
                if (isDetailVisible) {
                    Text(employee.title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
