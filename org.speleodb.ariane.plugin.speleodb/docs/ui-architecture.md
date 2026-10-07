# UI Architecture

## FXML Layout

The plugin's main panel is defined in a single FXML file (dialogs are built in
Java): `src/main/resources/fxml/SpeleoDB.fxml`.

The controller (`SpeleoDBController`) is set programmatically via
`FXMLLoader.setController()` rather than declared in FXML, since the controller
is a singleton.

### Integration Modes

The plugin supports two interface modes (via `PluginInterface`):

- **`LEFT_TAB`**: embedded as a tab in Ariane's left panel (default)
- **`WINDOW`**: standalone JavaFX `Stage` with its own window

## Project Opening and Creation

After a project loads successfully, the controller expands its project pane and
collapses the project listing. This applies to downloaded projects and newly
created projects loaded from the empty TML template. Refreshing the list does
not change the selected pane.

- **Writable project:** the change description, Save Project, Import local file,
  and Reload Project controls are enabled. The footer shows the editing lock
  icon and the existing unlock guidance.
- **Read-only project:** the same pane stays visible, with all four controls
  disabled. The footer shows a red forbidden symbol and “Read-only project. You
  are not allowed to modify this project.” The status remains readable and the
  project listing remains accessible. This applies both to read-only permissions
  and to an unsuccessful lock acquisition.
- **New project:** creation acquires the lock and loads the empty template
  before selecting the editable project pane and showing the creation success
  message. The change description starts empty.

`currentProject` continues to represent a project with an acquired editing lock;
showing a read-only project does not grant a lock or enable upload shortcuts.
Loading-state updates keep every project action disabled without an active lock.

Before sending each delayed REDRAW command or firing Ariane's Center View
action, the controller checks the current survey for data. Empty surveys skip
both actions, including when a survey becomes empty during either redraw delay.
Surveys with data retain the two redraws and automatic centering.

Ariane 26.4.1 routes the plugin's REDRAW command to
`DisplayToolController.onRedrawMap()`, which shows “No Data to display” when its
survey data is empty. Guarding only Center View does not prevent this warning;
each REDRAW dispatch must also be guarded. Loading the empty template and
selecting the new project's editable pane still proceed normally.

## Modal Dialog System (`SpeleoDBModals`)

All user-facing dialogs are centralized in `SpeleoDBModals` to ensure consistent
Material Design styling.

### Dialog Types

| Method                     | Purpose                           | Button Colors                |
| -------------------------- | --------------------------------- | ---------------------------- |
| `showConfirmation()`       | Yes/No decisions                  | Blue / Grey                  |
| `showError()`              | Error display                     | Red                          |
| `showWarning()`            | Warning display                   | Orange                       |
| `showInfo()`               | Information display               | Blue                         |
| `showLockFailure()`        | Wide info for lock conflicts      | Blue                         |
| `showInputDialog()`        | Text input (e.g., commit message) | Green / Red                  |
| `showSuccessCelebration()` | Upload success with GIF animation | Green / Red                  |
| `showCustomDialog()`       | Fully custom content and buttons  | Configurable                 |
| `showSaveModal()`          | Ctrl+S save shortcut              | Delegates to showInputDialog |

### Internal Helpers

- `createBaseAlert(type, title)`: creates Alert with owner, modality, header
  cleared
- `applySimpleDialogStyle(pane)`: white background, shadow, transparent header
  panel
- `applyMaterialButton(button, color, colorDark, width, fontSize, padV, padH, radius)`:
  unified button styling, also used by the convenience wrappers
- `applyCenteredButtonBar(pane)`: centers button bar with separator
- CSS pre-warming via `preWarmModalSystem()` at startup

## Tooltip System (`SpeleoDBTooltips`)

Popup-based notifications displayed at the top center of the main window.

### Tooltip Types

| Type    | Icon    | Duration | Fade     |
| ------- | ------- | -------- | -------- |
| SUCCESS | check   | 4s       | Out only |
| ERROR   | X       | 5s       | Out only |
| INFO    | i       | 4s       | In + Out |
| WARNING | warning | 4s       | In + Out |

Initialize once during startup with `SpeleoDBTooltips.initialize(stage)` or the
`initialize(scene)` overload once the scene has a window. The controller uses
the scene overload, including a scene-property listener when the panel is not
yet attached.

## CSS Architecture

### Stylesheets

- `src/main/resources/css/fxmlmain.css`: main stylesheet, Lato font integration
- `src/main/resources/css/slider.css`: slider-specific styling

### Style Constants

Shared inline style constants live in `SpeleoDBConstants.STYLES`; existing code
also contains some inline literals. Shared constants include:

- `MATERIAL_COLORS`: Material Design color palette (PRIMARY, SUCCESS, ERROR,
  WARNING, INFO)
- `MATERIAL_INFO_DIALOG_STYLE`, `MATERIAL_BUTTON_STYLE`, etc.

### Fonts

Bundled Lato font variants in `src/main/resources/fonts/` (`Lato-*.ttf`).

### Other resources

Paths below are relative to `src/main/resources/`:

| Resource                                             | Purpose                                                          |
| ---------------------------------------------------- | ---------------------------------------------------------------- |
| `images/logo.png`                                    | Window icon and plugin branding                                  |
| `images/icons/`                                      | Project and lock icons                                           |
| `images/success_gifs/`                               | Upload success animations                                        |
| `org/speleodb/ariane/plugin/speleodb/countries.json` | Country choices; loaded relative to `NewProjectDialog`'s package |
| `tml/empty_project.tml`                              | Template for projects without uploaded survey content            |

## Threading Model

```
┌─────────────────────┐     ┌──────────────────────────┐
│  FX Application     │     │  SpeleoDB Worker Pool     │
│  Thread             │     │  (Cached, Daemon)         │
│                     │     │                            │
│  - UI rendering     │     │  - HTTP requests           │
│  - FXML injection   │◄────│  - File I/O                │
│  - Dialog display   │     │  - Plugin updates          │
│  - Tooltip animation│     │  - Announcement fetching   │
│                     │     │                            │
│  Platform.runLater()│     │  executorService.submit()  │
└─────────────────────┘     └──────────────────────────┘
```

Background tasks post results back to FX thread via `Platform.runLater()`. The
controller's `fxmlInitializedLatch` prevents background threads from accessing
`@FXML` fields before `initialize()` completes.

UI implementation conventions are in
[AGENTS.md](../../AGENTS.md#javafx-ui-conventions).
