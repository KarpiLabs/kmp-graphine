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
