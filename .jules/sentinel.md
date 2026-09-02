## 2026-03-31 - Non-finite Float Propagation in Compose Graph State
**Vulnerability:** Drag deltas or viewport scale/offset calculations containing `NaN` or `Infinity` propagate into `GraphState` and Compose `Animatable`s, freezing the UI animation loop or corrupting content bounds.
**Learning:** Math operations like `Math.min(Float.MAX_VALUE, Float.NaN)` return `Float.NaN`, bypassing `Rect.isEmpty` checks and causing silent state corruption in UI trees.
**Prevention:** Always validate floating point inputs and deltas with `isFinite()` guards before performing state updates or animations.

## 2026-03-31 - Unsafe Non-Null Assertions and NaN Propagation in Graph Layout Engine
**Vulnerability:** `ForceDirectedLayout` used non-null assertions (`!!`) on positions and displacements during repulsion and spring attraction iterations. Dangling edges referencing missing node IDs caused `NullPointerException` crashes, and zero-distance nodes produced `NaN` displacements that corrupted all layout coordinates.
**Learning:** Graph layout algorithms processing external/dynamic graph edge inputs must never assume all edge endpoints exist in the node set or that distances are non-zero/finite.
**Prevention:** Always use safe null checks (`?: continue`) and `.isFinite()` guards on distance and displacement vectors before updating node layout positions.

## 2026-03-31 - Unbounded Scale and Non-Finite Floating Point Operations in Image Export
**Vulnerability:** `GraphExport.buildModel` and `GraphExportModel.toPngBytes` accepted non-finite (`NaN`, `Infinity`), negative, or arbitrarily large `scale`, `nodeRadius`, and `padding` parameters, which could cause massive bitmap allocations (`BufferedImage`), leading to JVM `OutOfMemoryError` DoS crashes or invalid graphics transform states.
**Learning:** Multiplying canvas width/height by unchecked float scale factors when instantiating raw Bitmaps/BufferedImages can overflow integer bounds or trigger multi-gigabyte memory allocations.
**Prevention:** Always validate scale and dimension parameters with `.isFinite() && > 0f` guards and coerce bitmap pixel dimensions to safe maximum limits (e.g., 8192px).

## 2026-03-31 - Infinite Loops and Memory Exhaustion in Canvas Grid Generation
**Vulnerability:** `GraphBackground` calculated `scaledGridSize = gridSize * state.scale` without validation. If `gridSize` or `scale` was 0, negative, `NaN`, or near-zero, `while (x < canvasWidth + scaledGridSize)` resulted in infinite loops (`x += 0`) that froze the UI composition thread or generated millions of `Offset` points, causing CPU/memory exhaustion DoS.
**Learning:** Loop step increments calculated from dynamic UI state or user config parameters must be validated to be strictly positive and bounded by a minimum threshold to guarantee finite forward progress.
**Prevention:** Validate step parameters with `.isFinite() && > 0f` guards and coerce calculated step increments with `maxOf(minStepThreshold, step)` before using them in iteration loops.

## 2026-03-31 - Positive Float.MIN_VALUE Corrupting Bounding Box Calculations for Negative Coordinates
**Vulnerability:** Initializing upper bound tracking variables (`maxX`, `maxY`) to `Float.MIN_VALUE` caused bounding box calculations (`getContentBounds`, `computeFitBounds`, group zones, minimap) to incorrectly expand `maxX` and `maxY` to `~0f` when all node coordinates were negative.
**Learning:** In Kotlin and Java, `Float.MIN_VALUE` is `1.4E-45f` (the smallest positive non-zero float value), NOT `-Float.MAX_VALUE` or negative infinity.
**Prevention:** Always initialize maximum bounding box search accumulators (`maxX`, `maxY`) to `-Float.MAX_VALUE` or `Float.NEGATIVE_INFINITY` when calculating coordinate bounds.
