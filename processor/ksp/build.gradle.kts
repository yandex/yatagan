plugins {
    id("yatagan.artifact")
}

dependencies {
    api(libs.ksp.api)

    implementation(project(":api:public"))
    implementation(project(":processor:common"))
    implementation(project(":lang:ksp"))
}