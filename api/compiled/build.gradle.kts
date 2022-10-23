plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    api(project(":api:public"))

    implementation(project(":api:common"))
}