/*
 *
 * Copyright 2023 Kevin Hernández
 * Copyright 2012-2023 Terry Yin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kevinah95.klizard_languages

import io.github.kevinah95.FileInfoBuilder

interface CCppCommentsMixin {
    fun getCommentFromToken(token: String): String? {
        if (token.startsWith("/*") || token.startsWith("//")) {
            return token.substring(2)
        }
        return null
    }
}

class CLikeReader : CodeReader(), CCppCommentsMixin {
    override lateinit var context: FileInfoBuilder
    override var ext: MutableList<String> = mutableListOf("c", "cpp", "cc", "mm", "cxx", "h", "hpp")

    val languageNames = listOf("cpp", "c")

    val macroPattern = Regex("""#\s*(\w+)\s*(.*)""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

    init {
        parallelStates = listOf(CLikeStates(context),
                                CLikeNestingStackStates(context),
                                CppRValueRefStates(context))
    }

    override operator fun invoke(_context: FileInfoBuilder) {
        context = _context
    }

    override fun preprocess(tokens: Sequence<String>) = sequence<String> {
        var tilde: Boolean = false

        for (token in tokens) {
            if (token == "~") {
                tilde = true
            } else if (tilde) {
                tilde = false
                yield("~$token")
            } else if (!token.all { it.isWhitespace() } || token == "\n") {
                val macro = macroPattern.find(token)
                if (macro != null) {
                    if (macro.groupValues[1] in listOf("if", "ifdef", "elif")) {
                        context.addCondition()
                    } else if (macro.groupValues[1] == "include") {
                        yield("#include")
                        try {
                            if (macro.groupValues[2] != null) yield(macro.groupValues[2])
                        } catch (_: Exception) {
                            yield("\"\"")
                        }
                        try {
                            for (x in macro.groupValues[2].lines().drop(1)) {
                                yield("\n")
                            }
                        } catch (_: Exception) {
                        }
                    }
                } else {
                    yield(token)
                }
            }
        }
    }
}


class CppRValueRefStates(context: FileInfoBuilder) : CodeStateMachine(context) {

    override val _stateGlobal: (token: String) -> Unit = {
        if (it == "&&") {
            next(_rValueRef)
        } else if (it == "typedef") {
            next(_typedef)
        }
    }

    val _rValueRef = readUntilThen("=;{})") { token: String, _: List<String> ->
        if (token == "=") {
            context.addCondition(-1)
        }
        next(_stateGlobal)
    }


    val _typedef = readUntilThen(";") {_: String, tokens: List<String> ->
        context.addCondition(-tokens.count { it == "&&" })
        next(_stateGlobal)
    }

}

