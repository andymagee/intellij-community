/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.konan.file.unzipTo
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.exists

object KotlinNativeVersion {
    /** This field is automatically setup from project-module-updater.
     *  See [org.jetbrains.tools.model.updater.updateKGPVersionForKotlinNativeTests]
     */
    private const val kotlinGradlePluginVersion: String = "2.2.0-dev-15683"

    /** Return bootstrap version or version from properties file of specified Kotlin Gradle Plugin.
     *  Make sure localMaven has kotlin-gradle-plugin with required version for cooperative development environment.
     */
    val resolvedKotlinNativeVersion: String by lazy { getKotlinNativeVersionFromKotlinGradlePluginPropertiesFile() }

    /**
     * Returns a KGP version that corresponds to the [resolvedKotlinNativeVersion]
     */
    val resolvedKotlinGradlePluginVersion: String by lazy {
        resolvedKotlinNativeVersion // force resolution to make sure the K/N version for this KGP version actually exists
        kotlinGradlePluginVersion
    }

    private fun getKotlinNativeVersionFromKotlinGradlePluginPropertiesFile(): String {
        val outputPath = Paths.get(PathManager.getCommunityHomePath()).resolve("out")
            .resolve("kotlin-gradle-plugin")
            .resolve(kotlinGradlePluginVersion)

        if (!outputPath.exists()) {
            File(outputPath.toString()).mkdirs()
        }
        val propertiesPath = outputPath.resolve("project.properties")

        if (!propertiesPath.exists()) {
            val localRepository = File(FileUtil.getTempDirectory()).toPath()
            val kotlinGradlePluginPath = KotlinGradlePluginDownloader.downloadKotlinGradlePlugin(kotlinGradlePluginVersion, localRepository)
            kotlinGradlePluginPath.unzipTo(outputPath)
        }

        val propertiesFromKGP = Properties()
        FileInputStream(propertiesPath.toFile()).use { propertiesFromKGP.load(it) }

        return propertiesFromKGP.getProperty("kotlin.native.version")
    }
}