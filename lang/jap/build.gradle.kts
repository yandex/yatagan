plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":lang:compiled"))

    implementation(project(":base"))
    implementation(libs.autoCommon)
}