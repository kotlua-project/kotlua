package io.github.kotlua.name

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

private val luaPackage = StandardClassIds.BASE_KOTLIN_PACKAGE.child(Name.identifier("lua"))

val StandardClassIds.BASE_LUA_PACKAGE: FqName
    get() = luaPackage