plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":codegen:poetry:api"))
    implementation(project(":lang:compiled"))

    implementation(libs.poets.kotlin)
    implementation(libs.poets.kotlinKsp)
    implementation(libs.ksp.api)
}