plugins {
    id("daggerlite.artifact")
}

dependencies {
    // Include javax.inject API
    api("javax.inject:javax.inject:1")

    // no stdlib required in api module.
}