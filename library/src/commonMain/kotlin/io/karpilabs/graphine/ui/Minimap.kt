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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.karpilabs.graphine.GraphState

/**
 * A small overview map of the entire graph.
 */
@Composable
fun Minimap(
    state: GraphState<*>,
    viewportWidth: Float,
    viewportHeight: Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
) {
    if (state.nodeStates.isEmpty()) return

    Box(
        modifier = modifier
            .size(150.dp, 100.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Calculate Bounding Box of all nodes
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            state.nodeStates.values.forEach {
                minX = minOf(minX, it.position.x)
                minY = minOf(minY, it.position.y)
                maxX = maxOf(maxX, it.position.x)
                maxY = maxOf(maxY, it.position.y)
            }

            val padding = 100f
            val graphWidth = (maxX - minX + padding * 2).coerceAtLeast(1f)
            val graphHeight = (maxY - minY + padding * 2).coerceAtLeast(1f)

            val scaleX = size.width / graphWidth
            val scaleY = size.height / graphHeight
            val scale = minOf(scaleX, scaleY)

            val drawOffset = Offset(
                (size.width - graphWidth * scale) / 2,
                (size.height - graphHeight * scale) / 2,
            )

            // 2. Draw Nodes as tiny dots
            state.nodeStates.values.forEach { node ->
                val x = (node.position.x - minX + padding) * scale + drawOffset.x
                val y = (node.position.y - minY + padding) * scale + drawOffset.y
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.5f),
                    radius = 2f,
                    center = Offset(x, y),
                )
            }

            // 3. Draw Viewport Rectangle
            val viewLeft = (-state.offset.x / state.scale - minX + padding) * scale + drawOffset.x
            val viewTop = (-state.offset.y / state.scale - minY + padding) * scale + drawOffset.y
            val viewWidth = (viewportWidth / state.scale) * scale
            val viewHeight = (viewportHeight / state.scale) * scale

            drawRoundRect(
                color = Color.Blue.copy(alpha = 0.4f),
                topLeft = Offset(viewLeft, viewTop),
                size = Size(viewWidth, viewHeight),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1f),
            )
        }
    }
}
