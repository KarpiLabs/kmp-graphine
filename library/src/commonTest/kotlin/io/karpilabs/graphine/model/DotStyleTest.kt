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
import kotlin.test.Test
import kotlin.test.assertEquals

class DotStyleTest {
    @Test
    fun testRadiusComputationMinimum() {
        val style = DotStyle<String>(baseRadius = 4f, radiusPerDegree = 0.5f, maxRadius = 12f)

        // Degree 0: base only
        assertEquals(4f, style.computeRadius(0))
    }

    @Test
    fun testRadiusComputationLinear() {
        val style = DotStyle<String>(baseRadius = 4f, radiusPerDegree = 0.5f, maxRadius = 12f)

        // Degree 1: base + 0.5 = 4.5
        assertEquals(4.5f, style.computeRadius(1))

        // Degree 2: base + 1.0 = 5.0
        assertEquals(5f, style.computeRadius(2))

        // Degree 10: base + 5.0 = 9.0
        assertEquals(9f, style.computeRadius(10))
    }

    @Test
    fun testRadiusComputationCapped() {
        val style = DotStyle<String>(baseRadius = 4f, radiusPerDegree = 0.5f, maxRadius = 12f)

        // Degree 20: base + 10 = 14, capped to 12
        assertEquals(12f, style.computeRadius(20))

        // Degree 100: base + 50 = 54, capped to 12
        assertEquals(12f, style.computeRadius(100))
    }

    @Test
    fun testDefaultColorFunction() {
        val style = DotStyle<String>()
        val node = GraphNode("1", "Test")

        val color = style.color(node, 5)

        // Default should be neutral gray
        assertEquals(Color.Gray.copy(alpha = 0.6f), color)
    }

    @Test
    fun testCustomColorFunction() {
        val customColor = Color.Red
        val style = DotStyle<String>(
            color = { _, degree ->
                if (degree > 3) Color.Red else Color.Blue
            },
        )
        val node = GraphNode("1", "Test")

        assertEquals(Color.Red, style.color(node, 5))
        assertEquals(Color.Blue, style.color(node, 2))
    }
}
