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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.karpilabs.graphine.model.NodePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeRoutingTest {
    @Test
    fun testClipToRectBoundaryStopsAtEdge() {
        val rect = Rect(0f, 0f, 100f, 60f) // center at (50, 30)
        val center = rect.center
        val target = Offset(500f, 30f) // straight right

        val clipped = clipToRectBoundary(center, target, rect)

        assertEquals(100f, clipped.x)
        assertEquals(30f, clipped.y)
    }

    @Test
    fun testClipToRectBoundaryNoOpForZeroSizeRect() {
        val rect = Rect(50f, 50f, 50f, 50f)
        val center = Offset(50f, 50f)
        val target = Offset(500f, 500f)

        assertEquals(center, clipToRectBoundary(center, target, rect))
    }

    @Test
    fun testPortAnchorSides() {
        val rect = Rect(0f, 0f, 100f, 60f)

        assertEquals(Offset(50f, 0f), portAnchor(rect, NodePort.TOP))
        assertEquals(Offset(50f, 60f), portAnchor(rect, NodePort.BOTTOM))
        assertEquals(Offset(0f, 30f), portAnchor(rect, NodePort.LEFT))
        assertEquals(Offset(100f, 30f), portAnchor(rect, NodePort.RIGHT))
    }

    @Test
    fun testFindObstacleBowReturnsNullWhenClear() {
        val from = Offset(0f, 0f)
        val to = Offset(100f, 0f)
        // Obstacle far away from the segment.
        val nodeRects = mapOf("obstacle" to Rect(0f, 500f, 20f, 520f))

        assertNull(findObstacleBow(from, to, "a", "b", nodeRects))
    }

    @Test
    fun testFindObstacleBowDetectsBlockingNode() {
        val from = Offset(0f, 0f)
        val to = Offset(100f, 0f)
        // Obstacle directly on the straight path between from and to.
        val nodeRects = mapOf("obstacle" to Rect(40f, -10f, 60f, 10f))

        val bow = findObstacleBow(from, to, "a", "b", nodeRects)

        assertNotNull(bow)
        // The bow should push perpendicular to the from->to line (i.e. off the y=0 axis).
        assertTrue(kotlin.math.abs(bow.y) > 0f)
    }

    @Test
    fun testFindObstacleBowIgnoresEndpointNodes() {
        val from = Offset(0f, 0f)
        val to = Offset(100f, 0f)
        // "a" and "b" themselves overlap the line but must be excluded as endpoints.
        val nodeRects = mapOf(
            "a" to Rect(-10f, -10f, 10f, 10f),
            "b" to Rect(90f, -10f, 110f, 10f),
        )

        assertNull(findObstacleBow(from, to, "a", "b", nodeRects))
    }

    @Test
    fun testClipToRectBoundaryHandlesNonFiniteValues() {
        val validCenter = Offset(50f, 30f)
        val validTarget = Offset(500f, 30f)
        val validRect = Rect(0f, 0f, 100f, 60f)

        // Non-finite center should return Offset.Zero
        val nanCenterResult = clipToRectBoundary(Offset(Float.NaN, 30f), validTarget, validRect)
        assertEquals(Offset.Zero, nanCenterResult)

        // Non-finite target should return center unchanged
        val nanTargetResult = clipToRectBoundary(validCenter, Offset(500f, Float.NaN), validRect)
        assertEquals(validCenter, nanTargetResult)

        // Non-finite rect should return center unchanged
        val nanRect = Rect(0f, 0f, Float.NaN, 60f)
        val nanRectResult = clipToRectBoundary(validCenter, validTarget, nanRect)
        assertEquals(validCenter, nanRectResult)

        // Infinity rect should return center unchanged
        val infRect = Rect(0f, 0f, Float.POSITIVE_INFINITY, 60f)
        val infRectResult = clipToRectBoundary(validCenter, validTarget, infRect)
        assertEquals(validCenter, infRectResult)
    }

    @Test
    fun testPortAnchorHandlesNonFiniteRect() {
        val nanRect = Rect(0f, 0f, Float.NaN, 60f)
        assertEquals(Offset.Zero, portAnchor(nanRect, NodePort.TOP))
    }

    @Test
    fun testFindObstacleBowHandlesNonFiniteValues() {
        val validFrom = Offset(0f, 0f)
        val validTo = Offset(100f, 0f)
        val validRects = mapOf("obstacle" to Rect(40f, -10f, 60f, 10f))

        // Non-finite 'from' offset
        assertNull(findObstacleBow(Offset(Float.NaN, 0f), validTo, "a", "b", validRects))

        // Non-finite 'to' offset
        assertNull(findObstacleBow(validFrom, Offset(100f, Float.POSITIVE_INFINITY), "a", "b", validRects))

        // Non-finite rect bounds in obstacle map
        val nanRects = mapOf("obstacle" to Rect(40f, -10f, Float.NaN, 10f))
        assertNull(findObstacleBow(validFrom, validTo, "a", "b", nanRects))

        // Zero-length path
        assertNull(findObstacleBow(validFrom, validFrom, "a", "b", validRects))
    }
}
