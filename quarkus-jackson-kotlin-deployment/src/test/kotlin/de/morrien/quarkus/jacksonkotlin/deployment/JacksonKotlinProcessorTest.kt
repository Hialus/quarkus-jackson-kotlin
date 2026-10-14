package de.morrien.quarkus.jacksonkotlin.deployment

import org.jboss.jandex.ClassInfo
import org.jboss.jandex.Index
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

data class TopLevel(val value: String)

class PlainClass(val value: String)

class Outer {
    data class Nested(val value: String) {
        data class Deeper(val value: Int)
    }

    enum class Kind { A, B }

    class NotData(val value: String)
}

fun localHolder(): Any {
    data class Local(val value: String)
    return Local("x")
}

class JacksonKotlinProcessorTest {

    private val processor = JacksonKotlinProcessor()

    private val index: Index = Index.of(
        TopLevel::class.java,
        PlainClass::class.java,
        Outer::class.java,
        Outer.Nested::class.java,
        Outer.Nested.Deeper::class.java,
        Outer.Kind::class.java,
        Outer.NotData::class.java,
        localHolder().javaClass,
    )

    private fun selected(): List<ClassInfo> = processor.selectCandidates(index.knownClasses.asSequence())

    @Test
    fun `selects nested data classes and enums beside top-level ones`() {
        assertEquals(
            setOf(
                TopLevel::class.java.name,
                Outer.Nested::class.java.name,
                Outer.Nested.Deeper::class.java.name,
                Outer.Kind::class.java.name,
            ),
            selected().map { it.name().toString() }.toSet(),
        )
    }

    @Test
    fun `names every enclosing class of a nested class`() {
        val deeper = selected().single { it.name().toString() == Outer.Nested.Deeper::class.java.name }
        assertEquals(
            listOf(Outer.Nested::class.java.name, Outer::class.java.name),
            processor.enclosingClassNames(deeper),
        )
    }

    @Test
    fun `a top-level class has no enclosing class`() {
        val topLevel = selected().single { it.name().toString() == TopLevel::class.java.name }
        assertEquals(emptyList<String>(), processor.enclosingClassNames(topLevel))
    }
}
