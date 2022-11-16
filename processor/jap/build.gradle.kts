plugins {
    id("yatagan.artifact")
}

dependencies {
    api(libs.autoCommon)

    implementation(project(":api:public"))
    implementation(project(":processor:common"))
    implementation(project(":lang:jap"))
}