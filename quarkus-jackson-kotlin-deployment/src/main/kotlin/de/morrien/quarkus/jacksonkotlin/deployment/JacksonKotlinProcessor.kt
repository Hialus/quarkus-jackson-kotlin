package de.morrien.quarkus.jacksonkotlin.deployment

import io.quarkus.bootstrap.classloading.QuarkusClassLoader
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
 *
 * Two more things fail the same way and are covered here:
 * - A nested class. kotlin-reflect resolves it through its enclosing classes, looked up by name, so
 *   those have to be reachable by name too. Registering the outer class does not register the nested
 *   one; left alone, Jackson finds no properties and writes `{}` without an error.
 * - A Kotlin standard library collection. `buildList { }` returns a `ListBuilder`, and Jackson's
 *   Kotlin module introspects it like any other Kotlin class.
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
            .let(::selectCandidates)
        if (candidates.isEmpty()) return

        for (klass in candidates) {
            producer.produce(fullAccess(klass.name().toString()))
        }
        // kotlin-reflect loads each enclosing class by name to find the nested one. The lookup needs
        // no member of the enclosing class, so those get a registration without members.
        val names = candidates.mapTo(HashSet()) { it.name().toString() }
        val enclosing = candidates.flatMap(::enclosingClassNames).distinct().filter { it !in names }
        if (enclosing.isNotEmpty()) {
            producer.produce(ReflectiveClassBuildItem.builder(*enclosing.toTypedArray()).constructors(false).build())
        }
        LOG.infof("Registered %d Kotlin class(es) for Jackson reflection", candidates.size)
    }

    @BuildStep
    fun registerStdlibCollections(producer: BuildProducer<ReflectiveClassBuildItem>) {
        val present = STDLIB_CLASSES.filter { QuarkusClassLoader.isClassPresentAtRuntime(it) }
        if (present.isEmpty()) return
        producer.produce(ReflectiveClassBuildItem.builder(*present.toTypedArray()).methods().fields().constructors().build())
    }

    private fun fullAccess(name: String): ReflectiveClassBuildItem =
        ReflectiveClassBuildItem.builder(name).methods().fields().constructors().build()

    /** The classes of an index that Jackson's Kotlin module is likely to read or write. */
    internal fun selectCandidates(classes: Sequence<ClassInfo>): List<ClassInfo> =
        classes
            .filter { it.hasAnnotation(KOTLIN_METADATA) }
            .filter { !it.isSynthetic && it.isNamedClass }
            .filter { !it.name().toString().contains("\$\$serializer") }
            .filter { !it.hasAnnotation(REGISTER_FOR_REFLECTION) }
            .filter { !it.hasAnnotation(JAKARTA_PATH) }
            .filter(::isJacksonCandidate)
            .distinctBy { it.name().toString() }
            .toList()

    /** The names of the classes around a nested class, innermost first; empty for a top-level class. */
    internal fun enclosingClassNames(klass: ClassInfo): List<String> =
        generateSequence(klass.name().toString()) { name -> name.substringBeforeLast('$', "").ifEmpty { null } }
            .drop(1)
            .toList()

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

    /** False for a local or an anonymous class, which nobody can name as a serialization type. */
    private val ClassInfo.isNamedClass: Boolean
        get() = nestingType() == ClassInfo.NestingType.TOP_LEVEL || nestingType() == ClassInfo.NestingType.INNER

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

        /**
         * The collection and tuple classes of the Kotlin standard library that end up in a serialized
         * value. A name that a Kotlin version does not have is skipped.
         */
        private val STDLIB_CLASSES: List<String> = listOf(
            "kotlin.Pair",
            "kotlin.Triple",
            "kotlin.collections.AbstractList",
            "kotlin.collections.AbstractList\$SubList",
            "kotlin.collections.ArrayDeque",
            "kotlin.collections.EmptyList",
            "kotlin.collections.EmptyMap",
            "kotlin.collections.EmptySet",
            "kotlin.collections.IndexedValue",
            "kotlin.collections.ReversedList",
            "kotlin.collections.ReversedListReadOnly",
            "kotlin.collections.builders.ListBuilder",
            "kotlin.collections.builders.ListBuilder\$BuilderSubList",
            "kotlin.collections.builders.MapBuilder",
            "kotlin.collections.builders.MapBuilderEntries",
            "kotlin.collections.builders.MapBuilderKeys",
            "kotlin.collections.builders.MapBuilderValues",
            "kotlin.collections.builders.SetBuilder",
        )

        private const val ACC_SYNTHETIC: Int = 0x1000
        private const val ACC_ENUM: Int = 0x4000
    }
}
