plugins {
    id("yatagan.artifact")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
    explicitApi()
}

apiValidation {
    nonPublicMarkers += listOf(
        "com.yandex.yatagan.base.api.Incubating",
        "com.yandex.yatagan.base.api.Internal",
    )

    ignoredPackages += listOf(
        "com.yandex.yatagan.internal",
    )
}
