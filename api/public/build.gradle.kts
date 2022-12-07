plugins {
    id("yatagan.artifact")
}

dependencies {
    // Include javax.inject API
    api("javax.inject:javax.inject:1")
}

kotlin {
    explicitApi()
}