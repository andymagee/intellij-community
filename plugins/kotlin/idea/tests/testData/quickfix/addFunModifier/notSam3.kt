// "Add 'fun' modifier to 'I'" "false"
// DISABLE_ERRORS
// ACTION: Introduce import alias
// ACTION: Split property declaration
interface I {
    fun <T> f()
}

fun test() {
    val x = <caret>I {}
}
