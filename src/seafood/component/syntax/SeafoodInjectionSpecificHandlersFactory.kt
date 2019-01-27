package seafood.component.syntax

import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.JavaScriptSpecificHandlersFactory
import com.intellij.lang.javascript.psi.JSEmbeddedContent
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.impl.JSReferenceExpressionImpl
import com.intellij.lang.javascript.psi.resolve.JSReferenceExpressionResolver
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

// todo: implement
class SeafoodInjectionSpecificHandlersFactory: JavaScriptSpecificHandlersFactory() {
    override fun createReferenceExpressionResolver(referenceExpression: JSReferenceExpressionImpl?,
                                                   ignorePerformanceLimits: Boolean): ResolveCache.PolyVariantResolver<JSReferenceExpressionImpl> =
            SeafoodInjectionReferenceExpressionResolver(referenceExpression, ignorePerformanceLimits)
}

class SeafoodInjectionReferenceExpressionResolver(referenceExpression: JSReferenceExpressionImpl?,
                                                  ignorePerformanceLimits: Boolean) :
        JSReferenceExpressionResolver(referenceExpression!!, ignorePerformanceLimits) {
    override fun resolve(ref: JSReferenceExpressionImpl, incompleteCode: Boolean): Array<ResolveResult> =
            resolveInLocalContext(ref) ?:
//            resolveInCurrentComponentDefinition(ref) ?:
            super.resolve(ref, incompleteCode)

    private fun resolveInLocalContext(ref: JSReferenceExpressionImpl): Array<ResolveResult>? {
        if (ref.qualifier != null) return null

        val injectedLanguageManager = InjectedLanguageManager.getInstance(ref.project)
        // injection host can be xml attribute value or embedded js inside jade tag - this we just skip moving up
        val host = injectedLanguageManager.getInjectionHost(ref)
        val elRef: Ref<PsiElement> = Ref(null)
        PsiTreeUtil.findFirstParent(host ?: ref, Condition {
            if (it is PsiFile) return@Condition true

            val valueElement = (it as? XmlTag)?.getAttribute("v-for")?.valueElement ?: return@Condition false
            // vue v-for embedded in attribute value in vue file & html template language
            val vFor = getCachedVForInsideAttribute(valueElement, injectedLanguageManager) ?: return@Condition false
            elRef.set(vFor.getVarStatement()?.variables?.firstOrNull { it.name == ref.referenceName })
            return@Condition !elRef.isNull
        })
        val foundElement = elRef.get() ?: return null
        return arrayOf(PsiElementResolveResult(foundElement))
    }

    private fun getCachedVForInsideAttribute(valueElement: XmlAttributeValue,
                                             injectedLanguageManager: InjectedLanguageManager): SeafoodForExpression? {
        // <template lang="jade">, vue injected inside js string embedded in jade tag
        // or vue injected into xml attribute value directly - in html
        return CachedValuesManager.getCachedValue(valueElement, CachedValueProvider {
            var vFor = PsiTreeUtil.findChildOfType(valueElement, SeafoodForExpression::class.java)
            if (vFor == null) {
                var lookForInjectedInside: PsiElement = valueElement
                if (HTMLLanguage.INSTANCE != valueElement.language) {
                    val embeddedJS = PsiTreeUtil.findChildOfType(valueElement, JSEmbeddedContent::class.java)
                    val literal = embeddedJS?.firstChild as? JSLiteralExpression
                    if (literal != null) {
                        lookForInjectedInside = literal
                    }
                }
                vFor = injectedLanguageManager.getInjectedPsiFiles(lookForInjectedInside)
                        ?.map { PsiTreeUtil.findChildOfType(it.first, SeafoodForExpression::class.java) }?.firstOrNull()
            }
            return@CachedValueProvider CachedValueProvider.Result(vFor, valueElement)
        })
    }

//    private fun resolveInCurrentComponentDefinition(ref: JSReferenceExpression): Array<ResolveResult>? {
//        ref.referenceName ?: return null
//        val obj = findScriptWithExport(ref)?.second?.stubSafeElement as? JSObjectLiteralExpression
////                ?: findInMountedVueInstance(ref) ?: return null
//        val descriptor = VueComponentDetailsProvider.INSTANCE.resolveAttribute(obj, ref.referenceName!!, false) ?: return null
//        return arrayOf(PsiElementResolveResult(descriptor.declaration!!))
//    }
}

//fun findInMountedVueInstance(reference : JSReferenceExpression) : JSObjectLiteralExpression? {
//    val xmlHost = InjectedLanguageManager.getInstance(reference.project).getInjectionHost(reference) ?: return null
//    val ref : Ref<JSObjectLiteralExpression?> = Ref(null)
//    PsiTreeUtil.findFirstParent(xmlHost, Condition {
//        if (it is PsiFile) return@Condition true
//        val idValue = (it as? XmlTag)?.getAttribute("id")?.valueElement?.value ?: return@Condition false
//        if (!StringUtil.isEmptyOrSpaces(idValue)) {
//            val elements = resolve("#" + idValue, GlobalSearchScope.projectScope(reference.project), VueOptionsIndex.KEY) ?: return@Condition false
//            val element = onlyLocal(elements).firstOrNull()
//            val obj = element as? JSObjectLiteralExpression ?: PsiTreeUtil.getParentOfType(element, JSObjectLiteralExpression::class.java)
//            ref.set(obj)
//            return@Condition obj != null
//        }
//        false
//    })
//    return ref.get()
//}

fun findScriptWithExport(element: PsiElement) : Pair<PsiElement, ES6ExportDefaultAssignment>? {
    val xmlFile = getContainingXmlFile(element) ?: return null

    val module = seafood.component.codeInsight.findModule(xmlFile) ?: return null
    val defaultExport = com.intellij.lang.ecmascript6.resolve.ES6PsiUtil.findDefaultExport(module)
            as? ES6ExportDefaultAssignment ?: return null
    if (defaultExport.stubSafeElement is JSObjectLiteralExpression) {
        return Pair(module, defaultExport)
    }
    return null
}

fun getContainingXmlFile(element: PsiElement): XmlFile? =
        (element.containingFile as? XmlFile ?: element as? XmlFile ?:
        InjectedLanguageManager.getInstance(element.project).getInjectionHost(element)?.containingFile as? XmlFile)
