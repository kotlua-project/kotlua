package builtins.primitives

import org.jetbrains.kotlin.generators.builtins.numbers.primitives.BasePrimitivesGenerator
import org.jetbrains.kotlin.generators.builtins.numbers.primitives.FileBuilder
import java.io.PrintWriter

class LuaPrimitivesGenerator(writer: PrintWriter) : BasePrimitivesGenerator(writer) {
    override fun FileBuilder.modifyGeneratedFile() {
        suppress("NON_ABSTRACT_FUNCTION_WITH_NO_BODY")
        suppress("UNUSED_PARAMETER")
    }
}
