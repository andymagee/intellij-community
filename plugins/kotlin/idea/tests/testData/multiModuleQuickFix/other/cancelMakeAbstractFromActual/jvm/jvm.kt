// "Make 'Op' 'abstract'|->Cancel" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// ERROR: Class 'Op' is not abstract and does not implement abstract member public abstract val a: Int defined in IFoo
actual open clas<caret>s Op actual constructor() : IFoo