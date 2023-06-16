plugins {
    id("yatagan.stable-api-artifact")
}

dependencies {
    api(project(":api:public"))
    api(project(":rt:support"))

    implementation(project(":base:impl"))
    implementation(project(":rt:engine"))
}
