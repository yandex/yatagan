/**
 * DEPRECATED, WILL BE REMOVED IN A FUTURE RELEASE
 */

plugins {
    id("yatagan.stable-api-artifact")
}

dependencies {
    // Just an alias for `api-public`
    api(project(":api:public"))
}
