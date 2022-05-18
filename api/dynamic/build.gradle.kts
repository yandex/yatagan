plugins {
    id("daggerlite.optimized")
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    // Implementation, will be merged and optimized.
    flatImplementation(project(":base"))
    flatImplementation(project(":graph-impl"))
    flatImplementation(project(":core-impl"))
    flatImplementation(project(":lang-rt"))
    flatImplementation(project(":validation-impl"))

    // Project API, will not be merged and will be declared as a normal dependency.
    flatApi(project(":api"))
    flatApi(project(":graph"))
    flatApi(project(":core"))
    flatApi(project(":lang"))
    flatApi(project(":validation"))

    // Third-party api dependencies, not merged, included transitively.
    libApi("javax.inject:javax.inject:1")
    libApi(kotlin("stdlib"))
}