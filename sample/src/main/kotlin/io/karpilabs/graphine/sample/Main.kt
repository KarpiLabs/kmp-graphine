package io.karpilabs.graphine.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.karpilabs.graphine.GraphState
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphNode
import io.karpilabs.graphine.rememberGraphState
import io.karpilabs.graphine.ui.GraphSurface

data class Person(val name: String, val title: String)

private val nodes = listOf(
    GraphNode(id = "1", data = Person("Alice Johnson", "CEO")),
    GraphNode(id = "2", data = Person("Bob Smith", "CTO")),
    GraphNode(id = "3", data = Person("Carol Davis", "CFO")),
    GraphNode(id = "4", data = Person("David Lee", "Engineering Lead")),
    GraphNode(id = "5", data = Person("Eve Martinez", "Finance Lead")),
)

private val edges = listOf(
    GraphEdge(from = "1", to = "2"),
    GraphEdge(from = "1", to = "3"),
    GraphEdge(from = "2", to = "4"),
    GraphEdge(from = "3", to = "5"),
)

private val initialPositions = mapOf(
    "1" to Offset(420f, 60f),
    "2" to Offset(200f, 260f),
    "3" to Offset(640f, 260f),
    "4" to Offset(200f, 460f),
    "5" to Offset(640f, 460f),
)

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KmpGraphine Sample - Org Chart") {
        MaterialTheme {
            Surface {
                OrgChart()
            }
        }
    }
}

@Composable
private fun OrgChart() {
    val state: GraphState<Person> = rememberGraphState(nodes = nodes, edges = edges)

    LaunchedEffect(Unit) {
        state.setNodePositions(initialPositions)
    }

    GraphSurface(
        state = state,
        modifier = Modifier,
        onNodeClick = { node -> println("Clicked: ${node.data.name}") },
    ) { node, isDetailVisible ->
        PersonCard(node.data, isDetailVisible)
    }
}

@Composable
private fun PersonCard(person: Person, isDetailVisible: Boolean) {
    Card(modifier = Modifier.width(180.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleSmall)
            if (isDetailVisible) {
                Text(person.title, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
