plugins {
    id("yatagan.artifact")
}

dependencies {
    implementation(project(":base:impl"))
    implementation(project(":validation:impl"))
    implementation(project(":validation:format"))
    implementation(project(":core:graph:impl"))
    implementation(project(":core:model:impl"))
    implementation(project(":codegen:impl"))
    implementation(project(":lang:common"))

    runtimeOnly(kotlin("reflect"))
}