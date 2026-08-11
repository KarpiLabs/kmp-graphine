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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.karpilabs.graphine.GraphState
import kotlinx.coroutines.launch

/**
 * Vertical zoom +/- controls for the graph.
 */
@Composable
fun ZoomControls(
    state: GraphState<*>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(24.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = {
            scope.launch {
                state.animateTo((state.scale * 1.2f).coerceAtMost(5f), state.offset)
            }
        }) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom In")
        }

        IconButton(onClick = {
            scope.launch {
                state.animateTo((state.scale / 1.2f).coerceAtLeast(0.1f), state.offset)
            }
        }) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
        }
    }
}
