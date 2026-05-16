package de.morrien.quarkus.jacksonkotlin.deployment

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem
import io.quarkus.runtime.annotations.RegisterForReflection
import org.jboss.jandex.ClassInfo
import org.jboss.jandex.DotName
import org.jboss.logging.Logger

/**
 * Registers Kotlin classes for native-image reflection so the Jackson Kotlin module can find
 * their primary constructors at runtime.
 *
 * The Jackson Kotlin module calls `KClass.getPrimaryConstructor()`, which walks `@kotlin.Metadata`.
 * GraalVM only preserves that annotation for classes registered with full reflection access, so
 * without this step a native image fails at first request with
 * `KotlinNamesAnnotationIntrospector.findDefaultCreator → KClass.getConstructors`.
 */
class JacksonKotlinProcessor {

    @BuildStep
    fun feature(): FeatureBuildItem = FeatureBuildItem(FEATURE)

    @BuildStep
    fun register(
        archives: ApplicationArchivesBuildItem,
        producer: BuildProducer<ReflectiveClassBuildItem>,
    ) {
        val candidates = archives.allApplicationArchives.asSequence()
            .flatMap { it.index.knownClasses.asSequence() }
            .filter { it.hasAnnotation(KOTLIN_METADATA) }
            .filter { !it.isSynthetic && !it.isInnerClass }
            .filter { !it.name().toString().contains("\$\$serializer") }
            .filter { !it.hasAnnotation(REGISTER_FOR_REFLECTION) }
            .filter { !it.hasAnnotation(JAKARTA_PATH) }
            .filter(::isJacksonCandidate)
            .distinctBy { it.name().toString() }
            .toList()

        if (candidates.isEmpty()) return

        for (klass in candidates) {
            producer.produce(
                ReflectiveClassBuildItem.builder(klass.name().toString())
                    .methods()
                    .fields()
                    .constructors()
                    .build()
            )
        }
        LOG.infof("Registered %d Kotlin class(es) for Jackson reflection", candidates.size)
    }

    private fun isJacksonCandidate(klass: ClassInfo): Boolean =
        klass.isEnumClass ||
            klass.isDataClass ||
            klass.hasAnyJacksonAnnotation() ||
            klass.hasAnnotation(KOTLINX_SERIALIZABLE)

    /**
     * `startsWith` instead of `==` because Kotlin mangles names of functions whose signature
     * mentions a `@JvmInline value class` — `component1` may show up as `component1-<hash>`.
     */
    private val ClassInfo.isDataClass: Boolean
        get() = methods().any { it.name().startsWith("component1") }

    private val ClassInfo.isEnumClass: Boolean
        get() = (flags().toInt() and ACC_ENUM) != 0

    private val ClassInfo.isInnerClass: Boolean
        get() = name().toString().contains('$')

    private val ClassInfo.isSynthetic: Boolean
        get() = (flags().toInt() and ACC_SYNTHETIC) != 0

    private fun ClassInfo.hasAnyJacksonAnnotation(): Boolean =
        annotations().any { it.name().toString().startsWith(JACKSON_ANNOTATION_PREFIX) }

    companion object {
        private val LOG: Logger = Logger.getLogger(JacksonKotlinProcessor::class.java)

        private const val FEATURE: String = "jackson-kotlin"
        private const val JACKSON_ANNOTATION_PREFIX: String = "com.fasterxml.jackson.annotation."

        private val KOTLIN_METADATA: DotName = DotName.createSimple("kotlin.Metadata")
        private val KOTLINX_SERIALIZABLE: DotName =
            DotName.createSimple("kotlinx.serialization.Serializable")
        private val REGISTER_FOR_REFLECTION: DotName =
            DotName.createSimple(RegisterForReflection::class.java.name)
        // String literal so this module doesn't need a compile dep on jakarta.ws.rs-api.
        private val JAKARTA_PATH: DotName = DotName.createSimple("jakarta.ws.rs.Path")

        private const val ACC_SYNTHETIC: Int = 0x1000
        private const val ACC_ENUM: Int = 0x4000
    }
}
