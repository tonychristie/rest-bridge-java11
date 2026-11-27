# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-11-27

### Changed
- The bridge listens on `127.0.0.1` only. To accept connections from other hosts, set `--server.address=0.0.0.0` or `SERVER_ADDRESS=0.0.0.0`.
- Each error returns the HTTP status that matches it. An upstream 4xx is returned as it is (a wrong password at connect gives 401), an upstream 5xx or unknown status gives 502, an unreachable endpoint gives 503 and a timed-out call gives 504. Error codes and the error body are unchanged.
- Requests that Spring MVC rejects before the controller runs return 400, 404, 405 or 415 with the bridge error body, instead of 500: a missing or invalid parameter, an unreadable body, an unsupported method or content type, and an unknown path.
- Error responses are always sent as JSON, whatever the `Accept` header says. A request whose `Accept` header refuses JSON returns 406 `NOT_ACCEPTABLE`.
- Calls to REST Services go through `RestTemplate` instead of `WebClient`, and every call uses `rest-bridge.timeout-seconds` as its connect and read timeout, instead of fixed 30 and 60 second timeouts.
- Cabinet and folder listings read every page, so a folder with more than 100 children is no longer cut at 100. Folder contents are paged by `object_name` and `r_object_id`, so each child is returned once, in name order.
- DQL returns at most `maxRows` rows (default 100) starting at `startRow` (default 0), and sets `hasMore` when more rows exist. A `maxRows` below 1 or a negative `startRow` returns 400.
- Name patterns on the user, group and type listings use `*` as the only wildcard, and a pattern without `*` matches the exact name.
- The aggregate check ignores string literals and subqueries, so `GROUP BY` inside a string or a subquery no longer rejects the query.
- Type metadata is cached per session, and only a successful lookup is stored.
- Only a refusal from REST Services marks DQL as unavailable for the session. Any other failure of the DQL check fails that request with `DQL_ERROR`, and the next request runs the check again.
- `GET /api/v1/objects/{id}` returns the status of a failed object read (a 4xx other than 404) instead of 404.
- `GET /api/v1/status` reports the version of the built jar.
- The OpenAPI document lists every status each endpoint returns, and `POST /api/v1/disconnect` no longer documents 404.
- `mvn verify` runs SpotBugs and writes the JaCoCo coverage report.

### Fixed
- Checkin sends the chronicle id in the path, so a checkin after version 1.0 no longer fails with `E_CHECKIN_INVALID_OBJECT_OF_CHRONICLE`. The version label is sent as the `version-label` query parameter, so it is no longer lost.
- Create in a folder given by path finds the folder by name; a missing folder, given by path or by id, returns 404 `OBJECT_NOT_FOUND`. The create endpoint is chosen from the parent chain of the type, so every subtype of `dm_folder` is created as a folder and `dm_cabinet` as a cabinet.
- Group endpoints return `isPrivate`, `isDynamic`, `ownerName`, `groupAddress` and `groupSource`.
- `GET /api/v1/groups/{groupName}` returns the users and groups that are members of the group, from every page.
- `superUser` is read from `user_privileges`.
- An update fails when the object type lookup fails, instead of sending repeating attributes as single values. A missing object returns 404.
- DQL text with `+` or `{...}` reaches REST Services unchanged.
- Permission labels and the DQL aggregate and refusal checks no longer depend on the default locale.
- A connect with an endpoint that is not an absolute http or https URL returns 400 `CONNECTION_ERROR`.
- The DQL state of a session is safely shared between request threads.

### Removed
- The `connected` field from `GET /api/v1/session/{sessionId}`.

## [1.4.0] - 2026-01-25

### Fixed
- `GET /api/v1/types` no longer reads the attributes of every type, so it makes 3 calls to REST Services instead of one for each type.

## [1.3.0] - 2026-01-25

### Added
- Each attribute in an object response carries its `type`, `value` and `repeating` flag.

## [1.2.0] - 2026-01-24

### Added
- `permissionLevel`, `permissionLabel` and `extendedPermissions` in object responses, read in the same batch request as the object

### Changed
- Reduced console output noise: disabled Spring banner, set logging to WARN/INFO levels
- Added log file output to `logs/rest-bridge-java11.log`
- Update, checkout and checkin send the change and the permissions read in one batch request
- `/health` returns `"ok"` instead of `"UP"`

### Fixed
- Type attributes are returned as `isRepeating` and `isInherited`
- Inherited attributes are found by comparing the type with its parent type
- Checkin sends the `object-id` query parameter
- Repeating attributes are sent as JSON arrays in create, update and checkin requests

## [1.1.0] - 2026-01-20

### Added
- Endpoint to get groups for a user: `GET /api/v1/users/{userName}/groups`
- Endpoint to get parent groups for a group: `GET /api/v1/groups/{groupName}/parents`
- Unit tests for UserGroupController
- Unit tests for UserGroupService

## [1.0.0] - 2026-01-19

### Added
- Initial REST Bridge implementation (Java 11 back-port)
- Session management (connect/disconnect)
- DQL query execution with graceful degradation when DQL is disabled
- Object operations (get, update, create, delete, checkout, checkin)
- Cabinet and folder browsing
- Type listing and type info
- User and group operations via native REST endpoints
- Swagger UI and OpenAPI documentation
- Health check and status endpoints
- MIT License
