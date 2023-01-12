plugins {
    id("yatagan.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":validation:impl"))
    implementation(project(":validation:format"))
    implementation(project(":core:graph:impl"))
    implementation(project(":core:model:impl"))
    implementation(project(":codegen:impl"))

    runtimeOnly(kotlin("reflect"))
}