# Changelog

The most important SpeleoDB Ariane Plugin updates are listed here. This is a
curated, high-level release history; routine maintenance and implementation
details are intentionally omitted.

## Unreleased

### Features

### UI/UX

### Performance

### Fixes

- Creating a project now checks for unsaved changes and releases the previous
  project's lock before switching. `f7aea97`

## v2026.09.16

### UI/UX

- Read-only projects now open their project tab with disabled actions, a
  forbidden icon, and a clear modification warning. `7dc5b9e`

### Fixes

- Prevent intermittent upload failures caused by incomplete survey files.
  `32e49b6`
- Project reloads keep controls disabled until loading finishes and preserve
  your change description if loading fails. `648fd68`
- New projects now open their project tab without the “No Data to display”
  warning. `c5b44ad`

## v2026.04.19

- Transition to SpeleoDB API v2

## v2026.04.15

### Fixes

- Race Condition Fix on TML upload causing random upload errors

## v2026.03.31

### UI/UX

- Better error messages

### Fixes

- Project Import Sequence Fixed
- Project Upload Message sanitized

## v2026.03.25

### Performanc

- Faster dialog display on macOS

### Fixes

- Project lock not released on disconnect
- Fixed random crash during project switch.
- Upload messages with extra whitespace
- [Race Condition Fix] Flaky "Import from Disk" flow*

## v2026.03.07

### Build

- Upgrade to Gradle 9.0

### Fixes

- Java doesn't respect RFC 2616/7230

## v2026.01.26

### Fixes

- Race Condition: FXML initialization and access
- Avoid releasing the project lock on Ariane exception

## v2026.12.25

### Fixes

- Race Condition: Project Uploaded executed before it was finished to be written
  on disk
- Projects with `WEB_VIEWER` permission were being displayed in the listing but
  couldn't be opened.

## v2026.12.17

### UI/UX

- Country Dropdown now searchable during project creation

## v2026.12.01

### UI/UX

- Support for project filtering based on survey software

## v2026.11.02

### UI/UX

- Automatic logging if the user has set their credentials.

### Fixes

- Upload spinner not clearing up after invalid upload

## v2026.10.26

### UI/UX

- Force centering the map viewer being triggered on project load

### Fixes

- Force REDRAW being triggered on project load

## v2026.09.05

### Fixes

- Ariane did not save if only comments were modified. Workaround added
- SpeleoDB was silently skipping if a project was uploaded but not modified. Now
  shows an explicit error.

## v2026.09.02

### Fixes

- Automatic Lock release on Windows: Fixed
- Project save path now living under `.ariane/speleodb/ariane`

## v2026.09.01

### Performance

- Project opening performance optimization

### Fixes

- Overflowing Text on Read Only opening modal
- Could not "switch project" from a Read Only project

## v2026.08.31

### UI/UX

- Upload from disk / local computer now supported

### Fixes

- Empty uploads are now blocked
- General improvements and stability

## v2026.08.13

### UI/UX

- Streamline the lock/unlock experience
- Success animation is now optional

### Fixes

- Fix random crashes at closing, (un)lock and project opening

## v2026.08.13

**Ariane - Initial Release 25.2.2 - First official release**
