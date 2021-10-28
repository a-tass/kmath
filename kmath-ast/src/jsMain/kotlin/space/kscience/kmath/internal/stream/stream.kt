/*
 * Copyright 2018-2021 KMath contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package space.kscience.kmath.internal.stream

import space.kscience.kmath.internal.emitter.Emitter

internal open external class Stream : Emitter {
    open fun pipe(dest: Any, options: Any): Any
}