package com.vasilisneo.trackstar.data.activity

// The user's activity groups, and live sharing: while recording with "Share live" on, the recorder's
// position and stats go to the backend every few seconds (only group members can see them), and the
// other members' live positions come back for the recorder map. Mirrors iOS's Store/GroupStore.swift.

import com.vasilisneo.trackstar.data.api.GroupDetail
import com.vasilisneo.trackstar.data.api.GroupPreview
import com.vasilisneo.trackstar.data.api.GroupSummary
import com.vasilisneo.trackstar.data.api.LiveMember
import com.vasilisneo.trackstar.data.api.LiveUpdate
import com.vasilisneo.trackstar.data.api.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

class GroupException(message: String) : Exception(message)

object GroupStore {
    private val api get() = NetworkClient.groupApi

    private val _groups = MutableStateFlow<List<GroupSummary>>(emptyList())
    val groups: StateFlow<List<GroupSummary>> = _groups
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded
    /** Other members recording right now, across all my groups (for the recorder map). */
    private val _liveMembers = MutableStateFlow<List<LiveMember>>(emptyList())
    val liveMembers: StateFlow<List<LiveMember>> = _liveMembers
    private val _memberNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val memberNames: StateFlow<Map<String, String>> = _memberNames

    private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): T {
        val r = runCatching { block() }.getOrElse { throw GroupException(it.message ?: "network") }
        if (!r.isSuccessful) throw GroupException("HTTP ${r.code()}")
        return r.body() ?: throw GroupException("empty")
    }

    // MARK: Groups

    suspend fun refresh() {
        runCatching { call { api.list() } }.getOrNull()?.let { _groups.value = it }
        _hasLoaded.value = true
    }

    suspend fun create(name: String): GroupSummary = call { api.create(mapOf("name" to name)) }.also { refresh() }

    suspend fun preview(token: String): GroupPreview = call { api.preview(token) }

    suspend fun join(token: String): GroupSummary = call { api.join(token) }.also { refresh() }

    suspend fun detail(id: String): GroupDetail = call { api.detail(id, LocalDate.now().toString()) }

    suspend fun leave(id: String) {
        call { api.leave(id) }
        _groups.value = _groups.value.filter { it.id != id }
    }

    suspend fun rename(id: String, name: String) { call { api.rename(id, mapOf("name" to name)) }; refresh() }

    suspend fun removeMember(groupId: String, memberId: String) { call { api.removeMember(groupId, memberId) } }

    suspend fun resetInvite(id: String): GroupSummary = call { api.resetInvite(id) }.also { refresh() }

    // MARK: Live sharing

    suspend fun sendLive(update: LiveUpdate) { runCatching { api.updateLive(update) } }

    suspend fun stopLive() { runCatching { api.stopLive() } }

    /** Everyone else recording in my groups right now. */
    suspend fun refreshLiveMembers() {
        val groups = _groups.value
        if (groups.isEmpty()) { _liveMembers.value = emptyList(); return }
        val byUser = HashMap<String, LiveMember>()
        val names = HashMap(_memberNames.value)
        for (g in groups) {
            val d = runCatching { detail(g.id) }.getOrNull() ?: continue
            d.members.forEach { names[it.userId] = it.name }
            val me = d.members.firstOrNull { it.isMe }?.userId
            d.live.filter { it.userId != me }.forEach { byUser[it.userId] = it }
        }
        _memberNames.value = names
        _liveMembers.value = byUser.values.toList()
    }

    // MARK: Links

    /** The invite token in trackstar://group/{token} or https://trackstar.fitness/group.html?token={token}. */
    fun token(link: String): String? {
        val uri = runCatching { android.net.Uri.parse(link.trim()) }.getOrNull() ?: return null
        if (uri.scheme.equals("trackstar", true) && uri.host.equals("group", true)) {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        if (uri.host?.lowercase()?.endsWith("trackstar.fitness") == true && (uri.path ?: "").startsWith("/group")) {
            return uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
