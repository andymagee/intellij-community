// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract
// K2_AFTER_ERROR: Property must be initialized or be abstract.
class A {
    fun test() {
        <caret>foo = 1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction