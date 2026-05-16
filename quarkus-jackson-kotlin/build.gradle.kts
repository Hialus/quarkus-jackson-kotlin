plugins {
    `java-library`
    id("io.quarkus.extension")
    kotlin("jvm")
}

val quarkusVersion: String by rootProject.extra

quarkusExtension {
    setDisableValidation(true)
}

dependencies {
    api(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    api("io.quarkus:quarkus-core")
}
