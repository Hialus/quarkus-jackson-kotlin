# quarkus-jackson-kotlin

Build a Kotlin Quarkus app in native mode, fire the first request, and watch it crash with:

```
KotlinReflectionInternalError: Cannot resolve constructors of class ...
  at KotlinNamesAnnotationIntrospector.findDefaultCreator
```

Jackson's Kotlin module calls `KClass.getPrimaryConstructor()`, which walks the
`@kotlin.Metadata` annotation that every Kotlin class carries. GraalVM strips that
annotation unless the class is registered for reflection. Quarkus' rest-jackson
auto-registration covers Java reflection but doesn't reach Kotlin metadata, so the usual
fix is a `@RegisterForReflection` aggregator that you maintain by hand. Forever.

This extension walks your Jandex index at build time and registers the Kotlin classes
that look like they need it. No config.

## Use it

Published via [JitPack](https://jitpack.io). Add the repo and pull in a tag:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.Hialus.quarkus-jackson-kotlin:quarkus-jackson-kotlin:0.1.0")
}
```

JitPack builds the repo on demand the first time someone asks for a tag, so the initial
resolve can take a minute. After that it's cached.

On boot Quarkus prints `jackson-kotlin` in the `Installed features:` line. During the
Gradle build the extension logs how many classes it grabbed
(`Registered N Kotlin class(es) for Jackson reflection`). If you don't see that, run
Gradle with `--info`.

## What it picks up

A class qualifies if it carries `@kotlin.Metadata` and matches one of:

- `data class`
- `enum class`
- any `com.fasterxml.jackson.annotation.*` annotation, on the class or a member
- `@kotlinx.serialization.Serializable`

It skips synthetic and inner/nested classes (the outer covers them), kotlinx.serialization's
`$$serializer` helpers, and anything already annotated `@RegisterForReflection` or
`@jakarta.ws.rs.Path` — those are Quarkus' job.

Scope is your own modules plus whatever you've added under
`quarkus.index-dependency.<name>` in `application.properties`. External Quarkus
extension jars are out of scope; extensions ship their own reflection metadata.

It's a heuristic. If something slips through, slap `@RegisterForReflection` on it.

## When you don't need it

Pure-Java apps don't have this problem. Neither does kotlinx.serialization on its own —
it generates code and never reflects. And if your whole serialization surface fits in
one `@RegisterForReflection(targets = [...])` aggregator, you don't need a dependency.

## Build from source

```bash
./gradlew publishToMavenLocal
```

If you're building inside Docker and want to use the mavenLocal jar instead of going
through JitPack, BuildKit named contexts work:

```bash
docker build \
  --build-context jackson_kotlin_local=$HOME/.m2/repository/com/github/Hialus/quarkus-jackson-kotlin \
  -f Dockerfile.native .
```

```dockerfile
COPY --from=jackson_kotlin_local . /root/.m2/repository/com/github/Hialus/quarkus-jackson-kotlin/
```

## License

MIT.
