import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

val bytedeco = "1.5.14"
val ffmpegVersion = "8.1.2-$bytedeco"
val nativePlatform = "windows-x86_64"

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")

    // ffmpeg para decodificar todo (FLAC, ALAC, Opus, MP3, AAC, DSD…). Sólo los nativos de Windows x64.
    implementation("org.bytedeco:javacv:$bytedeco") { isTransitive = false }
    implementation("org.bytedeco:javacpp:$bytedeco")
    implementation("org.bytedeco:javacpp:$bytedeco:$nativePlatform")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion:$nativePlatform")

    // Barra de tareas (botones ⏮ ⏯ ⏭) y controles multimedia de Windows.
    implementation("net.java.dev.jna:jna:5.15.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.5")
    testImplementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.5")
}

compose.desktop {
    application {
        mainClass = "com.marco.audiopro.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dsun.java2d.d3d=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Flacker"
            packageVersion = "2.4.0"
            description = "Flacker: tu música de Navidrome en alta fidelidad"
            vendor = "Marco"
            includeAllModules = true
            windows {
                menu = true
                shortcut = true
                menuGroup = "Flacker"
                upgradeUuid = "6d0e2a9c-6c0f-4d6b-9b8e-2f5a1c7e9a31"
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}

tasks.withType<Test> {
    listOf("MARCO_TEST_SERVER", "MARCO_SHOTS_DIR").forEach { k -> System.getenv(k)?.let { environment(k, it) } }
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// ./gradlew :desktop:run -Pmarco.profile=dev → ajustes separados de los reales.
afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        listOf("marco.profile", "marco.startMini").forEach { k ->
            (project.findProperty(k) as String?)?.let { systemProperty(k, it) }
        }
    }
}
