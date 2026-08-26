pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        if (extra["compose.useMavenLocal"] == "true") {
            mavenLocal()
        }
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String)
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        id("org.jetbrains.kotlin.plugin.compose").version(extra["kotlin.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
        id("com.android.library").version(extra["agp.version"] as String)
        id("com.android.kotlin.multiplatform.library").version(extra["agp.version"] as String)
        id("org.jetbrains.kotlinx.binary-compatibility-validator").version("0.17.0")
    }

    val gradlePluginDir = rootDir.resolve("../gradle-plugins")
    if (gradlePluginDir.exists()) {
        includeBuild(gradlePluginDir)
    }
}


// tvOS fork: the resources library's tvosArm64/tvosSimulatorArm64 targets resolve
// org.jetbrains.compose.* through the compose-tvos redirect settings plugin, exactly like a
// consumer app does (the official artifacts have no tvOS variants; the fork republishes them as
// dev.sajidali.compose.* at the same version). Pair with -Pcompose.useMavenLocal=true so a locally
// published fork core is visible, and -Ptvos.redirect.manifestUrl=file:///... to test an unpublished
// version manifest. The included ../gradle-plugins build already provides the org.jetbrains.compose
// plugin, so the plugin-marker interception is disabled.
plugins { id("dev.sajidali.compose-tvos") version "1.3.0" }

composeTvos {
    interceptComposeGradlePlugin.set(false)
    verbose.set(extra["tvos.redirect.verbose"] == "true")
    (extra.properties["tvos.redirect.manifestUrl"] as? String)?.let { manifestUrl.set(it) }
}

dependencyResolutionManagement {
    repositories {
        if (extra["compose.useMavenLocal"] == "true") {
            mavenLocal() // mavenLocal should be the first to get the correct version of skiko during a local build.
        }
        google()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
    }

    versionCatalogs {
        create("libs") {
            version("compose", extra["compose.version"].toString())
        }
    }
}

include(":SplitPane:library")
include(":SplitPane:demo")
include(":AnimatedImage:library")
include(":AnimatedImage:demo")
include(":resources:library")
include(":resources:demo:androidApp")
include(":resources:demo:desktopApp")
include(":resources:demo:shared")
include(":ui-tooling-preview:library")
include(":ui-tooling-preview:demo:desktopApp")
include(":ui-tooling-preview:demo:shared")
