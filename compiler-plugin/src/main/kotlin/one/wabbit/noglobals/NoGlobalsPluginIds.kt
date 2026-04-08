// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal const val NO_GLOBALS_PLUGIN_ID: String = "one.wabbit.no-globals"
internal const val REQUIRES_GLOBAL_STATE_FQ_NAME: String = "one.wabbit.noglobals.RequiresGlobalState"

internal val REQUIRES_GLOBAL_STATE_CLASS_ID: ClassId =
    ClassId.topLevel(FqName(REQUIRES_GLOBAL_STATE_FQ_NAME))
