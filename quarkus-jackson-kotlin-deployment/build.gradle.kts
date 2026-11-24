plugins {
    kotlin("jvm")
    kotlin("kapt")
}

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

dependencies {
    api(platform("io.quarkus:quarkus-bom:$quarkusVersion"))

    api(project(":quarkus-jackson-kotlin"))
    api("io.quarkus:quarkus-core-deployment")
    api("io.quarkus:quarkus-arc-deployment")

    kapt("io.quarkus:quarkus-extension-processor:$quarkusVersion")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
