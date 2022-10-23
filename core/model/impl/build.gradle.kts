plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core:model:api"))
    implementation(project(":validation:format"))
    implementation(project(":api:public"))
    implementation(project(":base"))

    implementation(kotlin("stdlib"))
}