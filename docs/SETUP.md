# Owner setup

Steps only the repo owner can do, because they need your own accounts. None of this is needed to start Phase 1 except §1.

## 1. Before delegating Phase 1
1. **Default branch:** GitHub → repo → Settings → General → Default branch → set to `main`.
2. **Branch protection (recommended):** Settings → Branches → add a rule for `main`: require a pull request and require status checks to pass (add the CI check after M0 creates it).
3. **Actions:** Settings → Actions → General → allow GitHub Actions. Under "Workflow permissions", choose "Read and write" so the M6 release job can attach the APK.

## 2. Release signing (optional; needed for a stable, upgradable APK)
Without it, CI ships a debug-signed APK. Debug builds from different CI runs may not install over each other.

Generate a keystore **on your own computer** (needs a JDK) and keep it backed up. If you lose it, you can't ship updates that install over the old app.

```bash
keytool -genkeypair -v -keystore release.jks -alias reminder -keyalg RSA -keysize 4096 -validity 10000
```

```bash
base64 -w0 release.jks > release.jks.b64
```

On macOS use `base64 -i release.jks -o release.jks.b64`.

**Local signed build (Windows, no CI):** create the key once, copy `keystore.properties.example` to `keystore.properties` (gitignored) and fill in the four lines, then run `.\gradlew.bat :app:assembleRelease`. The signed APK is `app\build\outputs\apk\release\app-<versionName>.apk`. If `keystore.properties` is missing, the build falls back to the CI secrets below, and then to the debug key.

Add these as repo secrets (Settings → Secrets and variables → Actions → New repository secret):
- `KEYSTORE_BASE64` — contents of `release.jks.b64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS` — `reminder`
- `KEY_PASSWORD`

Never commit `release.jks`; `.gitignore` already blocks it.

## 3. Phase 2 backend (do later, when Phase 2 starts)
Phase 1 has no network access by design. Set these up only when Phase 2 begins; all of them are free with no card.

### Supabase (database, sign-in, photo storage, missed-dose check)
1. Sign up at supabase.com → New project (free plan). Region: closest to the family (e.g., Mumbai `ap-south-1`).
2. Save the database password in a password manager.
3. Settings → API: note the **Project URL** and the **anon public key**. Both are safe to ship in the app; RLS protects the data.
4. Authentication → Providers → enable **Google**. You'll need a Google OAuth client from step 5.
5. Database → Extensions: enable `pg_cron` and `pg_net`.
6. Add repo secrets: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and for deploying migrations from CI, `SUPABASE_ACCESS_TOKEN` + `SUPABASE_DB_PASSWORD`. **Never** put the `service_role` key in the app.

### Google Cloud OAuth client (for Google Sign-In)
1. console.cloud.google.com → create a project (or reuse the Firebase one below).
2. APIs & Services → OAuth consent screen → External → add family members as test users.
3. Credentials → Create OAuth client:
   - **Android** client, with package `app.allinonereminder` and the SHA-1 of your release keystore (`keytool -list -v -keystore release.jks`)
   - **Web** client, whose client ID and secret go into Supabase → Auth → Google

### Firebase (push notifications only — Spark free plan, no card)
1. console.firebase.google.com → Add project → stay on the **Spark** plan. Don't upgrade.
2. Add an Android app with package `app.allinonereminder` → download `google-services.json`. It holds client config, not secrets; Phase 2 commits it to `/app`.
3. Project settings → Service accounts → Generate new private key. This JSON **is** a secret: store it only as a Supabase secret (`supabase secrets set FCM_SERVICE_ACCOUNT=...`) for the Edge Function that sends pushes. Never commit it or put it in the app.

### Free-tier checks
- Supabase pauses projects after about 7 days of inactivity on the free plan. The app's daily heartbeat prevents this; if you get a "project paused" email, restore it from the dashboard.
- Supabase free has no backups; the app's weekly encrypted export covers that.
