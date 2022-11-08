plugins {
    id("yatagan.artifact")
}

dependencies {
    implementation(project(":validation:format"))

    api(project(":spi"))
}