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

/**
 * Specifies how nodes should be rendered in the graph.
 *
 * @property COMPOSABLE Full Composable rendering for each node, supporting rich content (labels, icons, custom layouts).
 *                      Suitable for graphs with dozens to hundreds of nodes. Each node is a real layout node
 *                      with its own pointer input and measure pass.
 * @property DOT       Lightweight canvas-drawn dots, sized by node degree. Suitable for large graphs (hundreds
 *                      to thousands of nodes). All dots drawn in a single Canvas pass with shared hit-testing.
 *                      Position interpreted as the dot's center; no size measurement.
 */
enum class NodeRenderMode {
    COMPOSABLE,
    DOT,
}
