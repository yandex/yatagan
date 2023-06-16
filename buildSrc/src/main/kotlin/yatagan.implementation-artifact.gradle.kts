plugins {
    id("yatagan.artifact")
}

kotlin {
    sourceSets.configureEach {
        languageSettings {
            optIn("com.yandex.yatagan.base.api.Incubating")
            optIn("com.yandex.yatagan.base.api.Internal")
        }
    }
}