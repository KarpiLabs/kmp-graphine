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

import androidx.compose.ui.graphics.PathEffect

/**
 * Visual configuration for group background zones.
 */
data class GroupConfig(
    val padding: Float = 120f,
    val cornerRadius: Float = 32f,
    val backgroundAlpha: Float = 0.03f,
    val borderAlpha: Float = 0.2f,
    val borderWidth: Float = 1.5f,
    val pathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
)

/**
 * Global visual and interaction settings for the graph.
 */
data class GraphConfig(
    val detailZoomThreshold: Float = 0.6f,
    val minScale: Float = 0.1f,
    val maxScale: Float = 5f,
    val viewportPadding: Float = 100f,
    val fitToScreenPadding: Float = 150f,
    val groupConfig: GroupConfig = GroupConfig(),
)
