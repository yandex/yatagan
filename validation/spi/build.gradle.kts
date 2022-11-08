plugins {
    id("yatagan.artifact")
    id("yatagan.documented")
}

dependencies {
    // Expose full model API
    api(project(":lang:api"))  // I-level
    api(project(":core:model:api"))  // II-level
    api(project(":core:graph:api")) // III-level

    // Validation API
    api(project(":validation:api"))
}