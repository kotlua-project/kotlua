plugins {
    base
    alias(libs.plugins.kotlua.gradlePlugin)
}

val stdlib = rootProject.rootDir.resolve("lua-stdlib/build/lua/classes/kotlin-stdlib-lua.klib")

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)
    luaMainImplementation(files(stdlib))
}
