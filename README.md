# KmpGraphine

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/io.karpilabs/graphine.svg)](https://repo1.maven.org/maven2/io/karpilabs/graphine)
[![Build](https://img.shields.io/badge/build-gradle-green.svg)](https://gradle.org)

A modern graph and hierarchy visualization library for **Kotlin Multiplatform** and **Jetpack Compose**.

Inspired by tools like **Obsidian Canvas** and **Visual Capitalist**, KmpGraphine offers an interactive way to explore relational data with a polished canvas feel.

![KmpGraphine Overview](https://raw.githubusercontent.com/karpilabs/graphine/main/docs/overview.png)
*Modern dot grid background with glassmorphism nodes and path highlighting.*

## Core Features

- Infinite canvas: smooth panning and pinch to zoom from 0.1x to 5.0x.
- Obsidian inspired style: subtle dot grid background, gradient edges, and dashed group zones.
- Built in intelligence:
  - Semantic zoom: labels and metadata appear or fade based on scale.
  - Path highlighting: trace ownership chains with focused dimming.
- Layout engines:
  - Tree layout: hierarchical branching with grid based clustering for dense node sets.
  - Force directed layout: organic, physics based positioning.
- UI components: built in `Minimap`, `GraphControls`, and `ZoomControls`.

## Usage

### 1. Define your data
KmpGraphine works with any data model using the generic `GraphNode<T>`.

```kotlin
val nodes = listOf(
    GraphNode(id = "1", data = "Parent"),
    GraphNode(id = "2", data = "Child A"),
    GraphNode(id = "3", data = "Child B")
)
val edges = listOf(GraphEdge(from = "1", to = "2"), GraphEdge(from = "1", to = "3"))
```

### 2. State and surface
Initialize the state and place `GraphSurface` in your UI.

```kotlin
val state = rememberGraphState(nodes, edges)

GraphSurface(
    state = state,
    edgeConfig = EdgeConfig(showArrowheads = true),
    nodeContent = { node, isDetailVisible ->
        // Use any Composable as your node
        MyNodeCard(node.data, showText = isDetailVisible)
    }
)
```

### 3. Viewport controls
Add the floating toolbar and minimap for easier navigation.

```kotlin
Box(Modifier.fillMaxSize()) {
    GraphSurface(state = state, ...)

    Minimap(state = state, modifier = Modifier.align(Alignment.TopStart))

    GraphControls(state = state, modifier = Modifier.align(Alignment.BottomEnd))
}
```

## Real world example: Investigo
In the **Investigo** app, KmpGraphine is used to visualize large corporate ownership trees.

<img src="docs/investigo_example.png" alt="Investigo Ownership Graph" width="50%" />

*Example: Visualizing a corporate ownership tree in Investigo.*

## Publishing

KmpGraphine is configured for publishing to Maven Central (OSSRH) and local repositories.

### Local Installation
To test the library in another project locally:
```bash
./gradlew publishAllPublicationsToMavenLocal
```

### Maven Central (OSSRH)
1. Add your credentials to `gradle.properties`:
   - `ossrhUsername`: Your Sonatype Jira username.
   - `ossrhPassword`: Your Sonatype Jira password.
   - `signing.keyId`: Your GPG key ID (last 8 characters).
   - `signing.password`: Your GPG key passphrase.
   - `signing.secretKeyRingFile`: Path to your exported secret key file (`.gpg` or `.kbx`).

2. Run the deployment task:
```bash
./gradlew publishAllPublicationsToOSSRHRepository
```

## Library roadmap
- Edge routing to avoid node overlap
- Box and lasso selection for bulk operations
- Node connector ports for drag to link
- SVG and PNG export
