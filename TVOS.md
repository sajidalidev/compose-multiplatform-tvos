# tvOS Support (Fork)

This is a fork of [JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform)
that adds Apple tvOS support to the Compose Gradle plugin and Compose Resources.

## What this fork adds

- **tvOS resource handling in the Compose Gradle plugin.** The existing iOS resource-sync task
  wiring (`IosResourcesTasks`) is extended to the `appletvos`/`appletvossimulator` platforms, so
  `compose.components.resources` resources sync into the Xcode build for tvOS the same way they
  already do for iOS.
- **tvOS Kotlin targets** (`tvosArm64`/`tvosSimulatorArm64`) added to `components-resources`.
- Published as:
  - `dev.sajidali.compose:compose-gradle-plugin`
  - `dev.sajidali.compose.components:components-resources`

## For consumers

You should never need to depend on this repository directly. Use the compose-tvos Gradle settings
plugin instead — it transparently intercepts and redirects the official
`org.jetbrains.compose`/`org.jetbrains.compose.components` artifacts (and the Compose Gradle
plugin itself) to this fork's tvOS builds, with no changes to your `dependencies {}` blocks:

```kotlin
// settings.gradle.kts
plugins {
    id("dev.sajidali.compose-tvos") version "1.1.0"
}
```

Canonical docs: **https://sajidalidev.github.io/compose-tvos/** (site is being built in parallel;
until it's live, see the [compose-tvos](https://github.com/sajidalidev/compose-tvos) repository).

## For contributors

- Work happens on the `tvos-publishing` branch, which carries the publishing-related commits on
  top of the tvOS feature work and is rebased onto upstream JetBrains changes periodically.
- Publishing overrides the Maven group with `-Ppublication.groupId=dev.sajidali...` (see
  `gradle-plugins/buildSrc/src/main/kotlin/BuildProperties.kt` and
  `components/buildSrc/src/main/kotlin/CommonMavenProperties.kt`), so the same build produces
  `dev.sajidali.*` coordinates instead of `org.jetbrains.compose.*` without touching the rest of
  the build logic.
- The rest of the tvOS ecosystem lives in:
  - [compose-multiplatform-core](https://github.com/sajidalidev/compose-multiplatform-core-tvos) —
    the Compose runtime/UI/foundation/material3/navigation/lifecycle fork with the actual tvOS
    rendering, focus-navigation, and Siri Remote input work.
  - [compose-tvos](https://github.com/sajidalidev/compose-tvos) — the settings plugin, version
    manifest, and canonical docs.
