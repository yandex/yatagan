plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":api:public"))

    implementation(project(":api:common"))
}

kotlin {
    explicitApi()
}