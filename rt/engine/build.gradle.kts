plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":rt:support"))

    implementation(project(":base:impl"))
    implementation(project(":api:public"))
    implementation(project(":validation:impl"))
    implementation(project(":validation:format"))
    implementation(project(":core:graph:impl"))
    implementation(project(":core:model:impl"))
    implementation(project(":lang:rt"))
}