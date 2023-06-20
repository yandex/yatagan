plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    implementation(project(":api:public"))
    implementation(project(":core:graph:api"))
    implementation(project(":base:impl"))
    implementation(project(":codegen:poetry"))
    implementation(project(":lang:compiled"))
}