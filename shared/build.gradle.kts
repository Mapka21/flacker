import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("io.coil-kt.coil3:coil-compose:3.3.0")
            api("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
        }

        // Android y escritorio son JVM: el código compartido (que usa OkHttp, java.io, el SDK de
        // Anthropic…) vive en src/jvmShared y se compila dentro de cada uno de los dos targets.
        val jvmDeps: org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler.() -> Unit = {
            api("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("com.anthropic:anthropic-java:2.68.0")
        }
        androidMain {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies(jvmDeps)
            // Selector de imágenes del sistema (portadas propias).
            dependencies { implementation("androidx.activity:activity-compose:1.11.0") }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies(jvmDeps)
            dependencies { implementation(compose.desktop.common) }
        }
        val desktopTest by getting {
            dependencies { implementation("junit:junit:4.13.2") }
        }
    }
}

android {
    namespace = "com.marco.audiopro.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "com.marco.audiopro.resources"
    generateResClass = always
    publicResClass = true
}
