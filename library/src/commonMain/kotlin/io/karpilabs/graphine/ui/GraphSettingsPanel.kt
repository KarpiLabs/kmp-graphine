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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.graphine.layout.ForceSimulationConfig
import io.karpilabs.graphine.model.DotStyle
import io.karpilabs.graphine.model.EdgeConfig
import io.karpilabs.graphine.model.GraphConfig
import io.karpilabs.graphine.model.GraphGroup
import kotlin.random.Random

/**
 * Settings panel for interactive graph visualization (groups, display, and forces).
 * Operates on mutable state held at the call site for full control over all graph parameters.
 *
 * Uses [Surface] so content color tracks the theme (readable on both light and dark schemes).
 *
 * @param groups Mutable list of node groups (add/remove/edit groups).
 * @param onGroupsChanged Callback when groups list changes.
 * @param edgeConfig Edge appearance configuration (arrows, thickness).
 * @param onEdgeConfigChanged Callback when edge config changes.
 * @param graphConfig Graph behavior configuration (zoom thresholds).
 * @param onGraphConfigChanged Callback when graph config changes.
 * @param dotStyle Dot appearance configuration (size, coloring).
 * @param onDotStyleChanged Callback when dot style changes.
 * @param forceSimulationConfig Physics simulation parameters.
 * @param onForceSimulationConfigChanged Callback when force config changes.
 * @param animationRunning Whether force simulation is active.
 * @param onAnimationRunningChanged Callback to toggle animation.
 */
@Composable
fun GraphSettingsPanel(
    groups: List<GraphGroup>,
    onGroupsChanged: (List<GraphGroup>) -> Unit,
    edgeConfig: EdgeConfig,
    onEdgeConfigChanged: (EdgeConfig) -> Unit,
    graphConfig: GraphConfig,
    onGraphConfigChanged: (GraphConfig) -> Unit,
    dotStyle: DotStyle<*>,
    onDotStyleChanged: (DotStyle<*>) -> Unit,
    forceSimulationConfig: ForceSimulationConfig,
    onForceSimulationConfigChanged: (ForceSimulationConfig) -> Unit,
    animationRunning: Boolean = true,
    onAnimationRunningChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        inactiveTrackColor = colors.onSurface.copy(alpha = 0.22f),
        activeTickColor = colors.onPrimary,
        inactiveTickColor = colors.onSurface.copy(alpha = 0.22f),
    )
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = colors.primary,
        uncheckedColor = colors.onSurface.copy(alpha = 0.7f),
        checkmarkColor = colors.onPrimary,
    )

    val scrollState = rememberScrollState()

    // BoxWithConstraints supplies a finite max height so verticalScroll can overflow.
    // Do not install a consuming pointerInput here — it steals events from sliders/buttons.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .clipToBounds(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.surface,
            contentColor = colors.onSurface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(maxWidth)
                    .height(maxHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SectionHeader("Groups")

                groups.forEachIndexed { index, group ->
                    GroupRow(
                        group = group,
                        onUpdate = { updated ->
                            val newGroups = groups.toMutableList()
                            newGroups[index] = updated
                            onGroupsChanged(newGroups)
                        },
                        onDelete = {
                            onGroupsChanged(groups.filterIndexed { i, _ -> i != index })
                        },
                    )
                }

                Button(
                    onClick = {
                        val newGroup = GraphGroup(
                            id = "group_${groups.size}_${Random.nextInt(10000)}",
                            label = "Group ${groups.size + 1}",
                            nodeIds = emptyList(),
                            color = Color.hsl(
                                (groups.size * 60f) % 360f,
                                0.7f,
                                0.55f,
                            ),
                        )
                        onGroupsChanged(groups + newGroup)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Group", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }

                PanelDivider()

                SectionHeader("Display")

                ToggleRow(
                    label = "Show Arrowheads",
                    checked = edgeConfig.showArrowheads,
                    onCheckedChange = { checked ->
                        onEdgeConfigChanged(edgeConfig.copy(showArrowheads = checked))
                    },
                    checkboxColors = checkboxColors,
                )

                if (onAnimationRunningChanged != null) {
                    ToggleRow(
                        label = "Animate",
                        checked = animationRunning,
                        onCheckedChange = onAnimationRunningChanged,
                        checkboxColors = checkboxColors,
                    )
                }

                SliderRow(
                    label = "Text Fade Threshold",
                    valueText = formatFloat(graphConfig.detailZoomThreshold, 2),
                    value = graphConfig.detailZoomThreshold,
                    valueRange = 0.1f..2f,
                    onValueChange = { newThreshold ->
                        onGraphConfigChanged(graphConfig.copy(detailZoomThreshold = newThreshold))
                    },
                    sliderColors = sliderColors,
                )

                SliderRow(
                    label = "Node Size",
                    valueText = formatFloat(dotStyle.baseRadius, 1),
                    value = dotStyle.baseRadius,
                    valueRange = 2f..10f,
                    onValueChange = { newRadius ->
                        onDotStyleChanged(dotStyle.copy(baseRadius = newRadius))
                    },
                    sliderColors = sliderColors,
                )

                SliderRow(
                    label = "Link Thickness",
                    valueText = formatFloat(edgeConfig.width, 1),
                    value = edgeConfig.width,
                    valueRange = 0.5f..5f,
                    onValueChange = { newWidth ->
                        onEdgeConfigChanged(edgeConfig.copy(width = newWidth))
                    },
                    sliderColors = sliderColors,
                )

                PanelDivider()

                SectionHeader("Forces")

                SliderRow(
                    label = "Center Strength",
                    valueText = formatFloat(forceSimulationConfig.centerStrength, 3),
                    value = forceSimulationConfig.centerStrength,
                    valueRange = 0f..0.5f,
                    onValueChange = { newStrength ->
                        onForceSimulationConfigChanged(
                            forceSimulationConfig.copy(centerStrength = newStrength),
                        )
                    },
                    sliderColors = sliderColors,
                )

                SliderRow(
                    label = "Repel Force",
                    valueText = forceSimulationConfig.repelStrength.toInt().toString(),
                    value = forceSimulationConfig.repelStrength,
                    valueRange = 1000f..50000f,
                    onValueChange = { newStrength ->
                        onForceSimulationConfigChanged(
                            forceSimulationConfig.copy(repelStrength = newStrength),
                        )
                    },
                    sliderColors = sliderColors,
                )

                SliderRow(
                    label = "Link Strength",
                    valueText = formatFloat(forceSimulationConfig.linkStrength, 3),
                    value = forceSimulationConfig.linkStrength,
                    valueRange = 0f..0.2f,
                    onValueChange = { newStrength ->
                        onForceSimulationConfigChanged(
                            forceSimulationConfig.copy(linkStrength = newStrength),
                        )
                    },
                    sliderColors = sliderColors,
                )

                SliderRow(
                    label = "Link Distance",
                    valueText = forceSimulationConfig.linkDistance.toInt().toString(),
                    value = forceSimulationConfig.linkDistance,
                    valueRange = 50f..1000f,
                    onValueChange = { newDistance ->
                        onForceSimulationConfigChanged(
                            forceSimulationConfig.copy(linkDistance = newDistance),
                        )
                    },
                    sliderColors = sliderColors,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkboxColors: androidx.compose.material3.CheckboxColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = checkboxColors,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    sliderColors: androidx.compose.material3.SliderColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GroupRow(
    group: GraphGroup,
    onUpdate: (GraphGroup) -> Unit,
    onDelete: () -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(group.color)
                .border(1.dp, colors.onSurface.copy(alpha = 0.35f), CircleShape)
                .clickable { showColorPicker = !showColorPicker },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = group.label ?: group.id,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colors.onSurface.copy(alpha = 0.65f),
            ),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete group",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun formatFloat(value: Float, decimals: Int): String {
    var factor = 1f
    repeat(decimals) { factor *= 10f }
    val rounded = (value * factor).toInt() / factor
    return rounded.toString()
}
