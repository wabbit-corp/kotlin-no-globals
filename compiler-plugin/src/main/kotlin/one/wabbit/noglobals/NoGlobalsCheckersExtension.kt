package one.wabbit.noglobals

import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.findClosestClassOrObject
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.findClosest
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId

internal class NoGlobalsCheckersExtension(
    session: FirSession,
    private val pluginConfiguration: NoGlobalsConfiguration,
) : FirAdditionalCheckersExtension(session) {
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

    data object AnonymousObjectHolder : GlobalMutableStateCategory
}

private fun FirProperty.globalMutableStateViolation(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
    checkerContext: CheckerContext,
): GlobalMutableStateViolation? {
    if (symbol.isLocal) {
        return null
    }

    val scope = checkerContext.globalMutableStateScope() ?: return null
    val category =
        when {
            isVar -> GlobalMutableStateCategory.Var(isLateinit = status.isLateInit)
            holdsAnonymousObjectWithMutableMembers(session, pluginConfiguration) ->
                GlobalMutableStateCategory.AnonymousObjectHolder
            else ->
                blacklistedTypeMatch(session, pluginConfiguration.blacklistedTypeClassIds)
                    ?.let(GlobalMutableStateCategory::BlacklistedType)
                    ?: return null
        }

    return GlobalMutableStateViolation(
        scope = scope,
        category = category,
    )
}

private fun FirProperty.blacklistedTypeMatch(
    session: FirSession,
    blacklistedTypeClassIds: List<ClassId>,
): String? {
    val propertyTypeSymbol = returnTypeRef.toClassLikeSymbol(session) as? FirClassSymbol<*> ?: return null
    return blacklistedTypeClassIds.firstNotNullOfOrNull { blacklistedTypeClassId ->
        val blacklistedTypeSymbol = blacklistedTypeClassId.toSymbol(session) as? FirClassSymbol<*>
        when {
            propertyTypeSymbol.classId == blacklistedTypeClassId -> blacklistedTypeClassId.asSingleFqName().asString()
            blacklistedTypeSymbol != null && blacklistedTypeSymbol.isSupertypeOf(propertyTypeSymbol, session) ->
                blacklistedTypeClassId.asSingleFqName().asString()
            else -> null
        }
    }
}

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
    var containsMutableMembers = false
    processAllDeclarations(session) { member ->
        val property = member as? FirPropertySymbol ?: return@processAllDeclarations
        if (
            property.isVar ||
            property.blacklistedTypeMatch(session, pluginConfiguration.blacklistedTypeClassIds) != null ||
            property.holdsAnonymousObjectWithMutableMembers(session, pluginConfiguration)
        ) {
            containsMutableMembers = true
        }
    }
    return containsMutableMembers
}

private fun FirPropertySymbol.blacklistedTypeMatch(
    session: FirSession,
    blacklistedTypeClassIds: List<ClassId>,
): String? {
    val propertyTypeSymbol = resolvedReturnTypeRef.toClassLikeSymbol(session) as? FirClassSymbol<*> ?: return null
    return blacklistedTypeClassIds.firstNotNullOfOrNull { blacklistedTypeClassId ->
        val blacklistedTypeSymbol = blacklistedTypeClassId.toSymbol(session) as? FirClassSymbol<*>
        when {
            propertyTypeSymbol.classId == blacklistedTypeClassId -> blacklistedTypeClassId.asSingleFqName().asString()
            blacklistedTypeSymbol != null && blacklistedTypeSymbol.isSupertypeOf(propertyTypeSymbol, session) ->
                blacklistedTypeClassId.asSingleFqName().asString()
            else -> null
        }
    }
}

private fun FirPropertySymbol.holdsAnonymousObjectWithMutableMembers(
    session: FirSession,
    pluginConfiguration: NoGlobalsConfiguration,
): Boolean {
    val anonymousObjectExpression = resolvedInitializer as? FirAnonymousObjectExpression ?: return false
    return anonymousObjectExpression.anonymousObject.containsMutableMembers(session, pluginConfiguration)
}

private fun CheckerContext.globalMutableStateScope(): GlobalMutableStateScope? {
    if (findClosest<FirEnumEntrySymbol>() != null) {
        return GlobalMutableStateScope.ENUM_ENTRY
    }

    return when (val nearestClassOrObject = findClosestClassOrObject()) {
        null -> GlobalMutableStateScope.TOP_LEVEL
        is FirAnonymousObjectSymbol -> null
        is FirRegularClassSymbol ->
            when (nearestClassOrObject.classKind) {
                ClassKind.OBJECT -> GlobalMutableStateScope.OBJECT_SINGLETON
                ClassKind.ENUM_CLASS -> GlobalMutableStateScope.ENUM_CLASS
                else -> null
            }
    }
}

private fun FirProperty.hasRequiresGlobalStateMarker(session: FirSession): Boolean =
    symbol.resolvedAnnotationsWithClassIds.getAnnotationByClassId(REQUIRES_GLOBAL_STATE_CLASS_ID, session) != null ||
        setter?.symbol?.resolvedAnnotationsWithClassIds?.getAnnotationByClassId(REQUIRES_GLOBAL_STATE_CLASS_ID, session) != null

private fun AbstractKtSourceElement?.realOrNull(): AbstractKtSourceElement? {
    val source = this ?: return null
    val ktSource = source as? KtSourceElement ?: return source
    return source.takeUnless { ktSource.kind is KtFakeSourceElementKind }
}
