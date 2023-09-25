plugins {
    id("yatagan.implementation-artifact")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":core:graph:api"))
    implementation(project(":base:impl"))
    implementation(project(":codegen:poetry:java"))
    implementation(project(":lang:compiled"))

    implementation(libs.yataganDogFood.api)
    ksp(libs.yataganDogFood.ksp)
}