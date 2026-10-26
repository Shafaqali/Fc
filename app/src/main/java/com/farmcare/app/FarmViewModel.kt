package com.farmcare.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmcare.app.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FarmUi(val loading: Boolean = true, val error: String? = null, val membership: Membership? = null,
                  val needsSetup: Boolean = false, val d: FarmData = FarmData(),
                  val offline: Boolean = false, val savedAt: Long = 0)

@HiltViewModel
class AuthViewModel @Inject constructor(val repo: FarmRepository) : ViewModel() {
    val status: StateFlow<SessionStatus> = repo.sessionStatus.stateIn(viewModelScope, SharingStarted.Eagerly, SessionStatus.Initializing)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun submit(block: suspend () -> String?) = viewModelScope.launch {
        busy.value = true; message.value = null
        message.value = try { block() } catch (e: Exception) { friendly(e) }
        busy.value = false
    }
    fun signIn(e: String, p: String) = submit { repo.signIn(e, p); null }
    fun signUp(e: String, p: String) = submit { if (repo.signUp(e, p)) null else "Account created. Confirm the email we sent, then sign in." }
    fun reset(e: String) = submit { repo.resetPassword(e); "If that email has an account, a reset link has been sent." }
    fun signOut() = viewModelScope.launch { runCatching { repo.signOut() }; runCatching { repo.wipeLocal() } }
    private fun friendly(e: Exception) = when {
        e.message?.contains("Invalid login", true) == true -> "Incorrect email or password."
        e.message?.contains("Unable to resolve", true) == true || e is java.io.IOException -> "No internet connection. Sign-in needs connectivity."
        else -> e.message ?: "Something went wrong."
    }
}

@HiltViewModel
class FarmViewModel @Inject constructor(private val repo: FarmRepository) : ViewModel() {
    private val _s = MutableStateFlow(FarmUi())
    val s = _s.asStateFlow()
    val pending = repo.dao.observeOps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load() = viewModelScope.launch {
        _s.update { it.copy(loading = true, error = null) }
        try {
            val m = repo.membership()
            if (m == null) _s.value = FarmUi(loading = false, needsSetup = true)
            else { val d = repo.loadAll(m); _s.value = FarmUi(false, null, m, false, d); runCatching { repo.saveSnapshot(m, d) } }
        } catch (e: Exception) {
            val snap = if (repo.isNetwork(e)) repo.loadSnapshot() else null
            if (snap != null) _s.value = FarmUi(false, null, snap.first.membership, false, snap.first.data, offline = true, savedAt = snap.second)
            else _s.update { it.copy(loading = false, error = if (repo.isNetwork(e)) "No connection and no saved data on this device yet. Sign in once while online." else e.message) }
        }
    }
    fun syncNow(onDone: (String) -> Unit = {}) { viewModelScope.launch {
        val ok = try { repo.syncPending() } catch (_: Exception) { false }
        load().join(); onDone(if (ok) "All changes are synchronised." else "Still offline or the server is unreachable. Changes stay queued.") } }
    fun retryOp(id: String) { viewModelScope.launch { repo.dao.mark(id, "pending", null); syncNow() } }
    fun discardOp(id: String) { viewModelScope.launch { repo.dao.delete(id) } }
    fun clear() { _s.value = FarmUi() }

    /** Runs a repository action, reloads data on success, and reports an error message (or null) to the caller. */
    fun run(onDone: (String?) -> Unit = {}, block: suspend FarmRepository.() -> Unit) {
        viewModelScope.launch {
            val err = try { repo.block(); null }
                catch (e: FarmRepository.QueuedException) { null }   // stored locally; shown in the sync banner until uploaded
                catch (e: Exception) { if (repo.isNetwork(e)) "No connection. This action needs internet." else e.message ?: "Action failed" }
            if (err == null) {
                try { _s.value.membership?.let { m -> _s.update { it.copy(d = repo.loadAll(m)) } } } catch (_: Exception) {}
            }
            onDone(err)
        }
    }
    fun setup(name: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        val err = try { repo.bootstrapFarm(name); null } catch (e: Exception) { e.message }
        if (err == null) load()
        onDone(err)
    }
    fun resolveQr(token: String, onDone: (animalId: String?, error: String?) -> Unit) = viewModelScope.launch {
        try { val id = repo.resolveQr(token); onDone(id, if (id == null) "This tag is unknown or has been revoked." else null) }
        catch (e: Exception) { onDone(null, "Could not verify the tag. Check your connection.") }
    }
}
