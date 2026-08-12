# Sentinel's Journal

## 2026-03-05 - Search Component Denial of Service via Unbounded Input Length
**Vulnerability:** Unbounded input length in the `GraphSearch` component allowed users to input or paste arbitrarily long strings (e.g., megabytes of text), potentially causing CPU/memory exhaustion and freezing the main UI thread during substring matching.
**Learning:** Search inputs that match against graph nodes on-the-fly run on the UI/main thread. Without proper input constraints, heavy string operations (`String.contains`) on long strings can degrade application responsiveness and cause Denial of Service (DoS) in client-side applications.
**Prevention:** Always restrict the maximum character length for interactive search bars and sanitize search inputs before processing them against memory-bound data collections.
