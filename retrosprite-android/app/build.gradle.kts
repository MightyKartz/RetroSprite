plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.retrosprite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.retrosprite.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            // Current target devices are RG 476H and Apple Silicon AVD, both arm64-v8a.
            // Keep sherpa-onnx/onnxruntime APK size modest instead of packaging every ABI.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
            assets.srcDirs("$projectDir/schemas")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Make Android framework stubs return harmless defaults (0 / null / false)
        // instead of throwing `RuntimeException("Method ... not mocked.")` when a
        // JVM unit test transitively touches an Android API (e.g. android.util.Log).
        // Required for our Ktor + RequestLogger tests that go through retroArchModule.
        unitTests.isReturnDefaultValues = true
    }
}

// -----------------------------------------------------------------------------
// JaCoCo coverage — OPTIONAL.
//
// The block below registers a `jacocoTestReport` task that aggregates the HTML
// + XML coverage report for the debug unit tests. It is intentionally NOT
// wired into the default `check` lifecycle, so existing `./gradlew testDebugUnitTest`
// runs are unaffected.
//
// Usage (once enabled):
//   ./gradlew testDebugUnitTest jacocoTestReport
//   open app/build/reports/jacoco/jacocoTestReport/html/index.html
//
// To activate, uncomment the `plugins { jacoco }` line below and run
// `./gradlew tasks --group verification` to confirm the task is registered.
// -----------------------------------------------------------------------------
// apply(plugin = "jacoco")
//
// tasks.register<JacocoReport>("jacocoTestReport") {
//     group = "verification"
//     description = "Generates JaCoCo coverage report for the debug unit tests."
//     dependsOn("testDebugUnitTest")
//
//     reports {
//         xml.required.set(true)
//         html.required.set(true)
//     }
//
//     val fileFilter = listOf(
//         "**/R.class", "**/R$*.class",
//         "**/BuildConfig.*", "**/Manifest*.*",
//         "**/*Test*.*", "android/**/*.*",
//         // Generated Room / Compose code is not meaningful to cover.
//         "**/*_Impl*.*", "**/*_Factory*.*", "**/*ComposableSingletons*.*"
//     )
//     val mainSrc = "$projectDir/src/main/kotlin"
//     val kotlinTree = fileTree("$buildDir/tmp/kotlin-classes/debug") { exclude(fileFilter) }
//
//     sourceDirectories.setFrom(files(mainSrc))
//     classDirectories.setFrom(files(kotlinTree))
//     executionData.setFrom(
//         fileTree(buildDir) {
//             include("jacoco/testDebugUnitTest.exec")
//         }
//     )
// }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose BOM + UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Ktor Server (local HTTP endpoint)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor Client (LLM)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // OkHttp / Retrofit
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Local offline ASR (sherpa-onnx JNI + Android ABIs)
    implementation(libs.sherpa.onnx.android) {
        isTransitive = false
    }
    implementation(libs.sherpa.onnx.runtime) {
        isTransitive = false
    }

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.server.content.negotiation)

    // Android Instrumented Testing
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
