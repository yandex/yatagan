plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    // Expose full model API
    api(project(":lang"))  // I-level
    api(project(":core"))  // II-level
    api(project(":graph")) // III-level

    // Validation API
    api(project(":validation"))
}