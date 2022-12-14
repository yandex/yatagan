plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":api:public"))
    api(project(":rt:support"))

    implementation(project(":base"))
    implementation(project(":api:common"))
    implementation(project(":rt:engine"))
}

kotlin {
    explicitApi()
}