plugins {
    `java-library`
    id("io.quarkus.extension")
    kotlin("jvm")
}

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

quarkusExtension {
    setDisableValidation(true)
}

dependencies {
    api(platform("io.quarkus:quarkus-bom:$quarkusVersion"))
    api("io.quarkus:quarkus-core")
}
