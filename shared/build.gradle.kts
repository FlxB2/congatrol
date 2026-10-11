import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Emit source-location info (file:line) in Kotlin/Native stack traces via
            // libbacktrace, so iOS crash reports are symbolicated instead of raw addresses.
            binaryOption("sourceInfoType", "libbacktrace")
        }
    }

    // Desktop JVM, for Compose Hot Reload: `./gradlew :shared:hotRunDesktop` opens the app in a
    // window and swaps in each composable edit without a rebuild-and-install. A development surface,
    // not a shipped platform.
    jvm("desktop")

    androidLibrary {
        namespace = "xyz.felixb.congatrol.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }

        // Enables JVM host unit tests (`androidHostTest`) so commonTest runs on the JVM.
        withHostTestBuilder {}
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Dispatchers.Main on the JVM; without it every viewModelScope launch throws.
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.haze)
            implementation(libs.haze.blur)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.collections.immutable)

            // The robot is spoken to over plain HTTP on the home network; CIO is the one engine
            // that runs on every target.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// --- Desktop (hot reload) -----------------------------------------------------------------------
compose.desktop {
    application {
        mainClass = "xyz.felixb.congatrol.MainKt"
    }
}

compose.resources {
    packageOfResClass = "congatrol.shared.generated.resources"
}
