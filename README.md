# Psst

A small one-to-one messaging assessment built with Java 21, Spring Boot 3.5.6, Vaadin Flow 24.9.6, Spring Data JPA, and PostgreSQL.

## Run

Start Docker Desktop, then run:

```powershell
docker compose up --build
```

Open http://localhost:8080. The first build downloads Maven and frontend dependencies and can take several minutes. Compose starts PostgreSQL, waits for it to be ready, then starts the app. The named `psst_data` volume keeps messages when containers restart. `docker compose down` stops the app without deleting history. Do **not** use `docker compose down -v` if you want to keep it.

To run code changes directly with Maven against a local PostgreSQL server, stop the Docker app first so port 8080 is free:

```powershell
docker compose stop app
$env:DB_PASSWORD = "your-local-postgres-password" # omit if your local server uses no password
.\mvnw.cmd clean spring-boot:run
```

The Maven run defaults to `localhost:5432/postgres` with user `postgres`, matching the local database shown in DBeaver. Set `DB_URL` and `DB_USER` in the same PowerShell window if your database name or user differs. Flyway creates a separate `psst` schema inside that database, so existing tables in `public` are left alone. The token prints in the Maven console. If a previous frontend install left `node_modules/.bin` empty and Vaadin reports `tsc` is missing, run `npm install` once.

If you run `Application.main()` from IntelliJ, set `DB_PASSWORD=your-local-postgres-password` in **Run > Edit Configurations > Environment variables**. A password set in a PowerShell window is available to Maven started from that window, but IntelliJ's run configuration needs its own value.

Use separate browser profiles or private windows. On Spring Security's generated login page, enter `alice` and click **Send Token**. The app prints the one-time token to its log:

```powershell
docker compose logs app --tail 30
```

Copy the latest token for `alice` into the token page and click **Sign in**. Repeat with `bob` in the other browser profile, then select the other name in the drawer. After both have signed in once, each appears under Contacts. The Conversations section keeps existing threads available when the other person is offline. **Refresh contacts** discovers usernames added after the current page opened. Tokens are single-use and held in memory, so request a new one after a restart.

The local Compose database accepts connections without a password only on its private Docker network; it is not published to the host. The demo serves HTTP, so message transport is **not** protected from a network observer. A real deployment needs HTTPS/WSS with a trusted certificate, database authentication, and `COOKIE_SECURE=true`. No database secret is committed here.

## Notes

- `chat/ui`: the AppLayout chat screen and Spring Security configuration for its generated one-time-token pages.
- `src/main/frontend/styles.css` styles chat; `src/main/resources/org/springframework/security/default-ui.css` styles Spring Security's generated login pages without replacing their forms.
- `chat/service/ChatService.java`: owns contacts, conversations, messages, and in-memory delivery. It checks membership, validates messages, and publishes after commit through `UI.access` and Vaadin Push.
- `chat/service/UserService.java`: normalizes usernames and calls `ChatService` to load or create users for Spring Security.
- `chat/persistence`: four JPA entities and four `JpaRepository` interfaces. There is no SQL embedded in Java.
- `src/main/resources/db/migration`: Flyway creates the schema in V1, renames the participant table in V2, and removes redundant conversation columns in V3. Existing participants and messages stay in place. Hibernate validates the result on startup.

Spring Security authenticates with a one-time token, but this local demo **prints that credential to the application log** instead of delivering it privately to the named user. Anyone who can read the logs can claim an existing username, and new usernames are created on first sign-in. Do not use this demo for secrets. A session signed in as `charlie` is denied access to an Alice/Bob thread at the service boundary, even if it knows the thread ID. The message list renders plain text with Markdown disabled, so `<b>Hello</b>` appears literally. No message bodies are intentionally logged.

## Check

```powershell
.\mvnw.cmd package
docker compose ps
```

Manual browser check:

1. Request tokens for Alice and Bob in separate browser profiles, read each token from the app log, and sign in. Refresh contacts and select each other. Send messages in both directions; the other open screen should update immediately.
2. Refresh both pages, switch threads, and restart the app with `docker compose restart app`. The history and conversation should remain.
3. Send `<b>Hello</b>`; it should display as text. Try a blank or more than 2,000-character message; the service rejects it.
4. Sign in as Charlie in another profile; Charlie cannot see the Alice/Bob thread. Sign out and try reusing the same token; Spring Security rejects it. Request a new Alice token in a fresh profile to demonstrate that log access allows a known username to be claimed.

## Known missing features

1. Group creation
2. Read receipts
3. Delivery acknowledgments
4. Msg notifications
5. End-to-end encryption
6. HTTPS
7. Exposing OTT in logs
