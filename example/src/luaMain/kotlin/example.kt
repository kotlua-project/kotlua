package io.github.kotlua.example

fun greet(name: String) {
    val prefix = "Hello, "
    lua("print(prefix .. name)")
}

fun addFive(value: Int): Int {
    return lua("5 + value")
}

fun main(args: Array<String>) {
    for (string in args) {
        println("Arg = $string")
    }
    greet("Lua!")
    val c = 4
    val b = addFive(c)
    val d = c + 5 == b
    greet("Hey $d")
    print("Enter your name: ")
    val name = readln()
    println("Hello, $name!")
}
