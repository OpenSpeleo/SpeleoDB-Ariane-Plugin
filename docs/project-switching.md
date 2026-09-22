# Project creation and switching

Project cards and successful project creation both enter
`SpeleoDBController.selectProject`. Creation has no separate lock-acquisition or
survey-loading path.

## Creation sequence

1. Check Ariane's dirty-state property. If the survey has unsaved edits, show
   the save reminder and stop before displaying the creation dialog.
2. Show `NewProjectDialog`. Cancellation leaves the current project and lock
   intact. Check the dirty state again after the dialog returns.
3. Create the project through the API on the background executor. Creation
   failure leaves the current project and lock intact.
4. Select the returned project through the shared switching path on the FX
   thread, checking unsaved edits again before scheduling the switch.
5. Release the previous lock if one is held and the selected project differs.
   Stop if release fails; do not acquire or download the selected project.
6. Use normal opening behavior: attempt a lock when permissions allow, then
   download and load. A failed acquisition opens the project read-only. The
   download service supplies an empty survey template for an empty project.
7. Recheck unsaved edits immediately before dispatching the host `LOAD` command,
   so edits made during download are not overwritten.

## Host integration and failure boundaries

`SpeleoDBPlugin.getDirtyProperty()` returns one persistent `BooleanProperty` for
Ariane to update. Checks run on the FX thread and depend on the host publishing
its unsaved state. This property describes unsaved survey edits; it does not
independently detect saved local revisions that have not been uploaded.

Creation and switching are separate operations. If creation succeeds but
switching is blocked, the newly created server project remains available.

Lock release precedes downloading and loading the selected project. A download
failure or newly unsaved edits prevent replacing the current survey, but do not
restore the previous lock or roll back a lock acquired for the selected project.

## Regression coverage

Tests live in
`org.speleodb.ariane.plugin.speleodb/src/test/java/org/speleodb/ariane/plugin/speleodb/SpeleoDBProjectOpeningTest.java`.

| Behavior                                                                                                    | Test                                     |
| ----------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| Dialog followed by API creation, previous-lock release, new-lock acquisition, and normal download           | `newProject`                             |
| No release when the previous project is read-only                                                           | `newProject`                             |
| Stable host dirty property across edits and save notifications                                              | `persistentDirtyProperty`                |
| Unsaved edits block both creation and ordinary selection before release                                     | `unsavedChangesBlockReplacement`         |
| Dialog cancellation preserves the current project and lock                                                  | `cancelledCreation`                      |
| API failure, release failure, or edits during the dialog/API operation preserve the current survey and lock | `creationDoesNotReplaceCurrentOnFailure` |
| Download failure or edits during download preserve the current survey and clear the busy state              | `failedDownloadDoesNotReplaceSurvey`     |

These tests exercise the controller with real JavaFX controls, a simulated host,
and mocked service responses. They do not verify a running Ariane editor's
dirty-state binding. The JavaFX test class is skipped when `CI=true` because it
requires a desktop toolkit.

Run the related regression tests and Java lint checks locally with:

```bash
./gradlew :org.speleodb.ariane.plugin.speleodb:test \
  --tests '*SpeleoDBProjectOpeningTest' \
  --tests '*SpeleoDBProjectDownloadApiTest' \
  --tests '*SpeleoDBLockReleaseTest' \
  --tests '*SpeleoDBLockAcquisitionTest' \
  :org.speleodb.ariane.plugin.speleodb:javaLint
```
