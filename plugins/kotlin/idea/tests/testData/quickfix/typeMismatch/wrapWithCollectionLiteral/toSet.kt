// "Wrap element with 'setOf()' call" "true"
// WITH_STDLIB

fun foo(a: String) {
    bar(a<caret>)
}

fun bar(a: Set<String>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithCollectionLiteralCallFix