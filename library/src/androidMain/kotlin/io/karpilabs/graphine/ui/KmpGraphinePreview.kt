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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.karpilabs.graphine.layout.TreeLayout
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.GraphEdge
import io.karpilabs.graphine.model.GraphGroup
import io.karpilabs.graphine.model.GraphNode
import io.karpilabs.graphine.rememberGraphState

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun FullGraphPreview() {
    val nodes = listOf(
        GraphNode("1", "Root"),
        GraphNode("2", "Sister A"),
        GraphNode("3", "Sister B"),
        GraphNode("4", "Brand 1"),
        GraphNode("5", "Brand 2"),
    )
    val edges = listOf(
        GraphEdge("1", "2"),
        GraphEdge("1", "3"),
        GraphEdge("2", "4"),
        GraphEdge("3", "5"),
    )

    val state = rememberGraphState(nodes, edges)
    state.targetId = "1"

    // Set some initial positions
    state.setNodePositions(
        mapOf(
            "1" to Offset(500f, 100f),
            "2" to Offset(300f, 300f),
            "3" to Offset(700f, 300f),
            "4" to Offset(200f, 500f),
            "5" to Offset(800f, 500f),
        ),
    )

    val density = LocalDensity.current

    MaterialTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            GraphSurface(
                state = state,
                edgeConfig = EdgeConfig(showArrowheads = true),
                nodeContent = { node, _ ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = node.data, style = MaterialTheme.typography.labelSmall)
                    }
                },
            )

            GraphControls(
                state = state,
                viewportWidth = widthPx,
                viewportHeight = heightPx,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 500, heightDp = 800)
@Composable
fun GroupedGraphPreview() {
    val nodes = listOf(
        GraphNode("root", "Nestle"),
        GraphNode("c1", "Confectionery"),
        GraphNode("c2", "Water"),
        GraphNode("b1", "KitKat"),
        GraphNode("b2", "Smarties"),
        GraphNode("b3", "Aero"),
        GraphNode("b4", "Perrier"),
        GraphNode("b5", "Vittel"),
    )
    val edges = listOf(
        GraphEdge("root", "c1"),
        GraphEdge("root", "c2"),
        GraphEdge("c1", "b1"),
        GraphEdge("c1", "b2"),
        GraphEdge("c1", "b3"),
        GraphEdge("c2", "b4"),
        GraphEdge("c2", "b5"),
    )

    val groups = listOf(
        GraphGroup("g1", "Sweets", listOf("b1", "b2", "b3")),
        GraphGroup("g2", "Drinks", listOf("b4", "b5")),
    )

    val state = rememberGraphState(nodes, edges, groups)

    // Use the updated TreeLayout with clustering
    val layout = TreeLayout(horizontalSpacing = 200f, verticalSpacing = 300f)
    val positions = layout.calculatePositions(nodes, edges, 1000f, 1000f)
    state.setNodePositions(positions)

    MaterialTheme {
        GraphSurface(
            state = state,
            edgeConfig = EdgeConfig(showArrowheads = true),
            nodeContent = { node, _ ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = node.data, style = MaterialTheme.typography.labelSmall)
                }
            },
        )
    }
}
