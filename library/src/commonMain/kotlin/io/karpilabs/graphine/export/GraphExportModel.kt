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

package io.karpilabs.graphine.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * A single exported node: drawn as a filled circle with a centered label.
 * Coordinates are in the exported image's own space (origin top-left, padding already applied) —
 * not the source [io.karpilabs.graphine.GraphState]'s content space.
 */
data class ExportNode(
    val id: String,
    val center: Offset,
    val radius: Float,
    val color: Color,
    val label: String,
)

/**
 * A single exported edge.
 *
 * @property points Waypoints in drawing order (always at least 2). When [isCubicBezier] is false,
 *                   consecutive points are connected with straight segments (a polyline — used for
 *                   [io.karpilabs.graphine.model.EdgeStyle.STRAIGHT] and `.ORTHOGONAL`). When true,
 *                   [points] has exactly 4 entries `(start, control1, control2, end)` forming one
 *                   cubic Bézier curve (used for `.CURVED`).
 */
data class ExportEdge(
    val points: List<Offset>,
    val isCubicBezier: Boolean,
    val color: Color,
    val strokeWidth: Float,
    val arrowhead: Boolean,
)

/**
 * A flat, platform-agnostic snapshot of a graph ready to rasterize or serialize.
 * Produced by [GraphExport.buildModel] and consumed by [GraphExport.toSvg]
 * (and, on JVM/Desktop, `GraphExportModel.toPngBytes()`).
 */
data class GraphExportModel(
    val width: Float,
    val height: Float,
    val backgroundColor: Color,
    val nodes: List<ExportNode>,
    val edges: List<ExportEdge>,
)
