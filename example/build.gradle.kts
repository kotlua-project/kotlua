import io.github.kotlua.gradle.Kotlin2LuaCompile

plugins {
    base
    alias(libs.plugins.kotlua.gradlePlugin)
}

kotlua {
    compilerOptions {
        moduleName.set("example")
    }
}

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)
}

val minimalStdlib = rootProject.rootDir.resolve("lua-stdlib/minimal/build/lua/classes/kotlin-stdlib-lua.klib")

tasks {
    withType<Kotlin2LuaCompile>().configureEach {
        libraries.from(minimalStdlib)
    }
}
