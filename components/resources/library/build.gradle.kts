import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
    id("com.android.library")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
    jvm("desktop")
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    js {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilations.getByName("test").compileTaskProvider.configure {
            // https://youtrack.jetbrains.com/issue/KT-69014
            compilerOptions.freeCompilerArgs.add("-Xwasm-enable-array-range-checks")
        }
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        binaries.executable()
    }
    macosArm64()

    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("org.jetbrains.compose.resources.InternalResourceApi")
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        //          common
        //       ┌────┴────┐
        //    skiko       blocking
        //      │      ┌─────┴────────┐
        //  ┌───┴───┬──│────────┐     │
        //  │      native       │ jvmAndAndroid
        //  │    ┌───┴───┐      │   ┌───┴───┐
        // web   ios    macos   desktop    android

        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.kotlinx.coroutines.core)
                // tvOS fork: the tvOS variants of org.jetbrains.compose.* are injected at
                // resolution time by the compose-tvos redirect plugin (see settings.gradle.kts),
                // but Kotlin's granular metadata transformation only trusts a library's static
                // project-structure metadata. Transitive compose modules therefore get demoted
                // for the shared source sets unless they are declared DIRECTLY here, so list
                // every compose module this library's commonMain code actually touches.
                val composeVersion = libs.versions.compose.get()
                implementation(libs.compose.ui)
                implementation("org.jetbrains.compose.ui:ui-graphics:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-text:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-unit:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-util:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui-geometry:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation-layout:$composeVersion")
                implementation("org.jetbrains.compose.animation:animation:$composeVersion")
                implementation("org.jetbrains.compose.animation:animation-core:$composeVersion")
                implementation("org.jetbrains.compose.runtime:runtime-saveable:$composeVersion")
            }
        }
//        val commonTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//                implementation(libs.kotlinx.coroutines.test)
//                implementation(libs.compose.material3)
//                implementation(libs.compose.ui.test)
//            }
//        }
        val blockingMain by creating {
            dependsOn(commonMain)
        }
//        val blockingTest by creating {
//            dependsOn(commonTest)
//        }
        val skikoMain by creating {
            dependsOn(commonMain)
        }
//        val skikoTest by creating {
//            dependsOn(commonTest)
//        }
        val jvmAndAndroidMain by creating {
            dependsOn(blockingMain)
        }
//        val jvmAndAndroidTest by creating {
//            dependsOn(blockingTest)
//        }
        val desktopMain by getting {
            dependsOn(skikoMain)
            dependsOn(jvmAndAndroidMain)
        }
//        val desktopTest by getting {
//            dependsOn(skikoTest)
//            dependsOn(jvmAndAndroidTest)
//            dependencies {
//                implementation(compose.desktop.currentOs)
//            }
//        }
        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)
            dependencies {
                //it will be called only in android instrumented tests where the library should be available
                compileOnly(libs.androidx.test.monitor)
            }
        }
//        val androidInstrumentedTest by getting {
//            dependsOn(jvmAndAndroidTest)
//            dependencies {
//                implementation(libs.androidx.test.core)
//                implementation(libs.androidx.compose.ui.test)
//                implementation(libs.androidx.compose.ui.test.manifest)
//                implementation(libs.androidx.compose.ui.test.junit4)
//            }
//        }
//        val androidUnitTest by getting {
//            dependsOn(jvmAndAndroidTest)
//        }
        val nativeMain by getting {
            dependsOn(skikoMain)
            dependsOn(blockingMain)
        }
//        val nativeTest by getting {
//            dependsOn(skikoTest)
//            dependsOn(blockingTest)
//        }
        val webMain by getting {
            dependsOn(skikoMain)
            dependencies {
                 implementation(libs.kotlinx.browser)
            }
        }
        val jsMain by getting {
            dependsOn(webMain)
        }
        val wasmJsMain by getting {
            dependsOn(webMain)
        }
//        val webTest by getting {
//            dependsOn(skikoTest)
//        }
//        val jsTest by getting {
//            dependsOn(webTest)
//        }
//        val wasmJsTest by getting {
//            dependsOn(webTest)
//        }
    }
}

android {
    compileSdk = 35
    namespace = "org.jetbrains.compose.components.resources"
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel5").apply {
                    device = "Pixel 5"
                    apiLevel = 31
                    systemImageSource = "aosp"
                }
            }
        }
    }
    sourceSets {
        val commonTestResources = "src/commonTest/resources"
        named("androidTest") {
            resources.srcDir(commonTestResources)
            assets.srcDir("src/androidInstrumentedTest/assets")
        }
        named("test") { resources.srcDir(commonTestResources) }
        named("main") { manifest.srcFile("src/androidMain/AndroidManifest.xml") }
    }
}

// Version decision: this module compiles/links against the official org.jetbrains.compose.*
// artifacts at compose.version (1.12.0, from Maven Central) and is published at deploy.version
// (1.12.0), so the POM/module metadata declares the org.jetbrains.compose.* 1.12.0 dependency
// verbatim (never rewritten to dev.sajidali). Consumers get the tvOS variants through the
// compose-tvos redirect settings plugin. A build-time dependency-substitution path
// (org.jetbrains.compose.{ui,foundation,runtime,...} -> dev.sajidali equivalents, gated behind
// -Ptvos.buildAgainstFork=true) was prototyped for 1.12.0-beta01 and abandoned: the fork core's
// mavenLocal publish only republishes the umbrella + new tvosArm64/tvosSimulatorArm64 platform
// artifacts, and redirecting the root module broke Kotlin's hierarchical common-metadata
// compilation (which needs a consistent variant set across every target sharing a source set).
// See task-9a-report.md in compose-tvos-redirect for the full investigation.
configureMavenPublication(
    groupId = (findProperty("publication.groupId") as String?) ?: "org.jetbrains.compose.components",
    artifactId = "components-resources",
    name = "Resources for Compose JB",
    description = "Compose Multiplatform resources library with tvOS support (dev.sajidali fork)"
)

apiValidation {
    @OptIn(ExperimentalBCVApi::class)
    klib { enabled = true }
    nonPublicMarkers.add("org.jetbrains.compose.resources.InternalResourceApi")
}

//utility task to generate CLDRPluralRuleLists.kt file by 'CLDRPluralRules/plurals.xml'
tasks.register<GeneratePluralRuleListsTask>("generatePluralRuleLists") {
    val projectDir = project.layout.projectDirectory
    pluralsFile = projectDir.file("CLDRPluralRules/plurals.xml")
    outputFile = projectDir.file("src/commonMain/kotlin/org/jetbrains/compose/resources/plural/CLDRPluralRuleLists.kt")
    samplesOutputFile = projectDir.file("src/commonTest/kotlin/org/jetbrains/compose/resources/CLDRPluralRuleLists.test.kt")
}

tasks {
    val desktopTestProcessResources =
        named<ProcessResources>("desktopTestProcessResources")

    withType<Test> {
        dependsOn(desktopTestProcessResources)
        environment("RESOURCES_PATH", desktopTestProcessResources.map { it.destinationDir.absolutePath }.get())
    }
}