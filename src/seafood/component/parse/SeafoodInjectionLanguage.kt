package seafood.component.parse

import com.intellij.lang.PsiBuilder
import com.intellij.lang.ecmascript6.parsing.ES6ExpressionParser
import com.intellij.lang.ecmascript6.parsing.ES6FunctionParser
import com.intellij.lang.ecmascript6.parsing.ES6Parser
import com.intellij.lang.ecmascript6.parsing.ES6StatementParser
import com.intellij.lang.javascript.*
import com.intellij.lang.javascript.parsing.JSPsiTypeParser
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.psi.tree.IElementType

class SeafoodInjectionLanguage: JSLanguageDialect("SeafoodInjection", DialectOptionHolder.TS, JavaScriptSupportLoader.TYPESCRIPT) {
    override fun getFileExtension(): String {
        return "ts"
    }

    companion object {
        val INSTANCE: SeafoodInjectionLanguage = SeafoodInjectionLanguage()
    }

    override fun createParser(builder: PsiBuilder): JavaScriptParser<*, *, *, *> {
        return SeafoodInjectionParser(builder)
    }

    class SeafoodInjectionParser(builder: PsiBuilder) : ES6Parser<ES6ExpressionParser<*>, ES6StatementParser<*>, ES6FunctionParser<*>, JSPsiTypeParser<JavaScriptParser<*, *, *, *>>>(builder) {
        init {
            myStatementParser = object: ES6StatementParser<SeafoodInjectionParser>(this) {
                override fun parseSourceElement() {
                    if (builder.currentOffset != 0 || !parseExpectedExpression(builder, false)) {
                        super.parseSourceElement()
                    }
                }
            }
        }

        private fun parseForContents(): Boolean {
            val vForExpr = builder.mark()
            if (builder.tokenType == JSTokenTypes.LPAR) {
                if(!parseForVariables()) {
                    vForExpr.rollbackTo()
                    return false
                }
            } else if (isIdentifierToken(builder.tokenType)) {
                val statement = builder.mark()
                buildTokenElement(JSStubElementTypes.VARIABLE)
                statement.done(JSStubElementTypes.VAR_STATEMENT)
            } else {
                builder.error("identifier(s) expected")
                builder.advanceLexer()
            }
            if (builder.tokenType !== JSTokenTypes.IN_KEYWORD && builder.tokenType !== JSTokenTypes.OF_KEYWORD) {
                vForExpr.rollbackTo()
                return false
            } else {
                builder.advanceLexer()
            }
            if (parseExpectedExpression(builder, true)) {
                vForExpr.done(SeafoodElementTypes.FOR_EXPRESSION)
            }
            else {
                vForExpr.rollbackTo()
                return false
            }
            return true
        }

        private fun parseForVariables(): Boolean {
            val parenthesis = builder.mark()
            builder.advanceLexer() //LPAR
            val varStatement = builder.mark()
            var cnt = 3
            while (isIdentifierToken(builder.tokenType) && cnt > 0) {
                buildTokenElement(JSStubElementTypes.VARIABLE)
                --cnt
                if (cnt == 0 || builder.tokenType != JSTokenTypes.COMMA) break
                else {
                    builder.advanceLexer()
                }
            }
            if (builder.tokenType != JSTokenTypes.RPAR) {
                builder.error("closing parenthesis expected")
                while (!builder.eof() && builder.tokenType != JSTokenTypes.RPAR &&
                        builder.tokenType != JSTokenTypes.IN_KEYWORD &&
                        builder.tokenType != JSTokenTypes.OF_KEYWORD) {
                    builder.advanceLexer()
                }
                if (builder.tokenType != JSTokenTypes.RPAR) {
                    varStatement.done(JSStubElementTypes.VAR_STATEMENT)
                    parenthesis.done(JSElementTypes.PARENTHESIZED_EXPRESSION)
                    return false
                }
            }
            varStatement.done(JSStubElementTypes.VAR_STATEMENT)
            builder.advanceLexer()
            parenthesis.done(JSElementTypes.PARENTHESIZED_EXPRESSION)
            return true
        }

        fun parseSeafood(root: IElementType) {
            val rootMarker = builder.mark()
            while (!builder.eof()) {
                parseExpectedExpression(builder, false)
            }
            rootMarker.done(root)
        }

        private fun parseExpectedExpression(builder: PsiBuilder, isOnlyStandardJS: Boolean) : Boolean {
            if (!isOnlyStandardJS && parseForContents()) return true
            if (!myExpressionParser.parseExpressionOptional()) {
                builder.error(JSBundle.message("javascript.parser.message.expected.expression"))
                builder.advanceLexer()
                return false
            }
            return true
        }
    }
}
