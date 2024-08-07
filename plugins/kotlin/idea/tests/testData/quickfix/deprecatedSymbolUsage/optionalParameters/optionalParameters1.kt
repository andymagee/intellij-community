// "Replace with 'newFun(p1, p2.toString(), p3, p4, p5)'" "true"

interface I {
    @Deprecated("", ReplaceWith("newFun(p1, p2.toString(), p3, p4, p5)"))
    fun oldFun(p1: String, p2: Int = 0, p3: String? = null, p4: String = p1, p5: Int = p2)

    fun newFun(p1: String, p2: String, p3: String?, p4: String, p5: Int)
}

fun foo(i: I) {
    i.<caret>oldFun("a")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix