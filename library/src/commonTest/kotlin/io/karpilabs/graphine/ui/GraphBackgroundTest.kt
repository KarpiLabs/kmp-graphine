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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphBackgroundTest {

    @Test
    fun testComputeGridPointsValidInputs() {
        val points = computeGridPoints(
            gridSize = 60f,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertTrue(points.isNotEmpty())
    }

    @Test
    fun testComputeGridPointsHandlesNonFiniteGridSizeOrScale() {
        val nanGridPoints = computeGridPoints(
            gridSize = Float.NaN,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertEquals(emptyList(), nanGridPoints)

        val infScalePoints = computeGridPoints(
            gridSize = 60f,
            scale = Float.POSITIVE_INFINITY,
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertEquals(emptyList(), infScalePoints)
    }

    @Test
    fun testComputeGridPointsHandlesZeroOrNegativeGridSize() {
        val zeroGridPoints = computeGridPoints(
            gridSize = 0f,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertEquals(emptyList(), zeroGridPoints)

        val negativeGridPoints = computeGridPoints(
            gridSize = -20f,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertEquals(emptyList(), negativeGridPoints)
    }

    @Test
    fun testComputeGridPointsCoercesNearZeroScaledGridSize() {
        val tinyScalePoints = computeGridPoints(
            gridSize = 60f,
            scale = 0.000001f, // raw scaled = 0.00006f, coerced to 10f
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertTrue(tinyScalePoints.isNotEmpty())
        // Should produce finite number of points without hanging in loop
        assertTrue(tinyScalePoints.size < 500)
    }

    @Test
    fun testComputeGridPointsHandlesNonFiniteOffsetsOrCanvasSize() {
        val nanOffsetPoints = computeGridPoints(
            gridSize = 60f,
            scale = 1f,
            offsetX = Float.NaN,
            offsetY = 0f,
            canvasWidth = 100f,
            canvasHeight = 100f,
        )
        assertEquals(emptyList(), nanOffsetPoints)

        val nanCanvasPoints = computeGridPoints(
            gridSize = 60f,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            canvasWidth = Float.NaN,
            canvasHeight = 100f,
        )
        assertEquals(emptyList(), nanCanvasPoints)
    }
}
