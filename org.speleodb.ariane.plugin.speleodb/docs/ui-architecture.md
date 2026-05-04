# UI Architecture

## FXML Layout

The plugin's entire UI is defined in a single FXML file: `src/main/resources/fxml/SpeleoDB.fxml`.

The controller (`SpeleoDBController`) is set programmatically via `FXMLLoader.setController()` rather than declared in FXML, since the controller is a singleton.

### Integration Modes

The plugin supports two interface modes (via `PluginInterface`):
- **`LEFT_TAB`**: embedded as a tab in Ariane's left panel (default)
- **`WINDOW`**: standalone JavaFX `Stage` with its own window

## Modal Dialog System (`SpeleoDBModals`)

All user-facing dialogs are centralized in `SpeleoDBModals` to ensure consistent Material Design styling.

### Dialog Types

| Method | Purpose | Button Colors |
|--------|---------|---------------|
| `showConfirmation()` | Yes/No decisions | Blue / Grey |
| `showError()` | Error display | Red |
| `showWarning()` | Warning display | Orange |
| `showInfo()` | Information display | Blue |
| `showLockFailure()` | Wide info for lock conflicts | Blue |
| `showInputDialog()` | Text input (e.g., commit message) | Green / Red |
| `showSuccessCelebration()` | Upload success with GIF animation | Green / Red |
| `showCustomDialog()` | Fully custom content and buttons | Configurable |
| `showSaveModal()` | Ctrl+S save shortcut | Delegates to showInputDialog |

### Internal Helpers

- `createBaseAlert(type, title)`: creates Alert with owner, modality, header cleared
- `applySimpleDialogStyle(pane)`: white background, shadow, transparent header panel
- `applyMaterialButton(button, color, colorDark, width, fontSize, padV, padH, radius)`: unified button styling
- `applyCenteredButtonBar(pane)`: centers button bar with separator
- CSS pre-warming via `preWarmModalSystem()` at startup

## Tooltip System (`SpeleoDBTooltips`)

Popup-based notifications displayed at the top center of the main window.

### Tooltip Types

| Type | Icon | Duration | Fade |
|------|------|----------|------|
| SUCCESS | check | 4s | Out only |
| ERROR | X | 5s | Out only |
| INFO | i | 4s | In + Out |
| WARNING | warning | 4s | In + Out |

Initialization: `SpeleoDBTooltips.initialize(stage)` must be called once during startup.

## CSS Architecture

### Stylesheets
- `src/main/resources/css/fxmlmain.css`: main stylesheet (~636 lines), Lato font integration
- `src/main/resources/css/slider.css`: slider-specific styling

### Style Constants
All inline `-fx-*` styles are defined in `SpeleoDBConstants.STYLES`:
- `MATERIAL_COLORS`: Material Design color palette (PRIMARY, SUCCESS, ERROR, WARNING, INFO)
- `MATERIAL_INFO_DIALOG_STYLE`, `MATERIAL_BUTTON_STYLE`, etc.

### Fonts
Bundled Lato font family (10 weights) in `src/main/resources/fonts/`.

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

Background tasks post results back to FX thread via `Platform.runLater()`. The controller's `fxmlInitializedLatch` prevents background threads from accessing `@FXML` fields before `initialize()` completes.
