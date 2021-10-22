plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    // Include javax.inject API
    api("javax.inject:javax.inject:1")

    // no stdlib required in api module.
    //implementation(kotlin("stdlib"))
}