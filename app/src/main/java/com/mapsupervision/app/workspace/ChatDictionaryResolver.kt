package com.mapsupervision.app.workspace

import com.mapsupervision.domain.ai.DictionaryMatch
import com.mapsupervision.domain.ai.DictionaryResolverCore
import com.mapsupervision.domain.ai.DictionarySnapshot
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkCategory

data class ChatDictionarySnapshot(
    val projectId: String,
    val nodes: List<GisNode>,
    val routes: List<GisRoute>,
    val workCategories: List<WorkCategory>
)

class ChatDictionaryResolver private constructor(
    private val core: DictionaryResolverCore
) {
    fun resolveNode(raw: String): DictionaryMatch<GisNode>? = core.resolveNode(raw)
    fun resolveRoute(raw: String): DictionaryMatch<GisRoute>? = core.resolveRoute(raw)
    fun resolveCategory(raw: String): DictionaryMatch<WorkCategory>? = core.resolveCategory(raw)
    fun buildCanonicalPromptContext(): String = core.buildCanonicalPromptContext()
    fun buildInputHints(message: String, selectedNodeCode: String?, selectedRouteCode: String?): String =
        core.buildInputHints(message, selectedNodeCode, selectedRouteCode)
    fun canonicalizeMessage(message: String, selectedNodeCode: String?, selectedRouteCode: String?): String =
        core.canonicalizeMessage(message, selectedNodeCode, selectedRouteCode)

    companion object {
        fun from(state: WorkspaceState): ChatDictionaryResolver {
            val snapshot = ChatDictionarySnapshot(
                projectId = state.activeProjectId.orEmpty(),
                nodes = state.designNodes,
                routes = state.designRoutes,
                workCategories = state.workCategories
            )
            return ChatDictionaryResolver(
                DictionaryResolverCore(
                    DictionarySnapshot(
                        projectId = snapshot.projectId,
                        nodes = snapshot.nodes,
                        routes = snapshot.routes,
                        workCategories = snapshot.workCategories
                    )
                )
            )
        }
    }
}
