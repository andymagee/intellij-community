// "Change type to 'Int'" "true"
// ERROR: Null can not be a value of a non-null type Int
// K2_AFTER_ERROR: Null cannot be a value of a non-null type 'Int'.
interface Test<T> {
    val prop : T
}

class Other {
    fun doTest() {
        val some = object: Test<Int> {
            override val <caret>prop = null
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix