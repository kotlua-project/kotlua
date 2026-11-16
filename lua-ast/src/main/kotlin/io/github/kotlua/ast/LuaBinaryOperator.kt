/*
 * Copyright 2026 The kotlua Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.kotlua.ast

enum class LuaBinaryOperator(
    val precedence: Int,
    val isRightAssociative: Boolean = false,
) {
    Or(1),
    And(2),

    Gt(3),
    Gte(3),
    Lt(3),
    Lte(3),
    Eq(3),
    Neq(3),

    Concat(4, isRightAssociative = true),

    Add(5),
    Subtract(5),

    Multiply(6),
    Divide(6),
    Modulo(6),

    Power(8, isRightAssociative = true),
    ;

    companion object {
        const val UNARY_PRECEDENCE = 7
        const val ATOM_PRECEDENCE = 9
    }
}
