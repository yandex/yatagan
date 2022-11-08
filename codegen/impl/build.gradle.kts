plugins {
    id("yatagan.artifact")
}

dependencies {
    implementation(project(":api:public"))
    implementation(project(":core:graph:api"))
    implementation(project(":base"))
    implementation(project(":codegen:poetry"))
    implementation(project(":lang:compiled"))
    implementation(kotlin("stdlib"))
}