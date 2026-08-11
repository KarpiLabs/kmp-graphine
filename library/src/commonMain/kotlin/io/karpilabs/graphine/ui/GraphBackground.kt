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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import io.karpilabs.graphine.GraphState

/**
 * Renders a modern dot-grid background that pans and zooms with the graph.
 * Subtle dotted grid for the infinite canvas.
 */
@Composable
fun GraphBackground(
    state: GraphState<*>,
    dotColor: Color = Color.Gray.copy(alpha = 0.15f),
    gridSize: Float = 60f,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaledGridSize = gridSize * state.scale

        // We only want to draw dots that are visible on screen
        val startX = state.offset.x % scaledGridSize
        val startY = state.offset.y % scaledGridSize

        val points = mutableListOf<Offset>()

        var x = startX
        while (x < size.width + scaledGridSize) {
            var y = startY
            while (y < size.height + scaledGridSize) {
                points.add(Offset(x, y))
                y += scaledGridSize
            }
            x += scaledGridSize
        }

        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = dotColor,
            strokeWidth = 2f * state.scale.coerceIn(0.5f, 2.0f),
        )
    }
}
