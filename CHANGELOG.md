# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-01-20

### Added
- Endpoint to get groups for a user: `GET /api/v1/users/{userName}/groups`
- Endpoint to get parent groups for a group: `GET /api/v1/groups/{groupName}/parents`
- Unit tests for UserGroupController
- Unit tests for UserGroupService

## [1.0.0] - 2026-01-20

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
