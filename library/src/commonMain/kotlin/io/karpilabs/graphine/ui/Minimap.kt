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
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE

            val validPositions = state.nodeStates.values
                .map { it.position }
                .filter { it.x.isFinite() && it.y.isFinite() }
            if (validPositions.isEmpty()) return@Canvas

            validPositions.forEach { pos ->
                minX = minOf(minX, pos.x)
                minY = minOf(minY, pos.y)
                maxX = maxOf(maxX, pos.x)
                maxY = maxOf(maxY, pos.y)
            }

            val padding = 100f
            val graphWidth = (maxX - minX + padding * 2).coerceAtLeast(1f)
            val graphHeight = (maxY - minY + padding * 2).coerceAtLeast(1f)

            val scaleX = size.width / graphWidth
            val scaleY = size.height / graphHeight
            val scale = minOf(scaleX, scaleY)
            if (!scale.isFinite() || scale <= 0f) return@Canvas

            val drawOffset = Offset(
                (size.width - graphWidth * scale) / 2,
                (size.height - graphHeight * scale) / 2,
            )
            if (!drawOffset.x.isFinite() || !drawOffset.y.isFinite()) return@Canvas

            // 2. Draw Nodes as tiny dots
            state.nodeStates.values.forEach { node ->
                val pos = node.position
                if (!pos.x.isFinite() || !pos.y.isFinite()) return@forEach
                val x = (pos.x - minX + padding) * scale + drawOffset.x
                val y = (pos.y - minY + padding) * scale + drawOffset.y
                if (x.isFinite() && y.isFinite()) {
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.5f),
                        radius = 2f,
                        center = Offset(x, y),
                    )
                }
            }

            // 3. Draw Viewport Rectangle
            val safeStateScale = if (state.scale.isFinite() && state.scale > 0f) state.scale else 1f
            val safeOffsetX = if (state.offset.x.isFinite()) state.offset.x else 0f
            val safeOffsetY = if (state.offset.y.isFinite()) state.offset.y else 0f
            val safeVpWidth = if (viewportWidth.isFinite() && viewportWidth >= 0f) viewportWidth else 0f
            val safeVpHeight = if (viewportHeight.isFinite() && viewportHeight >= 0f) viewportHeight else 0f

            val viewLeft = (-safeOffsetX / safeStateScale - minX + padding) * scale + drawOffset.x
            val viewTop = (-safeOffsetY / safeStateScale - minY + padding) * scale + drawOffset.y
            val viewWidth = (safeVpWidth / safeStateScale) * scale
            val viewHeight = (safeVpHeight / safeStateScale) * scale

            if (viewLeft.isFinite() && viewTop.isFinite() && viewWidth.isFinite() && viewHeight.isFinite() && viewWidth >= 0f && viewHeight >= 0f) {
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
}
