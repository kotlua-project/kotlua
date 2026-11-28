[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlua-project/kotlua-gradle-plugin)](https://central.sonatype.com/namespace/io.github.kotlua-project)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://github.com/JetBrains/kotlin)

# kotlua

**Compile Kotlin to Lua**

Kotlua brings Kotlin, coroutines, and the Kotlin standard library to Lua 5.1 environments — complete with Lua interop, a Gradle plugin, and IntelliJ IDEA support.

## IntelliJ IDEA Plugin

For IDE support (syntax highlighting, code completion and navigation), install the **Kotlua** plugin in IntelliJ IDEA:

1. Download the latest plugin `.zip` from [GitHub Releases](https://github.com/kotlua-project/kotlua-idea-plugin/releases).
2. Open **Settings... → Plugins** in IntelliJ IDEA.
3. Click the gear icon (**⚙️**) → **Install Plugin from Disk...** and select the downloaded `.zip` archive.
4. Restart the IDE.

## Quick Start

### 1. Configure `settings.gradle.kts`

Add `mavenCentral()` to `pluginManagement` and `dependencyResolutionManagement`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-kotlua-app"
```

### 2. Configure `build.gradle.kts`

```kotlin
plugins {
    id("io.github.kotlua-project") version "2.4.21-0.1.0"
}

// optional configuration
kotlua {
    compilerOptions {
        moduleName = "my-app"
    }
}

// optional configuration
luaApplication {
    entryFile = "main.lua" // default: "main.lua"
    // args.addAll("--flag", "value")
}
```

`kotlin-stdlib-lua` is added automatically to `luaMainImplementation`.

### 3. Add Kotlin sources

Create `src/luaMain/kotlin/Main.kt`:

```kotlin
fun main() {
    println("Hello from Kotlua!")
}
```

### 4. Build and Run

```bash
./gradlew luaRunApplication
```

You can also override the entry file or pass arguments from the command line:

```bash
./gradlew luaRunApplication --entry=main.lua --args="--verbose input.txt"
```

## Gradle Tasks

| Task                | Description                                                |
|---------------------|------------------------------------------------------------|
| `kotlinLuaCompile`  | Compiles Kotlin sources into a `.klib`                     |
| `kotlinLuaIrLink`   | Links the module `.klib` and dependencies into Lua scripts |
| `assemble`          | Builds and links the project                               |
| `luaRunApplication` | Links and executes the configured Lua entry file           |

## Locating the Lua Interpreter

`luaRunApplication` resolves the Lua runtime in the following order:

1. **Repository-local `binaries/` directory** (checked in the project directory and root settings directory, including OS subdirectories `windows/`, `linux/`, `macos/` — convenient for committing team binaries in a wrapper-like layout).
2. **System `PATH`** (`lua5.1`, `lua51`, `luajit`, `lua`).

If no Lua interpreter is found, the task fails with a clear error listing all checked locations.

## License

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
