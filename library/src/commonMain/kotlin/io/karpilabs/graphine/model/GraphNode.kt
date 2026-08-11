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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * A generic data container for a node in the graph.
 *
 * @param T The type of user data associated with this node.
 * @property id A unique identifier for the node.
 * @property data The user-defined data payload.
 */
data class GraphNode<T>(
    val id: String,
    val data: T
)

/**
 * Internal state for a [GraphNode], tracking its layout properties.
 */
data class GraphNodeState<T>(
    val node: GraphNode<T>,
    val position: Offset = Offset.Zero,
    val size: IntSize = IntSize.Zero
)
