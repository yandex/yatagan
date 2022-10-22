plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":validation:format"))

    api(project(":spi"))
}