package seafood.component.codeInsight

import com.intellij.lang.javascript.JSStubElementTypes
import com.intellij.lang.javascript.psi.JSArrayLiteralExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

fun fromAsset(text: String): String {
    val split = es6Unquote(text).split("(?=[A-Z])".toRegex()).filter { it -> !StringUtil.isEmpty(it) }.toTypedArray()
    for (i in split.indices) {
        split[i] = StringUtil.decapitalize(split[i])
    }
    return StringUtil.join(split, "-")
}

fun toAsset(name: String): String {
    val words = name.split("-".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
    for (i in 1 until words.size) {
        words[i] = StringUtil.capitalize(words[i])
    }
    return StringUtil.join(*words)
}

fun getNameVariants(name: String, withKebab: Boolean): Set<String> {
    val camelCaseName = toAsset(name).decapitalize()
    if (withKebab) return setOf(fromAsset(name), camelCaseName, camelCaseName.capitalize())
    return setOf(camelCaseName, camelCaseName.capitalize())
}

private val QUOTES = setOf('\'', '"', '`')
fun es6Unquote(s: String) : String {
    if (s.length < 2) return s
    if (QUOTES.contains(s[0]) && s.endsWith(s[0])) return s.substring(1, s.length - 1)
    return s
}

val EMPTY_FILTER : (String, PsiElement) -> Boolean  = { _, _ -> true}
fun getStringLiteralsFromInitializerArray(holder: PsiElement,
                                          filter: (String, PsiElement) -> Boolean): List<JSLiteralExpression> {
    return JSStubBasedPsiTreeUtil.findDescendants<JSLiteralExpression>(holder,
            TokenSet.create(JSStubElementTypes.LITERAL_EXPRESSION,
                    JSStubElementTypes.STRING_TEMPLATE_EXPRESSION))
            .filter {
                val context = it.context
                !it.significantValue.isNullOrBlank() &&
                        QUOTES.contains(it.significantValue!![0]) &&
                        filter(es6Unquote(it.significantValue!!), it) &&
                        ((context is JSArrayLiteralExpression) && (context.parent == holder) || context == holder)
            }
}

fun getTextIfLiteral(holder: PsiElement?): String? {
    if (holder != null && holder is JSLiteralExpression && holder.isQuotedLiteral) {
        return holder.stringValue
    }
    return null
}

fun detectLanguage(tag: XmlTag?): String? = tag?.getAttribute("lang")?.value?.trim()

fun detectVueScriptLanguage(file: PsiFile): String? {
    val xmlFile = file as? XmlFile ?: return null
    val scriptTag = findScriptTag(xmlFile) ?: return null
    return detectLanguage(scriptTag)
}