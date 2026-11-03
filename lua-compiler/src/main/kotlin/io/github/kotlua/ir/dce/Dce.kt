@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.github.kotlua.ir.dce

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.copyTypeParametersFrom
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

fun runSimpleDce(modules: Iterable<IrModuleFragment>) {
    val mainFunction = modules
        .flatMap { it.files }
        .flatMap { it.declarations }
        .filterIsInstance<IrSimpleFunction>()
        .firstOrNull { it.isTopLevel && it.name.asString() == "main" }
        ?: error("Main function not found!")

    val reachable = mutableSetOf<IrDeclaration>()
    val queue = ArrayDeque<IrDeclaration>()

    fun enqueue(declaration: IrDeclaration?) {
        if (declaration != null && reachable.add(declaration)) {
            queue.addLast(declaration)
        }
    }

    enqueue(mainFunction)

    val scanner = object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitCall(expression: IrCall) {
            super.visitCall(expression)
            enqueue(expression.symbol.owner)
            expression.type.classOrNull?.owner?.let { enqueue(it) }
        }

        override fun visitConstructorCall(expression: IrConstructorCall) {
            super.visitConstructorCall(expression)
            enqueue(expression.symbol.owner)
            enqueue(expression.symbol.owner.constructedClass)
        }

        override fun visitFieldAccess(expression: IrFieldAccessExpression) {
            super.visitFieldAccess(expression)
            enqueue(expression.symbol.owner)
        }
    }

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()

        if (current is IrFunction) {
            current.returnType.classOrNull?.owner?.let { enqueue(it) }
            current.parameters.forEach { param ->
                param.type.classOrNull?.owner?.let { enqueue(it) }
            }
            current.body?.acceptVoid(scanner)
        }

        if (current is IrField) {
            current.type.classOrNull?.owner?.let { enqueue(it) }
            current.initializer?.acceptVoid(scanner)
        }

        if (current is IrClass) {
            current.superTypes.forEach { superType ->
                superType.classOrNull?.owner?.let { enqueue(it) }
            }
        }
    }

    for (module in modules) {
        for (file in module.files) {
            file.declarations.retainAll { declaration ->
                isKept(declaration, reachable)
            }
        }
    }
}

private fun isKept(declaration: IrDeclaration, reachable: Set<IrDeclaration>): Boolean {
    if (declaration in reachable) return true

    if (declaration is IrClass) {
        declaration.declarations.retainAll { isKept(it, reachable) }
        return declaration in reachable || declaration.declarations.isNotEmpty()
    }

    if (declaration is IrProperty) {
        val getterAlive = declaration.getter?.let { it in reachable } ?: false
        val setterAlive = declaration.setter?.let { it in reachable } ?: false
        val fieldAlive = declaration.backingField?.let { it in reachable } ?: false
        return getterAlive || setterAlive || fieldAlive
    }

    return false
}
