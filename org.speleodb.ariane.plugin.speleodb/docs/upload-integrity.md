# TML upload integrity

## Guarantee

Every service upload captures the survey into a private temporary snapshot,
closes the writer, validates the snapshot, and constructs the multipart artifact
from its frozen bytes. Validation, SHA-256, the empty-template check, and the
HTTP artifact all refer to the same content. The live survey and the shared
SpeleoDB project file are never overwritten as upload staging.

This closes the race left by the earlier fixes:

- [b17819f](https://github.com/OpenSpeleo/org.speleodb.ariane.plugin.speleodb/commit/b17819f50c20119df0f22350932d157b77b795e2)
  waited for a readable ZIP on the live path.
- [f6b3506](https://github.com/OpenSpeleo/org.speleodb.ariane.plugin.speleodb/commit/f6b35060cfcd8426dfc6ca653ed15c72a03df3b9)
  added entry reads and exponential backoff, but still reread the live path later.
  Its claim that `ZipFile` automatically verifies CRC32 was incorrect.

A previous complete ZIP could pass validation, then be truncated by another
asynchronous save before multipart construction. Increasing the delay or
validating that same live path again does not eliminate that race.

## Save dispatch and freshness

One action is invoked on the FX thread: the platform/shortcut save accelerator,
otherwise an enabled `#saveButton`, otherwise the `SAVE` command property.
A missing action permits fallback; an invoked action that fails does not. Repeated
command requests rearm the property through `DONE` before setting `SAVE` again.
`DONE` is a command reset, not an acknowledgement of disk completion.

The controller waits at most 10 seconds for dispatch and cancels a still-queued
request on timeout or interruption. Dispatch completion only means the host
handler was invoked. The checked-in host API has no ZIP-close acknowledgement.
A 250 ms quiet period and source identity/size/mtime comparisons improve capture
timing, but cannot prove that the latest in-memory edits have reached disk.
The integrity guarantee applies to the captured bytes, not save freshness or
full TML/XML semantic validity across every possible ZIP implementation.

## Preparation and limits

`TmlUploadPreparer` uses a private temporary directory and never extracts entries.
It rejects archives over 150 MiB compressed, checking both initial size and bytes
copied so a growing source cannot bypass the limit. This matches the backend's
checked-in default; deployments with higher backend limits still use this client
limit (`SpeleoDBConstants.UPLOAD.MAX_COMPRESSED_BYTES`). Multipart remains buffered
in memory, so peak heap use exceeds the compressed file size.

Validation requires a central directory and at least one non-directory entry.
Every entry is read with a fixed-size buffer and checked for decompressed length
and an explicitly calculated CRC32. Duplicate names, encrypted/unsupported
entries, truncated data, and invalid checksums are rejected. A second
`ZipInputStream` pass checks local entries/data descriptors against the directory
view; a streaming reader alone would accept some unfinished ZIPs without their
central directory.

A monotonic, cooperative 10-second preparation budget covers quiet periods,
copying, decompression, and retries. Reads check interruption and deadlines between
chunks; an OS-level blocked read is not forcibly interruptible. Retry delays
start at 50 ms and double up to 1 second. Missing files, changing captures, and
invalid ZIPs can be retried while an asynchronous save finishes. Permanent access
failures and size-limit errors fail immediately. Temporary files are removed on
success, retry, timeout, or interruption.

`SpeleoDBService.uploadProject(message, project)` remains supported and uses the
canonical project path. The source-path overload uses exactly the same validation.
There is no unvalidated service upload path. Empty-template resource failures also
fail closed. No HTTP retry is added; 200, 304, and server error handling remain as
before.

A controller guard suppresses overlapping uploads. Project/source or authentication
session changes observed before sending cancel the attempt. Cleanup releases the
guard and restores controls on success and failure. Failure preserves the commit
message and the local survey.

The upload captures the active survey identity and original path when requested,
then rechecks them on FX immediately before saving. A survey replacement while
the save is queued cancels the upload instead of sending the new survey to the
previous project. A Save As path change during dispatch is permitted only while
the survey identity stays the same; preparation also checks that identity and the
resulting path before sending.

Reload Project reads the active survey path, including a Save As location, so it
does not revert to the older canonical project file after a successful upload.
The canonical path is a fallback only when no active path is set; a missing active
file produces an error. Reload captures the project/session/survey context and
rechecks it immediately before the FX LOAD command. Loading completion is polled
only after FX dispatch succeeds. Reload retains its commit message and keeps controls
disabled until completion; rejected dispatch and executor failures release controls
without clearing the message. Success clears the message only if the same project,
session, and source are still active when the FX completion callback runs.

## Verification and troubleshooting

The regression suite is Java-only; no Python runtime or CI step is required.
`TmlUploadPreparerTest` exercises delayed finalization, rewrites during capture,
growth beyond the limit, malformed ZIPs, CRC-only corruption, and cleanup.
`TmlUploadBoundaryTest` captures real multipart requests with WireMock, compares
binary artifact bytes and SHA-256, and independently reads the received artifact
using `ZipFile` with explicit CRC/size assertions and `ZipInputStream`. A source
truncated after preparation must not change the uploaded artifact. Invalid input
must cause zero HTTP uploads. Save-dispatch and lifecycle tests cover single-save
selection, duplicate requests, timeout, interruption, and context changes.

Debug logs in `~/.ariane/speleodb/logs/speleodb-plugin.log` record attempt ID,
project, plugin/JVM version, save mechanism, validation attempts, snapshot size,
SHA-256, entry count, elapsed time, and HTTP status. Archive contents, credentials,
and commit text are not logged by this pipeline.

If a fixed client still receives `BadZipFile`, identify the installed build and
correlate the received artifact's size/hash with the logged snapshot. Compare the
multipart artifact rather than hashing the entire HTTP body. Matching bytes shift
the investigation toward parser compatibility or server-side artifact handling;
different bytes point toward multipart extraction or intermediary handling.
Do not retry the same bad HTTP payload automatically.

Before release, exercise real Ariane saves on macOS and Windows: repeated saves,
a large survey, saving while an upload is prepared, source-path changes, and a
no-change upload returning 304. The local dev container only displays save
requests and cannot establish real host save behavior.
