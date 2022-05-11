# Dagger Lite (DL)

This is the developer and contributor doc.

For the user docs, see [the latest user documentation](https://teamcity.browser.yandex-team.ru/repository/download/Mobile_DaggerLite_Publish/lastSuccessful/kdocs.zip!/index.html).

## Assembling

DL uses [Gradle](https://docs.gradle.org/current/userguide/userguide.html) build system.

```shell
# To assemble everything
./gradlew assemble

# To run tests
./gradlew test

# To assemble DL and publish it to local maven repository
./gradlew publishToMavenLocal --no-configuration-cache
```

## Versioning

DL uses [semantic versioning](https://semver.org/) scheme. 
Before version `1.0.0` there's no stable API and no strict breaking changes tracking is performed. 

## Coding conventions

1. The project is written in pure Kotlin (JVM), and, generally, no Java code is allowed.
2. Each Kotlin module _should_ contain only a single JVM package,
   which _should_ be named according to the module hierarchy,
   e.g. `:graph:graph-impl` should define a package `com.yandex.daggerlite.graph.impl`. Each package _must_ only be
   present in a single module.
3. Subpackage hierarchy is not expressed via actual directories, as Kotlin language permits;
   sources are located directly in a source set root, while containing matching `package` directive.
   In a rare case where a subpackage is necessary (e.g. `internal`), a single subdirectory should be created for it.
4. Each modeled entity's API _should_ be expressed via an `interface` and one or more implementations. Interface and
   implementations _should_ reside in a separate modules, e.g. `:my-feature` and `:my-feature-impl` correspondingly.
