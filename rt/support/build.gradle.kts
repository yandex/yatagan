plugins {
    id("yatagan.artifact")
}

dependencies {
    // Just for RichString. Consider using more narrow dependency
    api(project(":validation:api"))
}