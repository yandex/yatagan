plugins {
    id("yatagan.artifact")
    id("yatagan.documented")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":api:common"))
    implementation(project(":rt:engine"))

    api(project(":api:public"))
    api(project(":rt:support"))
}

kotlin {
    explicitApi()
}