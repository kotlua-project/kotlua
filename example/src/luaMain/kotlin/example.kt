package io.github.kotlua.example

fun shortestPath(graph: Map<Int, List<Pair<Int, Int>>>, s: Int, t: Int): Pair<Int, List<Int>> {
    val infinity = Int.MAX_VALUE
    val vertices = graph.keys
    val l = mutableMapOf<Int, Int>()
    val theta = mutableMapOf<Int, Int?>()
    val m = vertices.toMutableSet()
    var p = s

    for (v in vertices) {
        l[v] = if (v == s) 0 else infinity
    }

    while (p != t) {
        val neighbors = graph[p] ?: emptyList()

        for ((neighbor, weight) in neighbors) {
            if (neighbor in m) {
                val a = l[p]!! + weight
                if (a < l[neighbor]!!) {
                    l[neighbor] = a
                    theta[neighbor] = p
                }
            }
        }

        val vStar = m.minByOrNull { l[it] ?: infinity } ?: break
        m.remove(vStar)
        p = vStar
    }

    val path = mutableListOf<Int>()
    var current = t
    while (current != s) {
        path.add(current)
        current = theta[current] ?: break
    }
    path.add(s)
    path.reverse()

    return Pair(l[t] ?: infinity, path)
}

fun main() {
    val graph = mapOf(
        1 to listOf(3 to 3, 5 to 8),
        2 to listOf(3 to 2, 5 to 1),
        3 to listOf(6 to 4, 4 to 5, 8 to 6),
        4 to listOf(6 to 2, 7 to 2),
        5 to listOf(7 to 2),
        6 to listOf(9 to 2, 8 to 4),
        7 to listOf(9 to 8, 10 to 2),
        8 to listOf(11 to 8, 12 to 5),
        9 to listOf(11 to 2, 12 to 1),
        10 to listOf(11 to 2, 12 to 3),
        11 to emptyList(),
        12 to emptyList()
    )
    val (distance, path) = shortestPath(graph, 1, 12)
    println("Shortest distance: $distance")
    println("Shortest path: $path")
}
