package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.translateCall
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.nonDispatchArguments
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

class IrElementToLuaStatementTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {

    override fun visitFunction(declaration: IrFunction, data: LuaGenerationContext): LuaStatement {
        irError("All functions must be already lowered") {
            withIrEntry("declaration", declaration)
        }
    }

    override fun visitBlockBody(body: IrBlockBody, data: LuaGenerationContext): LuaStatement {
        return LuaBlock(
            statements = body.statements.map {
                it.accept(this, data)
            },
        )
    }

    override fun visitReturnableBlock(expression: IrReturnableBlock, data: LuaGenerationContext): LuaStatement {
        return super.visitReturnableBlock(expression, data)
    }

    override fun visitBlock(expression: IrBlock, data: LuaGenerationContext): LuaStatement {
        return LuaBlock(
            statements = expression.statements.map {
                it.accept(this, data)
            },
        )
    }

    override fun visitComposite(expression: IrComposite, data: LuaGenerationContext): LuaStatement {
        return super.visitComposite(expression, data)
    }

    override fun visitExpression(expression: IrExpression, data: LuaGenerationContext): LuaStatement {
        return super.visitExpression(expression, data)
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression, data: LuaGenerationContext): LuaStatement {
        return super.visitFunctionExpression(expression, data)
    }

    override fun visitBreak(jump: IrBreak, data: LuaGenerationContext): LuaStatement {
        return LuaStatement.Break
    }

    override fun visitContinue(jump: IrContinue, data: LuaGenerationContext): LuaStatement {
        irError("Unsupported operation") {
            withIrEntry("operation", jump)
        }
    }

    override fun visitSetField(expression: IrSetField, data: LuaGenerationContext): LuaStatement {
        return super.visitSetField(expression, data)
    }

    override fun visitSetValue(expression: IrSetValue, data: LuaGenerationContext): LuaStatement {
        return super.visitSetValue(expression, data)
    }

    override fun visitReturn(expression: IrReturn, data: LuaGenerationContext): LuaStatement {
        return super.visitReturn(expression, data)
    }

    override fun visitThrow(expression: IrThrow, data: LuaGenerationContext): LuaStatement {
        return super.visitThrow(expression, data)
    }

    override fun visitVariable(declaration: IrVariable, data: LuaGenerationContext): LuaStatement {
        return super.visitVariable(declaration, data)
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: LuaGenerationContext): LuaStatement {
        return super.visitDelegatingConstructorCall(expression, data)
    }

    override fun visitCall(expression: IrCall, data: LuaGenerationContext): LuaStatement {
        val function = expression.symbol.owner.realOverrideTarget
        val transformer = IrElementToLuaExpressionTransformer()
        return LuaExpression.FunctionCall(
            target = LuaName(function.name.identifier),
            args = function.nonDispatchParameters.map {
                val arg = expression.arguments[it.indexInParameters]!!
                arg.accept(transformer, data)
            },
        )
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: LuaGenerationContext): LuaStatement {
        return super.visitInstanceInitializerCall(expression, data)
    }

    override fun visitTry(aTry: IrTry, data: LuaGenerationContext): LuaStatement {
        return super.visitTry(aTry, data)
    }

    override fun visitWhen(expression: IrWhen, data: LuaGenerationContext): LuaStatement {
        return super.visitWhen(expression, data)
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: LuaGenerationContext): LuaStatement {
        return super.visitWhileLoop(loop, data)
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: LuaGenerationContext): LuaStatement {
        return super.visitDoWhileLoop(loop, data)
    }
}
