plugins {
    id("yatagan.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":api:public"))
    implementation(project(":validation:impl"))
    implementation(project(":validation:format"))
    implementation(project(":core:graph:impl"))
    implementation(project(":core:model:impl"))
    implementation(project(":lang:rt"))

    api(project(":rt:support"))
}