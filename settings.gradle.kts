pluginManagement {
    val kotlinVersion: String by settings
    val quarkusVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        id("io.quarkus.extension") version quarkusVersion
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "quarkus-jackson-kotlin-parent"

include("quarkus-jackson-kotlin")
include("quarkus-jackson-kotlin-deployment")
