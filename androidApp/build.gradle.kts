import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.core)
}

android {
    namespace = "xyz.felixb.congatrol"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    compileSdkMinor =
        libs.versions.android.compileSdkMinor
            .get()
            .toInt()

    defaultConfig {
        applicationId = "xyz.felixb.congatrol"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            // Its own application id, so it installs next to the release build instead of over it
            // — with its own data.
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            // What the app really feels like: a debuggable build runs without ART's optimisations,
            // and Compose scrolls visibly worse for it.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signed with the debug key until there is a real one. Not for distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
