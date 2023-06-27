plugins {
    id("yatagan.artifact")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":core:graph:api"))
    implementation(project(":base"))
    implementation(project(":codegen:poetry"))
    implementation(project(":lang:compiled"))

    implementation(libs.yataganDogFood.api)
    ksp(libs.yataganDogFood.ksp)
}