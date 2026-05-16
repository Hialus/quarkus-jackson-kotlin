import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") apply false
}

extra["quarkusVersion"] = providers.gradleProperty("quarkusVersion").get()

allprojects {
    group = "com.github.Hialus.quarkus-jackson-kotlin"
    version = providers.gradleProperty("version").get()

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set(project.provider { project.name })
                    description.set(
                        "Auto-registers Kotlin data classes, enums, and Jackson-annotated " +
                            "classes for native-image reflection."
                    )
                    url.set("https://github.com/Hialus/quarkus-jackson-kotlin")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("hialus")
                            name.set("Phoebe Morrien")
                        }
                    }
                    scm {
                        url.set("https://github.com/Hialus/quarkus-jackson-kotlin")
                        connection.set("scm:git:https://github.com/Hialus/quarkus-jackson-kotlin.git")
                        developerConnection.set("scm:git:ssh://git@github.com/Hialus/quarkus-jackson-kotlin.git")
                    }
                }
            }
        }

    }
}
