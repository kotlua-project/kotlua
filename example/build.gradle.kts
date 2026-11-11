plugins {
    base
    alias(libs.plugins.kotlua.gradlePlugin)
}

kotlua {
    compilerOptions {
        moduleName = "example"
    }
}

val minimalStdlib = rootProject.rootDir.resolve("lua-stdlib/minimal/build/lua/classes/kotlin-stdlib-lua.klib")

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)
    luaMainImplementation(files(minimalStdlib))
}
