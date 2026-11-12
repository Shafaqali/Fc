> **This code is developed by Claude.**

# FarmCare (native Android: Kotlin, Jetpack Compose, Supabase)

## 1. Backend (Supabase)
1. Create a project at supabase.com.
2. SQL editor: run `supabase/migrations/0001_schema.sql`, `0002_security.sql`, `0003_app_support.sql` in order.
3. Authentication > Providers > Email: keep enabled. Create the first user (or use "Create administrator" in the app), then **turn off "Allow new users to sign up"** once the admin exists. Create staff logins in Authentication > Users; the admin then adds them by email in the app (More > Staff).
4. Project Settings > API: copy the Project URL and the **anon/publishable** key. Never put the `service_role` key in the app.

## 2. Configure the app
Copy `local.properties.example` to `local.properties`, set `sdk.dir`, `supabase.url`, `supabase.anonKey`.

## 3. Build
Open the folder in Android Studio (Ladybug or newer, JDK 17+). Let Gradle sync, accept any version suggestions.
- Debug APK: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- Unit tests: `./gradlew test`
- Signed release: `keytool -genkeypair -v -keystore farmcare-release.jks -alias farmcare -keyalg RSA -keysize 2048 -validity 10000`, add the `release.*` lines to `local.properties`, then `./gradlew assembleRelease`. Keep the keystore backed up; losing it blocks updates.
- Play Store bundle: `./gradlew bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab`

## 4. Billing rules
Daily: rate x calendar days, start and end date both counted. Monthly: each month touched is charged rate x covered days / days in that month (full month = exactly the rate). Charges follow ownership periods, so a transfer bills each owner only for their days. A unique index blocks invoicing the same animal and period twice.

## 5. Backup and recovery
Use Supabase daily backups (paid plans) or point-in-time recovery; also export CSVs from Reports regularly. Restore through the Supabase dashboard.

## 6. Offline behaviour
- Every successful refresh saves a snapshot in Room; if the network is down the app opens that snapshot read-only and shows an "Offline" banner with the save time.
- Weights, vaccinations and treatments entered offline go to a Room queue with a unique operation id (`client_op_id`, unique in the database, so retries cannot duplicate). WorkManager uploads them when a connection returns. More > Synchronisation shows waiting/failed items with Retry and Discard.
- Animal edits use a version check; if someone else changed the animal first you get a conflict message instead of an overwrite.
- Creating animals/customers, payments, invoices and edits need a connection.
- Local data sits in the app-private folder (backups disabled). Signing out wipes it (with a warning if unsent changes exist). The database is not additionally encrypted; the login session is (Android Keystore).

## 7. Known gaps
- This code was written without a compiler or device available. Expect to fix a few compile errors on first sync, then run the tests in section 3 and the manual checklist from the spec.
- No photo or document upload (Supabase Storage) yet.
- Payments are not allocated to individual invoices (balance = charges - payments).
- Per-customer rates and rate effective dates are not modelled in the UI (rate is per animal, species defaults in Settings).
- No instrumented or Compose UI tests; unit tests cover billing and reminder rules only.
- Gradle wrapper jar/scripts are absent: in Android Studio use Gradle 8.9, or run `gradle wrapper` once.
