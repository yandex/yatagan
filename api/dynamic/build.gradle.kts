plugins {
    id("daggerlite.optimized")
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    // Implementation, will be merged and optimized.
    flatImplementation(project(":base"))
    flatImplementation(project(":core:graph:impl"))
    flatImplementation(project(":core:model:impl"))
    flatImplementation(project(":lang:common"))
    flatImplementation(project(":lang:rt"))
    flatImplementation(project(":validation:impl"))
    flatImplementation(project(":validation:format"))
    flatImplementation(project(":api:common"))

    // Project API, will not be merged and will be declared as a normal dependency.
    flatApi(project(":api:public"))
    flatApi(project(":core:graph:api"))
    flatApi(project(":core:model:api"))
    flatApi(project(":lang:api"))
    flatApi(project(":validation:api"))
    flatApi(project(":validation:spi"))

    // Third-party api dependencies, not merged, included transitively.
    libApi("javax.inject:javax.inject:1")
    libApi(kotlin("stdlib"))
}