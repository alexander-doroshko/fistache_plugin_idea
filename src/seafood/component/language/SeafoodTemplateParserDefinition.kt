package seafood.component.language

import com.intellij.lang.PsiParser
import com.intellij.lang.javascript.JSFlexAdapter
import com.intellij.lang.javascript.JavascriptParserDefinition
import com.intellij.lang.javascript.settings.JSRootConfiguration
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType

class SeafoodTemplateParserDefinition : JavascriptParserDefinition() {
    override fun createParser(project: Project?): PsiParser {
        return PsiParser { root, builder ->
            SeafoodTemplateParser(builder).parseSeafood(root)
            return@PsiParser builder.treeBuilt
        }
    }

    override fun createLexer(project: Project?): Lexer {
        return JSFlexAdapter(JSRootConfiguration.getInstance(project).languageLevel.dialect.optionHolder)
    }

    override fun getFileNodeType(): IFileElementType {
        return SeafoodElementTypes.FILE
    }
}