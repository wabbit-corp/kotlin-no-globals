// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers

internal object NoGlobalsErrors : KtDiagnosticsContainer() {
    val GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN =
        KtDiagnosticFactory1<String>(
            "GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN",
            Severity.ERROR,
            SourceElementPositioningStrategies.DEFAULT,
            PsiElement::class,
            NoGlobalsErrorMessages,
        )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = NoGlobalsErrorMessages
}

internal object NoGlobalsErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("NoGlobalsErrors") { map ->
        map.put(
            NoGlobalsErrors.GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN,
            "Global mutable state detected ({0}). Annotate this declaration with @RequiresGlobalState.",
            CommonRenderers.STRING,
        )
    }
}
