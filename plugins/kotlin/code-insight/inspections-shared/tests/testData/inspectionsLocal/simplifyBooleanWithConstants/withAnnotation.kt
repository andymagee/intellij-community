// FIX: Simplify boolean expression
var b = true

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun foo() {
    if (@Ann <caret>b == true) {

    }
}