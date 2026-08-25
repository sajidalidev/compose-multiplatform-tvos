import kotlinx.validation.ExperimentalBCVApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
    jvm("desktop")
    android {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        namespace = "org.jetbrains.compose.components.resources"
        compileSdk = 37
        minSdk = 23

        androidResources.enable = true

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }

        withHostTest {
            isIncludeAndroidResources = true
        }

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            @Suppress("UnstableApiUsage")
            managedDevices.localDevices.create("pixel5") {
                device = "Pixel 5"
                apiLevel = 31
                systemImageSource = "aosp"
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
//        val androidDeviceTest by getting {
//            dependsOn(jvmAndAndroidTest)
//            dependencies {
//                implementation(libs.androidx.test.core)
//                implementation(libs.androidx.compose.ui.test)
//                implementation(libs.androidx.compose.ui.test.manifest)
//                implementation(libs.androidx.compose.ui.test.junit4)
//            }
//            resources.srcDir("src/commonTest/resources")
//        }
//        val androidHostTest by getting {
//            dependsOn(jvmAndAndroidTest)
//            resources.srcDir("src/commonTest/resources")
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
    }
}

// compose-tvos redirect settings plugin. A build-time dependency-substitution path
// (org.jetbrains.compose.{ui,foundation,runtime,...} -> dev.sajidali equivalents, gated behind
// -Ptvos.buildAgainstFork=true) was prototyped for 1.12.0-beta01 and abandoned: the fork core's
// mavenLocal publish only republishes the umbrella + new tvosArm64/tvosSimulatorArm64 platform
// artifacts, and redirecting the root module broke Kotlin's hierarchical common-metadata
// compilation (which needs a consistent variant set across every target sharing a source set).
// See task-9a-report.md in compose-tvos-redirect for the full investigation.
configureMavenPublication(
    groupId = "org.jetbrains.compose.components",
    artifactId = "components-resources",
    name = "Resources for Compose JB"
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
