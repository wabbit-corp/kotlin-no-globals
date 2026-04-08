// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)

package one.wabbit.noglobals

import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPropertyAccessor

internal class NoGlobalsCheckersExtension(
    session: FirSession,
    private val pluginConfiguration: NoGlobalsConfiguration,
) : FirAdditionalCheckersExtension(session) {
    private val regularClassChecker =
        object : FirRegularClassChecker(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirRegularClass) {
                val violation = declaration.globalMutableStateViolation(session, pluginConfiguration) ?: return
                if (declaration.hasRequiresGlobalStateMarker(session)) {
                    return
                }
                val source = declaration.source.realOrNull() ?: return
                with(context) {
                    reporter.reportOn(
                        source,
                        NoGlobalsErrors.GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN,
                        violation.render(),
                    )
                }
            }
        }

    private val propertyChecker =
        object : FirPropertyChecker(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirProperty) {
                val violation = declaration.globalMutableStateViolation(session, pluginConfiguration, context) ?: return
                if (declaration.hasRequiresGlobalStateMarker(session)) {
                    return
                }
                val source = declaration.source.realOrNull() ?: return
                with(context) {
                    reporter.reportOn(
                        source,
                        NoGlobalsErrors.GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN,
                        violation.render(),
                    )
                }
            }
        }

    override val declarationCheckers: DeclarationCheckers =
        object : DeclarationCheckers() {
            override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(regularClassChecker)
            override val propertyCheckers: Set<FirPropertyChecker> = setOf(propertyChecker)
        }
}

private data class GlobalMutableStateViolation(
    val scope: GlobalMutableStateScope,
    val category: GlobalMutableStateCategory,
) {
    fun render(): String =
        when (category) {
            is GlobalMutableStateCategory.Var ->
                buildString {
                    if (category.isLateinit) {
                        append("lateinit ")
                    }
                    append(scope.varDescription())
                }
            is GlobalMutableStateCategory.BlacklistedType ->
                "${scope.valDescription()} of blacklisted mutable type ${category.typeName}"
            is GlobalMutableStateCategory.SingletonCarrier ->
                "object singleton of blacklisted mutable type ${category.typeName}"
            GlobalMutableStateCategory.AnonymousObjectHolder ->
                "${scope.valDescription()} holding an anonymous object with mutable members"
        }
}

private enum class GlobalMutableStateScope {
    TOP_LEVEL,
    OBJECT_SINGLETON,
    ENUM_CLASS,
    ENUM_ENTRY,
    ;

    fun varDescription(): String =
        when (this) {
            TOP_LEVEL -> "top-level var"
            OBJECT_SINGLETON -> "var declared in an object singleton"
            ENUM_CLASS -> "var declared in an enum class"
            ENUM_ENTRY -> "var declared in an enum entry"
        }

    fun valDescription(): String =
        when (this) {
            TOP_LEVEL -> "top-level val"
            OBJECT_SINGLETON -> "val declared in an object singleton"
            ENUM_CLASS -> "val declared in an enum class"
            ENUM_ENTRY -> "val declared in an enum entry"
        }
}

private sealed interface GlobalMutableStateCategory {
    data class Var(
        val isLateinit: Boolean,
    ) : GlobalMutableStateCategory

    data class BlacklistedType(
        val typeName: String,
    ) : GlobalMutableStateCategory

    data class SingletonCarrier(
        val typeName: String,
    ) : GlobalMutableStateCategory

    data object AnonymousObjectHolder : GlobalMutableStateCategory
}

private fun FirProperty.globalMutableStateViolation(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
    checkerContext: CheckerContext,
): GlobalMutableStateViolation? {
    val sourceScope = source.realOrNull().globalMutableStateScopeFromPsi() ?: source.globalMutableStateScopeFromPsi()
    val scope = sourceScope ?: checkerContext.globalMutableStateScope()
    if (symbol.isLocal && scope == null) {
        return null
    }
    scope ?: return null
    return globalMutableStateViolation(session, pluginConfiguration, scope)
}

private fun FirProperty.globalMutableStateViolation(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
    scope: GlobalMutableStateScope,
): GlobalMutableStateViolation? {
    val category =
        when {
            isVar -> GlobalMutableStateCategory.Var(isLateinit = status.isLateInit)
            holdsAnonymousObjectWithMutableMembers(session, pluginConfiguration) ->
                GlobalMutableStateCategory.AnonymousObjectHolder
            isPureComputedVal() -> return null
            else ->
                blacklistedTypeMatch(session, pluginConfiguration.blacklistedTypes)
                    ?.let(GlobalMutableStateCategory::BlacklistedType)
                    ?: return null
        }

    return GlobalMutableStateViolation(
        scope = scope,
        category = category,
    )
}

private fun FirRegularClass.globalMutableStateViolation(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
): GlobalMutableStateViolation? {
    if (classKind != ClassKind.OBJECT || symbol.classId.isLocal) {
        return null
    }

    val blacklistedType =
        symbol.blacklistedTypeMatch(session, pluginConfiguration.blacklistedTypes)
            ?: return null

    return GlobalMutableStateViolation(
        scope = GlobalMutableStateScope.OBJECT_SINGLETON,
        category = GlobalMutableStateCategory.SingletonCarrier(blacklistedType),
    )
}

private fun FirProperty.blacklistedTypeMatch(
    session: FirSession,
    blacklistedTypes: List<BlacklistedTypePattern>,
): String? {
    val propertyTypeSymbol = returnTypeRef.toClassLikeSymbol(session) as? FirClassSymbol<*> ?: return null
    return propertyTypeSymbol.blacklistedTypeMatch(session, blacklistedTypes)
}

private fun FirProperty.isPureComputedVal(): Boolean =
    !isVar &&
        initializer == null &&
        delegate == null &&
        getter != null

private fun FirProperty.holdsAnonymousObjectWithMutableMembers(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
): Boolean {
    val anonymousObjectExpression = initializer as? FirAnonymousObjectExpression ?: return false
    return anonymousObjectExpression.anonymousObject.containsMutableMembers(session, pluginConfiguration)
}

private fun FirAnonymousObject.containsMutableMembers(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
): Boolean {
    val declaredProperties = symbol.declaredProperties(session)
    return declaredProperties.any { property ->
        property.isVar ||
            property.blacklistedTypeMatch(session, pluginConfiguration.blacklistedTypes) != null ||
            property.holdsAnonymousObjectWithMutableMembers(session, pluginConfiguration)
    }
}

private fun FirPropertySymbol.blacklistedTypeMatch(
    session: FirSession,
    blacklistedTypes: List<BlacklistedTypePattern>,
): String? {
    val propertyTypeSymbol = resolvedReturnTypeRef.toClassLikeSymbol(session) as? FirClassSymbol<*> ?: return null
    return propertyTypeSymbol.blacklistedTypeMatch(session, blacklistedTypes)
}

private fun FirPropertySymbol.holdsAnonymousObjectWithMutableMembers(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
): Boolean {
    val anonymousObjectExpression = resolvedInitializer as? FirAnonymousObjectExpression ?: return false
    return anonymousObjectExpression.anonymousObject.containsMutableMembers(session, pluginConfiguration)
}

private fun CheckerContext.globalMutableStateScope(): GlobalMutableStateScope? {
    // `containingDeclarations` is ordered outermost to innermost, so walk it backwards to classify
    // from the nearest containing declaration instead of mixing independent closest-node queries.
    val declarations = containingDeclarations.asReversed()
    var index = 0
    while (index < declarations.size) {
        val declaration = declarations[index]
        when (declaration) {
            is FirFunctionSymbol<*> -> return null
            is FirPropertySymbol -> return null
            is FirAnonymousObjectSymbol -> {
                var ancestorIndex = index + 1
                while (ancestorIndex < declarations.size) {
                    when (val ancestor = declarations[ancestorIndex]) {
                        is FirFunctionSymbol<*> -> return null
                        is FirPropertySymbol -> return null
                        is FirEnumEntrySymbol -> return GlobalMutableStateScope.ENUM_ENTRY
                        is FirRegularClassSymbol ->
                            return when (ancestor.classKind) {
                                ClassKind.ENUM_ENTRY, ClassKind.ENUM_CLASS -> GlobalMutableStateScope.ENUM_ENTRY
                                else -> null
                            }
                    }
                    ancestorIndex += 1
                }
                return null
            }
            is FirEnumEntrySymbol -> return GlobalMutableStateScope.ENUM_ENTRY
            is FirRegularClassSymbol ->
                return declaration.globalMutableStateScopeFromSource()
        }
        index += 1
    }

    return GlobalMutableStateScope.TOP_LEVEL
}

private fun FirRegularClassSymbol.globalMutableStateScopeFromSource(): GlobalMutableStateScope? {
    val sourceScope = fir.source.realOrNull().globalMutableStateScopeFromPsi() ?: fir.source.globalMutableStateScopeFromPsi()
    return when {
        classKind == ClassKind.OBJECT -> GlobalMutableStateScope.OBJECT_SINGLETON
        classKind == ClassKind.ENUM_CLASS -> GlobalMutableStateScope.ENUM_CLASS
        classKind == ClassKind.ENUM_ENTRY || sourceScope == GlobalMutableStateScope.ENUM_ENTRY ->
            GlobalMutableStateScope.ENUM_ENTRY
        else -> null
    }
}

private fun AbstractKtSourceElement?.globalMutableStateScopeFromPsi(): GlobalMutableStateScope? {
    val sourceElement = this ?: return null
    val psiSource =
        when (sourceElement) {
            is KtPsiSourceElement -> sourceElement
            is KtLightSourceElement -> sourceElement.unwrapToKtPsiSourceElement()
            else -> null
        } ?: return null

    var current = psiSource.psi
    while (current != null) {
        when (current) {
            is KtNamedFunction,
            is KtPropertyAccessor,
            is KtAnonymousInitializer,
            -> return null

            is KtEnumEntry -> return GlobalMutableStateScope.ENUM_ENTRY

            is KtObjectDeclaration ->
                return if (current.isObjectLiteral()) {
                    null
                } else {
                    GlobalMutableStateScope.OBJECT_SINGLETON
                }

            is KtClass ->
                when {
                    current.isEnum() -> return GlobalMutableStateScope.ENUM_CLASS
                    current.parent is KtEnumEntry -> current = current.parent
                    else -> return null
                }
        }
        current = current.parent
    }

    return GlobalMutableStateScope.TOP_LEVEL
}

private fun FirProperty.hasRequiresGlobalStateMarker(session: FirSession): Boolean =
    symbol.resolvedAnnotationsWithClassIds.getAnnotationByClassId(REQUIRES_GLOBAL_STATE_CLASS_ID, session) != null

private fun FirRegularClass.hasRequiresGlobalStateMarker(session: FirSession): Boolean =
    symbol.resolvedAnnotationsWithClassIds.getAnnotationByClassId(REQUIRES_GLOBAL_STATE_CLASS_ID, session) != null

private fun FirClassSymbol<*>.blacklistedTypeMatch(
    session: FirSession,
    blacklistedTypes: List<BlacklistedTypePattern>,
): String? =
    blacklistedTypes.firstNotNullOfOrNull { blacklistedType ->
        blacklistedType.candidateClassIds.firstNotNullOfOrNull { candidateClassId ->
            val blacklistedTypeSymbol = candidateClassId.toSymbol(session) as? FirClassSymbol<*>
            when {
                classId == candidateClassId -> blacklistedType.configuredName
                blacklistedTypeSymbol != null && blacklistedTypeSymbol.isSupertypeOf(this, session) ->
                    blacklistedType.configuredName
                else -> null
            }
        }
    }

private fun AbstractKtSourceElement?.realOrNull(): AbstractKtSourceElement? {
    val source = this ?: return null
    val ktSource = source as? KtSourceElement ?: return source
    return source.takeUnless { ktSource.kind is KtFakeSourceElementKind }
}
