// "Remove redundant assignment" "true"
fun test(n: Int) {
    var i: Int
    <caret>i = n
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedValueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix