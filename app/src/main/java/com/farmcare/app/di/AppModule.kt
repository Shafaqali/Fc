package com.farmcare.app.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.farmcare.app.BuildConfig
import androidx.room.Room
import com.farmcare.app.data.local.LocalDao
import com.farmcare.app.data.local.LocalDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/** Stores the auth session encrypted (AES-256, keys in Android Keystore). */
class SecureSessionManager(context: Context) : SessionManager {
    private val prefs = EncryptedSharedPreferences.create(
        context, "farmcare_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun saveSession(session: UserSession) { prefs.edit().putString("s", json.encodeToString(UserSession.serializer(), session)).apply() }
    override suspend fun loadSession(): UserSession? = prefs.getString("s", null)?.let { runCatching { json.decodeFromString(UserSession.serializer(), it) }.getOrNull() }
    override suspend fun deleteSession() { prefs.edit().remove("s").apply() }
}

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun supabase(@ApplicationContext ctx: Context): SupabaseClient = createSupabaseClient(
        BuildConfig.SUPABASE_URL.ifBlank { "https://not-configured.invalid" },
        BuildConfig.SUPABASE_ANON_KEY.ifBlank { "not-configured" }) {
        install(Auth) { sessionManager = SecureSessionManager(ctx) }
        install(Postgrest)
    }

    @Provides @Singleton fun db(@ApplicationContext ctx: Context): LocalDb = Room.databaseBuilder(ctx, LocalDb::class.java, "farmcare.db").build()
    @Provides fun dao(db: LocalDb): LocalDao = db.dao()
}
