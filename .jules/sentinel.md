## 2026-03-31 - Non-finite Float Propagation in Compose Graph State
**Vulnerability:** Drag deltas or viewport scale/offset calculations containing `NaN` or `Infinity` propagate into `GraphState` and Compose `Animatable`s, freezing the UI animation loop or corrupting content bounds.
**Learning:** Math operations like `Math.min(Float.MAX_VALUE, Float.NaN)` return `Float.NaN`, bypassing `Rect.isEmpty` checks and causing silent state corruption in UI trees.
**Prevention:** Always validate floating point inputs and deltas with `isFinite()` guards before performing state updates or animations.
