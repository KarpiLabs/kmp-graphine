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
 * Defines a visual cluster of nodes.
 *
 * @property id Unique identifier for the group.
 * @property label Optional label displayed for the group.
 * @property nodeIds List of [GraphNode.id]s that belong to this group.
 * @property color The primary theme color for the group zone.
 */
data class GraphGroup(
    val id: String,
    val label: String? = null,
    val nodeIds: List<String>,
    val color: Color = Color.LightGray.copy(alpha = 0.1f)
)
