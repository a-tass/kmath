/*
 * Copyright  2018 Alexander Nozik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package scientifik.kmath.chains

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow


/**
 * A not-necessary-Markov chain of some type
 * @param R - the chain element type
 */
interface Chain<out R> {
    /**
     * Last cached value of the chain. Returns null if [next] was not called
     */
    fun peek(): R?

    /**
     * Generate next value, changing state if needed
     */
    suspend fun next(): R

    /**
     * Create a copy of current chain state. Consuming resulting chain does not affect initial chain
     */
    fun fork(): Chain<R>

}

/**
 * Chain as a coroutine flow. The flow emit affects chain state and vice versa
 */
@FlowPreview
val <R> Chain<R>.flow: Flow<R>
    get() = kotlinx.coroutines.flow.flow { while (true) emit(next()) }

fun <T> Iterator<T>.asChain(): Chain<T> = SimpleChain { next() }
fun <T> Sequence<T>.asChain(): Chain<T> = iterator().asChain()

/**
 * Map the chain result using suspended transformation. Initial chain result can no longer be safely consumed
 * since mapped chain consumes tokens. Accepts regular transformation function
 */
fun <T, R> Chain<T>.map(func: (T) -> R): Chain<R> {
    val parent = this;
    return object : Chain<R> {
        override fun peek(): R? = parent.peek()?.let(func)

        override suspend fun next(): R {
            return func(parent.next())
        }

        override fun fork(): Chain<R> {
            return parent.fork().map(func)
        }
    }
}

/**
 * A simple chain of independent tokens
 */
class SimpleChain<out R>(private val gen: suspend () -> R) : Chain<R> {
    private val atomicValue = atomic<R?>(null)
    override fun peek(): R? = atomicValue.value

    override suspend fun next(): R = gen().also { atomicValue.lazySet(it) }

    override fun fork(): Chain<R> = this
}

//TODO force forks on mapping operations?

/**
 * A stateless Markov chain
 */
class MarkovChain<out R : Any>(private val seed: () -> R, private val gen: suspend (R) -> R) :
    Chain<R> {

    constructor(seed: R, gen: suspend (R) -> R) : this({ seed }, gen)

    private val atomicValue = atomic<R?>(null)
    override fun peek(): R = atomicValue.value ?: seed()

    override suspend fun next(): R {
        val newValue = gen(peek())
        atomicValue.lazySet(newValue)
        return peek()
    }

    override fun fork(): Chain<R> {
        return MarkovChain(peek(), gen)
    }
}

/**
 * A chain with possibly mutable state. The state must not be changed outside the chain. Two chins should never share the state
 * @param S - the state of the chain
 */
class StatefulChain<S, out R>(
    private val state: S,
    private val seed: S.() -> R,
    private val gen: suspend S.(R) -> R
) : Chain<R> {

    constructor(state: S, seed: R, gen: suspend S.(R) -> R) : this(state, { seed }, gen)

    private val atomicValue = atomic<R?>(null)
    override fun peek(): R = atomicValue.value ?: seed(state)

    override suspend fun next(): R {
        val newValue = gen(state, peek())
        atomicValue.lazySet(newValue)
        return peek()
    }

    override fun fork(): Chain<R> {
        throw RuntimeException("Fork not supported for stateful chain")
    }
}

/**
 * A chain that repeats the same value
 */
class ConstantChain<out T>(val value: T) : Chain<T> {
    override fun peek(): T? = value

    override suspend fun next(): T = value

    override fun fork(): Chain<T> {
        return this
    }
}