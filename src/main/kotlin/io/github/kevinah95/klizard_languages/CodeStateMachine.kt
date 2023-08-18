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

open class CodeStateMachine(val context: FileInfoBuilder) {
    var savedState: (token: String) -> Unit

    var lastToken: String? = null
    var toExit: Boolean = false
    var callback: (() -> Unit)? = null
    var rutTokens: MutableList<String> = mutableListOf()
    var brCount: Int = 0

    var _state: ((token: String) -> Unit)? = null

    open val _stateGlobal: (token: String) -> Unit = {}

    open var commandsByName = listOf(::_stateGlobal).associateBy { it.name }

    init {
        savedState = _stateGlobal
    }

    fun statemachineClone(): CodeStateMachine {
        return CodeStateMachine(this.context)
    }

    fun next(state: ((token: String) -> Unit)? = null, token: String? = null): Boolean? {
        _state = state
        return token?.let { CodeStateMachine(context)(it) }
    }

    fun nextIf(state: ((token: String) -> Unit)? = null, token: String, expected: String) {
        if (token != expected) {
            return
        }
        next(state, token)
    }

    fun statemachineReturn() {
        toExit = true
        statemachineBeforeReturn()
    }

    fun subState(state: ((token: String) -> Unit)? = null, callback: (() -> Unit)? = null, token: String? = null) {
        savedState = _state!!
        this.callback = callback
        next(state, token)
    }

    operator fun invoke(token: String, reader: CodeReader? = null): Boolean? {
        if (_state?.invoke(token) != null) {
            next(savedState)
            if (callback != null) {
                callback?.let { it() }
            }
        }
        lastToken = token
        if (toExit) {
            return true
        }
        // TODO: Verify this
        return null
    }

    fun statemachineBeforeReturn() {}

    // TODO: Add decorators: https://www.reddit.com/r/Kotlin/comments/7f27vb/does_kotlin_have_decorators_similar_to_python/

    fun readUntilThen(tokens: String, function: (String, List<String>) -> Unit): (String) -> Unit {
        fun decorator(func: ((String, List<String>) -> Unit)): (String) -> Unit {
            fun readUntilThenToken(token: String): Unit {

                if (token in tokens){
                    func(token, rutTokens)
                    rutTokens = mutableListOf()
                } else {
                    rutTokens.add(token)
                }
            }
            return ::readUntilThenToken
        }
        return decorator(function)
    }



    // TODO: See example below
    inline fun readInsideBracketsThen(brs: String, endState: String? = null, token: String? = null, func: (String)->Unit) {
        brCount += when (token) {
            brs[0].toString() -> 1
            brs[1].toString() -> -1
            else -> 0
        }

        if (brCount == 0 || endState != null){
            func(token!!)
        }

        if (brCount == 0 && endState != null){
            // TODO: Review this method: https://stackoverflow.com/questions/69622835/how-to-call-a-function-in-kotlin-from-a-string-name
            next(commandsByName[endState]!!.invoke())
        }
    }
}