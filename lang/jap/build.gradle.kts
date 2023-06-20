plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":lang:compiled"))

    implementation(project(":base:impl"))
    implementation(libs.autoCommon)
}