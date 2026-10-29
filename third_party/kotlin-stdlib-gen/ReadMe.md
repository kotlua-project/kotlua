## Code Generation for Standard Library

Some of the code in the standard library is created by code generation based on templates.
For example, many `Array` methods need to be implemented separately for `Array<T>`, `ByteArray`, `ShortArray`, `IntArray`, etc.

To run the code generator use the following command in the root directory of the project:

    ./gradlew :tools:kotlin-stdlib-gen:run

> Note: on Windows type `gradlew` without the leading `./`

This then runs the script which generates a significant part of stdlib sources from the [templates](src/templates) authored with a special kotlin based DSL.

### Notice
This directory contains code derived from the official Kotlin repository:
- **Upstream Repository**: https://github.com/JetBrains/kotlin
- **Original Paths**: `libraries/tools/kotlin-stdlib-gen`, `generators/builtins`
- **Original Copyright**: Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
- **License**: Apache License 2.0 (see `license/LICENSE` in this directory)

### Modifications
Modified for the `kotlua` project by The kotlua Authors (2026):
- Adapted generation targets, templates, and outputs for Lua execution and runtime.
- Modified Gradle build configuration to fit into the kotlua project structure.
