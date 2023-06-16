plugins {
    id("yatagan.stable-api-artifact")
}

dependencies {
    api(project(":lang:api"))
    api(project(":validation:api"))
}
