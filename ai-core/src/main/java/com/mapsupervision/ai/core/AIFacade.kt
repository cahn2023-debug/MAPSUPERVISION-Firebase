package com.mapsupervision.ai.core

interface AIFacade {
    suspend fun <T : AiResult> execute(payload: AiPayload): AiDecision<T>
}
