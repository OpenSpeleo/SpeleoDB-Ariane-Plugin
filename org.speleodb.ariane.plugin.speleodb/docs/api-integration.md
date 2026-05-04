# SpeleoDB REST API Integration

## Base Configuration

| Parameter | Value | Source |
|-----------|-------|--------|
| Base URL | `https://www.speleodb.org` (default) | `PREFERENCES.DEFAULT_INSTANCE` |
| API prefix | `/api/v2` | `API.BASE_PATH` |
| Auth scheme | `Token <token>` header | `HEADERS.TOKEN_PREFIX` |
| Content-Type | `application/json` | `HEADERS.APPLICATION_JSON` |
| Connect timeout | 30 seconds | `NETWORK.CONNECT_TIMEOUT_SECONDS` |
| Request timeout | 60 seconds | `NETWORK.REQUEST_TIMEOUT_SECONDS` |
| Download timeout | 120 seconds | `NETWORK.DOWNLOAD_TIMEOUT_SECONDS` |

## URL Resolution

The plugin normalizes user-provided URLs through `resolveInstanceUrl()`:
1. Strip `http://` or `https://` prefix
2. Remove trailing slashes
3. Lowercase (RFC 7230 compliance)
4. Apply protocol: `http://` for local addresses (localhost, 127.x, 10.x, 172.16-31.x, 192.168.x), `https://` for everything else

HTTP version selection: HTTP/1.1 for `http://`, HTTP/2 for `https://`.

## Endpoints

### Authentication

**POST** `/api/v2/user/auth-token/`

Email/password login:
```json
{"email": "user@example.com", "password": "secret"}
```

OAuth token login: GET with `Authorization: Token <oauth_token>` header.

Response: `{"token": "<session_token>"}`

### Project Listing

**GET** `/api/v2/projects/`

Response: `[<project objects>]` (v2 root is the bare array; no `{"data": ...}` wrapper)

Client-side filtering:
- Only `type == "ARIANE"` projects
- Exclude `permission == "WEB_VIEWER"` entries

### Project Creation

**POST** `/api/v2/projects/`

```json
{
  "name": "My Cave",
  "description": "Survey of entrance zone",
  "country": "FR",
  "latitude": "43.1234",
  "longitude": "1.5678",
  "type": "ARIANE"
}
```

Latitude and longitude are optional. The 201 response body is the created project object directly (no `{"data": ...}` wrapper).
Wrapped success payloads such as `{"data": {...}}` are treated as an invalid response shape and rejected.

### Project Upload

**PUT** `/api/v2/projects/{id}/upload/ariane_tml/`

Multipart form data with fields:
- `message`: commit message (text)
- `artifact`: TML file (binary)

Responses:
- `200 OK`: upload successful
- `304 Not Modified`: no changes detected (raises `NotModifiedException`)

Pre-upload validation: SHA-256 hash compared against empty template to reject blank uploads.

### Project Download

**GET** `/api/v2/projects/{id}/download/ariane_tml/`

Responses:
- `200 OK`: binary TML file content
- `422 Unprocessable Entity`: project exists but has no content (empty template created locally)

### Lock Management

**POST** `/api/v2/projects/{id}/acquire/` -- acquire or refresh mutex lock

**POST** `/api/v2/projects/{id}/release/` -- release mutex lock

Both return `200 OK` on success.
Non-200 responses preserve the existing boolean contract (`false`) and surface the parsed server detail through `SpeleoDBLogger.warn(...)`.

### Announcements (Unauthenticated)

**GET** `/api/v2/announcements/`

Response: `[<announcement objects>]` (bare array, no wrapper).

Client-side filtering:
- `is_active == true`
- `software == "ARIANE"`
- `expiracy_date` not expired (or absent)
- `version` matches current plugin version (or absent)

### Plugin Releases (Unauthenticated)

**GET** `/api/v2/plugin_releases/`

Response: `[<release objects>]` (bare array, no wrapper).

Client-side filtering:
- `software == "ARIANE"`
- Current Ariane version within `[min_software_version, max_software_version]` bounds

### Plugin Update Download (Unauthenticated)

`SpeleoDBService.downloadPluginUpdate(String url)` downloads the plugin JAR binary from the
`download_url` returned by the plugin-releases endpoint (typically a non-SpeleoDB host such as
GitHub releases). It builds its own short-lived `HttpClient` via `createHttpClientForInstance()`
(HTTP/2 for `https://`, HTTP/1.1 for `http://`) with `Redirect.NORMAL` and does not require
authentication. URLs are normalized by lowercasing the host (RFC 7230) before the request is issued.

## Response Shapes

**Success bodies are returned as the JSON root.** v2 dropped the v1 `{"data": ..., "success": true, "timestamp": ..., "url": ...}` wrapper. List endpoints emit a bare array; single-resource endpoints emit a bare object.

**Error bodies retain a wrapper**, in one of three shapes:

- `{"error": "<message>"}` -- single-string detail.
- `{"errors": [<entries>]}` -- list of detail entries. Each entry is either:
  - a string, or
  - an object with one of `detail` / `message` / `error` (DRF/REST conventions).
- `{"non_field_errors": [<entries>]}` -- DRF's default cross-field-validation envelope; entries are parsed identically to `errors`.

The plugin parses all three shapes via `SpeleoDBService.parseV2ErrorMessage(String)`. Precedence is `error` > `errors` > `non_field_errors`. Unknown entry shapes fall back to `JsonValue.toString()` so information is never silently dropped. When the body is missing, blank, non-JSON, or has none of the recognized shapes, the parser returns `Optional.empty()` and the exception carries the prefix + status code only.

## Error Handling

All API methods throw exceptions on failure (the lock acquire/release methods log a warning and return `false` instead, preserving their boolean contract). The controller catches and displays errors via `SpeleoDBModals.showError()` or `SpeleoDBTooltips.showError()`. Each exception message is built by `SpeleoDBService.formatStatusError(prefix, statusCode, body)`, which appends the parsed detail (if any) to a status-coded prefix such as `MESSAGES.AUTH_FAILED_STATUS`. For mutex failures, that parsed detail is intentionally emitted to the UI console as part of the warning log so the user still sees the server-side reason.
