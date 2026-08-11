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

package io.karpilabs.graphine.model

import androidx.compose.ui.graphics.Color

/**
 * Visual styling for nodes rendered in [NodeRenderMode.DOT] mode.
 *
 * Dots are sized based on node degree (connection count), with the color determined by the [color] lambda.
 * This allows for degree-based sizing and group-based or custom coloring in a single, efficient Canvas pass.
 *
 * @param T The type of data held by [GraphNode]s.
 * @property baseRadius The minimum radius of a dot (for degree 0).
 * @property radiusPerDegree The radius increment per connection; final radius = baseRadius + (degree * radiusPerDegree), capped at [maxRadius].
 * @property maxRadius The maximum radius a dot can reach.
 * @property color Lambda that returns the color for a given node and its degree. Receives the node and its degree (connection count).
 *                 Default implementation checks if the node belongs to a group (via state.groups) and uses the group's color;
 *                 falls back to neutral gray for ungrouped nodes.
 */
data class DotStyle<T>(
    val baseRadius: Float = 4f,
    val radiusPerDegree: Float = 0.5f,
    val maxRadius: Float = 12f,
    val color: (node: GraphNode<T>, degree: Int) -> Color = { _, _ ->
        Color.Gray.copy(alpha = 0.6f)
    },
) {
    /**
     * Computes the radius for a dot given its degree.
     *
     * @param degree The node's degree (connection count).
     * @return The radius, clamped to [maxRadius].
     */
    fun computeRadius(degree: Int): Float = (baseRadius + degree * radiusPerDegree).coerceAtMost(maxRadius)
}
