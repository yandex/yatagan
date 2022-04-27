package com.yandex.daggerlite.gradle

// See https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
// See https://regex101.com/r/vkijKf/1/
private val semVerRegex = ("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
        "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\$").toRegex()

fun isValidSemVerString(string: String): Boolean {
    return semVerRegex.matches(string)
}

const val RepositoryBrowseUrl = "https://bitbucket.browser.yandex-team.ru/projects/ML/repos/dagger-lite/browse"