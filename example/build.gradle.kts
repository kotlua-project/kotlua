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
