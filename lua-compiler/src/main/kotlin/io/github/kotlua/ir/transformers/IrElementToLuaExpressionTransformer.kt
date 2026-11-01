package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDynamicMemberExpression
import org.jetbrains.kotlin.ir.expressions.IrDynamicOperatorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrRawFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen

class IrElementToLuaExpressionTransformer : BaseIrElementToLuaNodeTransformer<LuaExpression, LuaGenerationContext>() {

    override fun visitMemberAccess(expression: IrMemberAccessExpression<*>, data: LuaGenerationContext): LuaExpression {
        return super.visitMemberAccess(expression, data)
    }

    override fun visitComposite(expression: IrComposite, data: LuaGenerationContext): LuaExpression {
        return super.visitComposite(expression, data)
    }

    override fun visitVararg(expression: IrVararg, data: LuaGenerationContext): LuaExpression {
        return super.visitVararg(expression, data)
    }

    override fun visitExpressionBody(body: IrExpressionBody, data: LuaGenerationContext): LuaExpression {
        return super.visitExpressionBody(body, data)
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression, data: LuaGenerationContext): LuaExpression {
        return super.visitFunctionExpression(expression, data)
    }

    override fun visitConst(expression: IrConst, data: LuaGenerationContext): LuaExpression {
        return super.visitConst(expression, data)
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: LuaGenerationContext): LuaExpression {
        return super.visitStringConcatenation(expression, data)
    }

    override fun visitGetField(expression: IrGetField, data: LuaGenerationContext): LuaExpression {
        return super.visitGetField(expression, data)
    }

    override fun visitGetValue(expression: IrGetValue, data: LuaGenerationContext): LuaExpression {
        return super.visitGetValue(expression, data)
    }

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: LuaGenerationContext): LuaExpression {
        return super.visitGetObjectValue(expression, data)
    }

    override fun visitSetField(expression: IrSetField, data: LuaGenerationContext): LuaExpression {
        return super.visitSetField(expression, data)
    }

    override fun visitSetValue(expression: IrSetValue, data: LuaGenerationContext): LuaExpression {
        return super.visitSetValue(expression, data)
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: LuaGenerationContext): LuaExpression {
        return super.visitDelegatingConstructorCall(expression, data)
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: LuaGenerationContext): LuaExpression {
        return super.visitConstructorCall(expression, data)
    }

    override fun visitCall(expression: IrCall, data: LuaGenerationContext): LuaExpression {
        return super.visitCall(expression, data)
    }

    override fun visitWhen(expression: IrWhen, data: LuaGenerationContext): LuaExpression {
        return super.visitWhen(expression, data)
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: LuaGenerationContext): LuaExpression {
        return super.visitTypeOperator(expression, data)
    }

    override fun visitDynamicMemberExpression(expression: IrDynamicMemberExpression, data: LuaGenerationContext): LuaExpression {
        return super.visitDynamicMemberExpression(expression, data)
    }

    override fun visitDynamicOperatorExpression(expression: IrDynamicOperatorExpression, data: LuaGenerationContext): LuaExpression {
        return super.visitDynamicOperatorExpression(expression, data)
    }

    override fun visitRawFunctionReference(expression: IrRawFunctionReference, data: LuaGenerationContext): LuaExpression {
        return super.visitRawFunctionReference(expression, data)
    }
}
