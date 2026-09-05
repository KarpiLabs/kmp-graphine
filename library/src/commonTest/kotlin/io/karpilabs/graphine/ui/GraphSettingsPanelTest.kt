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

class GraphSettingsPanelTest {

    @Test
    fun formatFloat_handlesNormalValues() {
        assertEquals("1.23", formatFloat(1.234f, 2))
        assertEquals("0.5", formatFloat(0.5f, 1))
        assertEquals("400", formatFloat(400f, 0))
    }

    @Test
    fun formatFloat_handlesNonFiniteValues() {
        assertEquals("0", formatFloat(Float.NaN, 2))
        assertEquals("0", formatFloat(Float.POSITIVE_INFINITY, 2))
        assertEquals("0", formatFloat(Float.NEGATIVE_INFINITY, 2))
    }

    @Test
    fun formatFloat_handlesExtremeValues() {
        assertEquals("21474836", formatFloat(1e12f, 2))
        assertEquals("-21474836", formatFloat(-1e12f, 2))
    }
}
