package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.TemplateDto
import com.vasilisneo.trackstar.data.api.TemplateSessionDto
import com.vasilisneo.trackstar.data.api.TemplateSessionSyncRequest
import com.vasilisneo.trackstar.data.api.TemplateSyncRequest
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.cachedRead

// Coach workout-plan templates (/api/coach/templates). Reads are cache-then-network so the
// template list and each template's sessions show offline.
class TemplateRepository {
    private val api = NetworkClient.templateApi

    suspend fun getTemplates(): ApiResult<List<TemplateDto>> =
        cachedRead("templates") { apiCall { api.getTemplates() } }

    suspend fun getTemplateSessions(id: String): ApiResult<List<TemplateSessionDto>> =
        cachedRead("templateSessions:$id") { apiCall { api.getTemplateSessions(id) } }

    suspend fun createTemplate(id: String, name: String): ApiResult<TemplateDto> =
        apiCall { api.createTemplate(TemplateSyncRequest(id, name)) }

    suspend fun renameTemplate(id: String, name: String): ApiResult<TemplateDto> =
        apiCall { api.renameTemplate(id, TemplateSyncRequest(id, name)) }

    suspend fun deleteTemplate(id: String): ApiResult<MessageResponse> =
        apiCall { api.deleteTemplate(id) }

    suspend fun upsertTemplateSession(templateId: String, request: TemplateSessionSyncRequest): ApiResult<TemplateSessionDto> =
        apiCall { api.upsertTemplateSession(templateId, request) }

    suspend fun deleteTemplateSession(templateId: String, sessionId: String): ApiResult<MessageResponse> =
        apiCall { api.deleteTemplateSession(templateId, sessionId) }
}
