// DISABLE_ERRORS

fun check() {
    val a: Int? = null
    if (a != null && <caret>(a < 0)) {}
}
