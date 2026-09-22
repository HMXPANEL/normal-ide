/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HMX IDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with HMX IDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.utils

import java.io.File

/**
 * Detects whether a directory is a supported project type.
 *
 * Supported: Android Gradle, Java, Kotlin (Gradle), and Flutter (future-ready).
 */
object ProjectValidator {

  fun isSupportedProject(dir: File?): Boolean {
    if (dir == null || !dir.isDirectory) {
      return false
    }

    return hasAndroidGradleProject(dir) ||
      hasGradleProject(dir) ||
      hasJavaProject(dir) ||
      hasKotlinProject(dir) ||
      hasFlutterProject(dir)
  }

  private fun hasAndroidGradleProject(dir: File): Boolean {
    return File(dir, "settings.gradle").exists() ||
      File(dir, "settings.gradle.kts").exists() ||
      File(dir, "build.gradle").exists() ||
      File(dir, "build.gradle.kts").exists()
  }

  private fun hasGradleProject(dir: File): Boolean {
    return File(dir, "settings.gradle").exists() ||
      File(dir, "settings.gradle.kts").exists()
  }

  private fun hasJavaProject(dir: File): Boolean {
    return File(dir, "build.xml").exists() ||
      File(dir, "pom.xml").exists() ||
      findFileRecursive(dir, "src", "*.java")
  }

  private fun hasKotlinProject(dir: File): Boolean {
    return findFileRecursive(dir, "src", "*.kt") &&
      (File(dir, "build.gradle.kts").exists() || hasGradleProject(dir))
  }

  private fun hasFlutterProject(dir: File): Boolean {
    // ponytail: future-ready detection only; opening flow does not yet build Flutter
    return File(dir, "pubspec.yaml").exists()
  }

  private fun findFileRecursive(dir: File, subDirName: String, suffix: String): Boolean {
    val ext = suffix.removePrefix("*")
    val queue = ArrayDeque<File>().apply { add(dir) }
    var visited = 0
    while (queue.isNotEmpty() && visited < 200) {
      val current = queue.removeFirst()
      visited++
      val children = current.listFiles() ?: continue
      for (child in children) {
        if (!child.isDirectory) continue
        if (child.name == subDirName &&
          child.listFiles()?.any { it.name.endsWith(ext) } == true
        ) {
          return true
        }
        if (child.name != "build" && child.name != ".git") {
          queue.add(child)
        }
      }
    }
    return false
  }
}
