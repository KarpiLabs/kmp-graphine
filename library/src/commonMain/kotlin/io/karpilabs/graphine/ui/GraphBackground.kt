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
        val points = computeGridPoints(
            gridSize = gridSize,
            scale = state.scale,
            offsetX = state.offset.x,
            offsetY = state.offset.y,
            canvasWidth = size.width,
            canvasHeight = size.height,
        )
        if (points.isEmpty()) return@Canvas

        val safeScale = if (state.scale.isFinite()) state.scale.coerceIn(0.5f, 2.0f) else 1.0f
        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = dotColor,
            strokeWidth = 2f * safeScale,
        )
    }
}

/**
 * Computes dot grid offsets for canvas rendering with validation guards against non-finite
 * or near-zero inputs to prevent infinite loop UI thread freezes or memory exhaustion DoS.
 */
internal fun computeGridPoints(
    gridSize: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): List<Offset> {
    if (!gridSize.isFinite() || gridSize <= 0f || !scale.isFinite() || scale <= 0f) return emptyList()
    if (!offsetX.isFinite() || !offsetY.isFinite() || !canvasWidth.isFinite() || !canvasHeight.isFinite()) return emptyList()

    val rawScaledGrid = gridSize * scale
    if (!rawScaledGrid.isFinite() || rawScaledGrid <= 0f) return emptyList()

    // Minimum grid size threshold of 10f to prevent DoS via CPU/memory exhaustion from near-zero step sizes
    val scaledGridSize = maxOf(10f, rawScaledGrid)

    val startX = offsetX % scaledGridSize
    val startY = offsetY % scaledGridSize

    val points = mutableListOf<Offset>()
    var x = startX
    while (x < canvasWidth + scaledGridSize) {
        var y = startY
        while (y < canvasHeight + scaledGridSize) {
            points.add(Offset(x, y))
            y += scaledGridSize
        }
        x += scaledGridSize
    }
    return points
}
