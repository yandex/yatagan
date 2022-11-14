plugins {
    id("yatagan.artifact")
    id("yatagan.documented")
}

dependencies {
    api(project(":api:public"))

    implementation(project(":api:common"))
}

kotlin {
    explicitApi()
}