fun myFun<caret>(param: @MyAnnotation (String.() -> Unit)) {} // quick documentation myFun

@Target(AnnotationTarget.TYPE)
annotation class MyAnnotation

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">myFun</span>(
//K2_INFO:     <span style="color:#000000;">param</span><span style="">: </span><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://MyAnnotation">MyAnnotation</a></span> <span style="">(</span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style="">.</span><span style="">(</span><span style="">) </span><span style="">-&gt;</span> <span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span><span style="">)</span>
//K2_INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;renderAnnotatedExtensionFunctionType.kt<br/></div>



//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">myFun</span>(
//INFO:     <span style="color:#000000;">param</span><span style="">: </span><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://MyAnnotation">MyAnnotation</a></span><span style="">()</span> <span style="">(</span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style="">.</span><span style="">(</span><span style="">) </span><span style="">-&gt;</span> <span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span><span style="">)</span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;renderAnnotatedExtensionFunctionType.kt<br/></div>
