plugins {
    id("yatagan.implementation-artifact")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":core:graph:api"))
    implementation(project(":base:impl"))
    implementation(project(":codegen:poetry"))
    implementation(project(":lang:compiled"))
    implementation(project(":instrumentation:impl"))

    implementation(libs.yataganDogFood.api)
    ksp(libs.yataganDogFood.ksp)
}