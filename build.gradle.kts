plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}

// Si el proyecto vive en un disco de red (NAS), compilar ahí falla al sobreescribir .class.
// En local.properties se puede poner `marco.buildRoot=C:/ruta/local` para compilar en disco local.
val localProps = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
localProps.getProperty("marco.buildRoot")?.let { root ->
    allprojects { layout.buildDirectory.set(file("$root/${project.name}")) }
}
