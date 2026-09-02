# Compose Multiplatform — tvOS fork

This is a fork of [JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform)
(the Compose Gradle plugin and `components-resources`) that adds Apple tvOS support.
The upstream project's own README is [here](https://github.com/JetBrains/compose-multiplatform#readme).

It is an unofficial community fork. It is not affiliated with or endorsed by JetBrains.

## Status / maintenance

I maintain only the tvOS port, and only as far as I need it for my own tvOS app. I do not track
every upstream release; I republish roughly once per Compose Multiplatform stable line. There are
no support commitments and no release schedule.

**PRs are welcome** — bug fixes, additional targets or modules, and help keeping the fork up to
date with upstream are all appreciated.

## How to use it

Do not depend on this repository directly. Apply the
[compose-tvos](https://github.com/sajidalidev/compose-tvos) Gradle settings plugin and keep your
stock `org.jetbrains.compose` plugin id and `org.jetbrains.compose.components` coordinates:

```kotlin
// settings.gradle.kts
plugins {
    id("dev.sajidali.compose-tvos") version "1.3.0"
}
```

The plugin does two things that involve this repository:

- `plugins { id("org.jetbrains.compose") }` in your build scripts is transparently substituted
  with this fork's `dev.sajidali.compose:compose-gradle-plugin`, so Compose Resources packaging
  works for tvOS with no plugin-id change on your side.
- `compose.components.resources` resolves its tvOS variant from
  `dev.sajidali.compose.components:components-resources`; other targets keep the official
  JetBrains artifact.

Both artifacts are on Maven Central under the `dev.sajidali.*` group prefix. Versions follow
upstream (the "same-version convention"); the current published line is **1.12.0**.

Full documentation: https://sajidalidev.github.io/compose-tvos/ — and [TVOS.md](TVOS.md) in this repo.

## What's in this fork

### Branches

| Branch | Contents |
|---|---|
| `tvos-main` | upstream + the tvOS commits (rebased onto upstream periodically) |
| `release-1.12-tvos` | upstream `v1.12.0` + the tvOS commits + 1.12.0 pins, redirect-plugin build wiring and the Central staging script; the 1.12.0 artifacts were built from here |
| `tvos-publishing` | the `dev.sajidali` publication overrides (group override, plugin-marker suppression, POM metadata) |

### What was changed

- `tvosArm64` / `tvosSimulatorArm64` targets added to `components-resources`
  (`components/resources/library/build.gradle.kts`). `tvosX64` is not built.
- The Compose Gradle plugin's iOS resource-sync tasks (`IosResourcesTasks.kt`) extended to the
  `appletvos` / `appletvossimulator` platforms, so resources sync into an Xcode tvOS build the same
  way they do for iOS.
- Upstream's `script` resource qualifier mirrored into the tvOS `ResourceEnvironment`.
- The `components` build applies the compose-tvos redirect plugin itself
  (`components/settings.gradle.kts`), so the resources library's tvOS targets resolve
  `dev.sajidali.compose.*` exactly like a consumer app does — no `org.jetbrains` shadow set is
  needed to build it.
- Publication under `dev.sajidali.*` via `-Ppublication.groupId=...`, with the `org.jetbrains.compose`
  plugin marker suppressed so it is never republished under the overridden group. Published
  module metadata still declares `org.jetbrains.compose.*` dependencies; the redirect plugin
  resolves those at consumer build time.

### Published artifacts (1.12.0)

- `dev.sajidali.compose.components:components-resources` — umbrella plus android, desktop,
  iosArm64, iosSimulatorArm64, macosArm64, js, wasmJs, tvosArm64 and tvosSimulatorArm64 modules.
- `dev.sajidali.compose:compose-gradle-plugin`.

## Building / publishing locally

Publish the core fork to mavenLocal first (see
[compose-multiplatform-core](https://github.com/sajidalidev/compose-multiplatform-core)),
then:

```bash
# components-resources, resolving the core fork from ~/.m2
./gradlew -p components -Pcompose.useMavenLocal=true \
    -Ppublication.groupId=dev.sajidali.compose.components :resources:library:publishToMavenLocal

# the Gradle plugin
./gradlew -p gradle-plugins -Ppublication.groupId=dev.sajidali.compose :compose:publishToMavenLocal
```

The redirect plugin fetches its version manifest from the
[compose-tvos](https://github.com/sajidalidev/compose-tvos/blob/main/manifest/compose-tvos-versions.json)
repository (`main` branch), so no local manifest is needed. `-Ptvos.redirect.verbose=true` logs what
the plugin resolves.

`scripts/stage-central-bundle.sh <version>` signs and stages a Maven Central Portal bundle from
`~/.m2`. It never uploads.

## Related repositories

- [sajidalidev/compose-tvos](https://github.com/sajidalidev/compose-tvos) — the settings plugin,
  version manifest and docs. Start here.
- [sajidalidev/compose-multiplatform-core](https://github.com/sajidalidev/compose-multiplatform-core)
  — fork of the Compose runtime/ui/foundation/material3/navigation/lifecycle sources with the
  actual tvOS rendering, focus and Siri Remote work.
- [sajidalidev/koin](https://github.com/sajidalidev/koin) — Koin with tvOS targets.
- [sajidalidev/coil](https://github.com/sajidalidev/coil) — Coil 3 with tvOS targets.
- [sajidalidev/jetstream-tvos](https://github.com/sajidalidev/jetstream-tvos) — sample app
  (Google's JetStream) running on Apple TV.

## License

Same as upstream: [Apache License 2.0](LICENSE.txt). Copyright for the upstream code remains with
JetBrains s.r.o. and the original contributors.
