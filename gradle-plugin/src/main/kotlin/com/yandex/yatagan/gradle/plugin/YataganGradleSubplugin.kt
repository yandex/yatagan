/*
 * Copyright 2026 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Applies the Yatagan Kotlin compiler plugin (KCP backend) to every Kotlin compilation and
 * adds the matching `api-public` runtime dependency.
 *
 * Yatagan processor options can be passed as regular compiler plugin options:
 * `-P plugin:com.yandex.yatagan:<yatagan.option>=<value>`.
 */
public class YataganGradleSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project): Unit = Unit

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.yandex.yatagan",
        artifactId = "processor-kcp",
        version = yataganVersion,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        project.dependencies.add(
            kotlinCompilation.defaultSourceSet.implementationConfigurationName,
            "com.yandex.yatagan:api-public:$yataganVersion",
        )
        return project.provider { emptyList() }
    }

    private companion object {
        const val PLUGIN_ID = "com.yandex.yatagan"

        val yataganVersion: String by lazy {
            checkNotNull(YataganGradleSubplugin::class.java.classLoader.getResourceAsStream("yatagan.version")) {
                "yatagan.version resource is missing from the Yatagan Gradle plugin jar"
            }.use { stream -> stream.reader().readText().trim() }
        }
    }
}
