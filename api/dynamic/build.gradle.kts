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
    flatImplementation(project(":lang-common"))
    flatImplementation(project(":lang-rt"))
    flatImplementation(project(":validation-impl"))
    flatImplementation(project(":spi-impl"))

    // Project API, will not be merged and will be declared as a normal dependency.
    flatApi(project(":api"))
    flatApi(project(":graph"))
    flatApi(project(":core"))
    flatApi(project(":lang"))
    flatApi(project(":validation"))
    flatApi(project(":spi"))

    // Third-party api dependencies, not merged, included transitively.
    libApi("javax.inject:javax.inject:1")
    libApi(kotlin("stdlib"))
}