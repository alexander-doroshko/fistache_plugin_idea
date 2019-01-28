package seafood.component.syntax

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.javascript.JSInjectionBracesUtil
import com.intellij.lang.javascript.JSInjectionBracesUtil.injectInXmlTextByDelimiters
import com.intellij.lang.javascript.index.JavaScriptIndex
import com.intellij.lang.javascript.psi.JSDefinitionExpression
import com.intellij.lang.javascript.psi.JSEmbeddedContent
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.impl.JSLiteralExpressionImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl
import com.intellij.psi.impl.source.xml.XmlElementImpl
import com.intellij.psi.impl.source.xml.XmlTextImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.NullableFunction
import seafood.component.SeafoodFileType
import seafood.component.codeInsight.*
import seafood.component.index.SeafoodOptionsIndex
import seafood.component.index.hasSeafood
import seafood.component.index.resolve
import java.util.*

class SeafoodInjector : MultiHostInjector {
    companion object {
        private val delimitersOptionHolders = setOf("Vue.config.delimiters", "Vue.options.delimiters")

        val BRACES_FACTORY: NullableFunction<PsiElement, Pair<String, String>> = JSInjectionBracesUtil.delimitersFactory(
                SeafoodInjectionLanguage.INSTANCE.displayName) { project, key ->
                    if (project == null || key == null) return@delimitersFactory null
                    calculateDelimitersFromIndex(project, key) ?: calculateDelimitersFromAssignment(project, key)
                }

        private fun calculateDelimitersFromIndex(project: Project, key: String): Pair<String, PsiElement>? {
            val elements = resolve("", GlobalSearchScope.projectScope(project), SeafoodOptionsIndex.KEY) ?: return null
            val element = SeafoodComponents.onlyLocal(elements).firstOrNull() ?: return null
            val obj = element as? JSObjectLiteralExpression
                    ?: PsiTreeUtil.getParentOfType(element, JSObjectLiteralExpression::class.java)
                    ?: return null
            val property = findProperty(obj, "delimiters") ?: return null
            val delimiter = getDelimiterValue(property, key) ?: return null
            return Pair.create(delimiter, element)
        }

        private fun calculateDelimitersFromAssignment(project: Project, key: String): Pair<String, PsiElement>? {
            val delimitersDefinitions = JavaScriptIndex.getInstance(project).getSymbolsByName("delimiters", false)
            return delimitersDefinitions.filter {
                it is JSDefinitionExpression &&
                        (it as PsiElement).context != null &&
                        it.qualifiedName in delimitersOptionHolders
            }.map {
                val delimiter = getDelimiterValue((it as PsiElement).context!!, key)
                if (delimiter != null) return Pair.create(delimiter, it)
                return null
            }.firstOrNull()
        }

        private fun getDelimiterValue(holder: PsiElement, key: String): String? {
            val list = getStringLiteralsFromInitializerArray(holder, EMPTY_FILTER)
            if (list.size != 2) return null
            val literal = list[if (JSInjectionBracesUtil.START_SYMBOL_KEY == key) 0 else 1] as? JSLiteralExpression ?: return null
            return es6Unquote(literal.significantValue!!)
        }
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val project = context.project
        if (!hasSeafood(project)) return

        val fileType = context.containingFile?.originalFile?.virtualFile?.fileType
        if (fileType != HtmlFileType.INSTANCE && fileType != SeafoodFileType.INSTANCE) return

        // this supposed to work in <template lang="jade"> attribute values
        if (context is XmlAttributeValueImpl && !context.value.isNullOrBlank() && context.parent is XmlAttribute
//                && SeafoodAttributesProvider.isInjectJS((context.parent as XmlAttribute).name)) {
                ) {
            val embedded = PsiTreeUtil.getChildOfType(context, JSEmbeddedContent::class.java)
            if (embedded != null && SeafoodInjectionLanguage.INSTANCE != embedded.language) {
                val literal = PsiTreeUtil.getChildOfType(embedded, JSLiteralExpressionImpl::class.java)
                if (literal != null) {
                    injectInElement(literal, registrar)
                    return
                }
            }
            else if (embedded == null) {
                injectInElement(context, registrar)
            }
        }

        if (context is XmlTextImpl || context is XmlAttributeValueImpl) {
            val braces = BRACES_FACTORY.`fun`(context) ?: return
            injectInXmlTextByDelimiters(registrar, context, SeafoodInjectionLanguage.INSTANCE, braces.getFirst(), braces.getSecond())
        }
    }

    private fun injectInElement(host: PsiLanguageInjectionHost, registrar: MultiHostRegistrar) {
        registrar.startInjecting(SeafoodInjectionLanguage.INSTANCE)
                .addPlace(null, null, host, ElementManipulators.getValueTextRange(host))
                .doneInjecting()
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return Arrays.asList<Class<out XmlElementImpl>>(XmlTextImpl::class.java, XmlAttributeValueImpl::class.java)
    }
}