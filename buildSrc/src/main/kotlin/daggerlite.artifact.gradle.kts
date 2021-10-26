plugins {
    id("daggerlite.base-module")
    `maven-publish`
}

version = "0.0.1-SNAPSHOT"
group = "com.yandex.daggerlite"

publishing {
    publications {
        create<MavenPublication>(name) {
            from(components["java"])
        }
    }
}
