package io.github.kotlua

import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinLuaPlugin : Plugin<Project> {
    companion object {
        const val LUA_TARGET_NAME = "lua"
    }

    override fun apply(target: Project) {
    }
}
