package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.AddAthleteRequest
import com.vasilisneo.trackstar.data.api.AthleteNotesDto
import com.vasilisneo.trackstar.data.api.AthleteSummaryResponse
import com.vasilisneo.trackstar.data.api.CoachInviteResponse
import com.vasilisneo.trackstar.data.api.InviteValidationResponse
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.cachePeek
import com.vasilisneo.trackstar.data.local.cachedRead

// Coach-side reads over /api/coach/... — the roster plus each athlete's plan and sessions. Reads are
// cache-then-network so the roster and athlete detail render offline. Write/invite operations stay
// online-only until the Phase 3 outbox.
// `open` (class + the roster/plan/session/remove methods) so a test fake can override them when
// constructor-injected into a view model.
open class AthleteRepository {
    private val api = NetworkClient.athleteApi

    open suspend fun getAthletes(): ApiResult<List<ProfileResponse>> =
        cachedRead("roster") { apiCall { api.getAthletes() } }

    open suspend fun getAthleteSummaries(weekIdentifier: String, weekStart: String): ApiResult<List<AthleteSummaryResponse>> =
        cachedRead("athleteSummaries:$weekIdentifier") { apiCall { api.getAthleteSummaries(weekIdentifier, weekStart) } }

    open suspend fun cachedAthleteSummaries(weekIdentifier: String): List<AthleteSummaryResponse>? =
        cachePeek("athleteSummaries:$weekIdentifier")

    suspend fun getAthlete(athleteId: String): ApiResult<ProfileResponse> =
        cachedRead("athlete:$athleteId") { apiCall { api.getAthlete(athleteId) } }

    open suspend fun getAthletePlan(athleteId: String, weekIdentifier: String): ApiResult<List<PlannedSessionResponse>> =
        cachedRead("athletePlan:$athleteId:$weekIdentifier") { apiCall { api.getAthletePlan(athleteId, weekIdentifier) } }

    open suspend fun getAthleteSessions(athleteId: String): ApiResult<List<WorkoutSessionResponse>> =
        cachedRead("athleteSessions:$athleteId") { apiCall { api.getAthleteSessions(athleteId) } }

    // Cache-only peeks (no network) for painting the roster + weekly pills + athlete detail instantly.
    open suspend fun cachedRoster(): List<ProfileResponse>? = cachePeek("roster")

    open suspend fun cachedAthlete(athleteId: String): ProfileResponse? = cachePeek("athlete:$athleteId")

    open suspend fun cachedAthletePlan(athleteId: String, weekIdentifier: String): List<PlannedSessionResponse>? =
        cachePeek("athletePlan:$athleteId:$weekIdentifier")

    open suspend fun cachedAthleteSessions(athleteId: String): List<WorkoutSessionResponse>? =
        cachePeek("athleteSessions:$athleteId")

    open suspend fun cachedAthleteNotes(athleteId: String): AthleteNotesDto? = cachePeek("athleteNotes:$athleteId")

    open suspend fun cachedMyCoach(): ProfileResponse? = cachePeek("myCoach")

    suspend fun getAthleteNotes(athleteId: String): ApiResult<AthleteNotesDto> =
        cachedRead("athleteNotes:$athleteId") { apiCall { api.getAthleteNotes(athleteId) } }

    suspend fun saveAthleteNotes(athleteId: String, notes: AthleteNotesDto): ApiResult<AthleteNotesDto> =
        apiCall { api.saveAthleteNotes(athleteId, notes) }

    open suspend fun removeAthlete(athleteId: String): ApiResult<MessageResponse> =
        apiCall { api.removeAthlete(athleteId) }

    suspend fun addAthlete(email: String, useBronzeGrant: Boolean = false): ApiResult<ProfileResponse> =
        apiCall { api.addAthlete(AddAthleteRequest(email = email, useBronzeGrant = useBronzeGrant)) }

    suspend fun getBronzeGrants(): ApiResult<List<com.vasilisneo.trackstar.data.api.BronzeGrantResponse>> =
        apiCall { api.getBronzeGrants() }

    suspend fun revokeBronzeGrant(athleteId: String): ApiResult<MessageResponse> =
        apiCall { api.revokeBronzeGrant(athleteId) }

    suspend fun createInvite(): ApiResult<CoachInviteResponse> =
        apiCall { api.createInvite() }

    suspend fun updateInviteGrantBronze(token: String, grant: Boolean): ApiResult<Map<String, Boolean>> =
        apiCall { api.updateInviteGrantBronze(token, mapOf("grantBronze" to grant)) }

    // Athlete side: the linked coach's profile. Errors (400 "No coach linked") when unlinked —
    // callers treat that as the empty state rather than a failure. Cache-then-network, but the 400
    // is not an offline error so it won't be masked by a stale cached coach.
    suspend fun getMyCoach(): ApiResult<ProfileResponse> =
        cachedRead("myCoach") { apiCall { api.getMyCoach() } }

    suspend fun validateInvite(token: String): ApiResult<InviteValidationResponse> =
        apiCall { api.validateInvite(token) }

    suspend fun acceptInvite(token: String): ApiResult<MessageResponse> =
        apiCall { api.acceptInvite(token) }
}
