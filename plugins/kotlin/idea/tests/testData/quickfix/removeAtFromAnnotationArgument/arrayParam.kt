// "Remove @ from annotation argument" "true"
// DISABLE_ERRORS

annotation class Y()
annotation class X(val value: Array<Y>)

@X(arrayOf(Y(), @Y()<caret>))
fun foo() {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveAtFromAnnotationArgument
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveAtFromAnnotationArgument