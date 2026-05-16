plugins {
    kotlin("jvm")
    kotlin("kapt")
}

val quarkusVersion: String by rootProject.extra

dependencies {
    api(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))

    api(project(":quarkus-jackson-kotlin"))
    api("io.quarkus:quarkus-core-deployment")
    api("io.quarkus:quarkus-arc-deployment")

    kapt("io.quarkus:quarkus-extension-processor:$quarkusVersion")
}
