# SpeleoDB Plugin Architecture

## Module Dependency Graph

```mermaid
graph TB
    subgraph submodules [Git Submodules - DO NOT MODIFY]
        CavelibAPI["com.arianesline.cavelib.api<br/>(Cave survey data model)"]
        PluginAPI["com.arianesline.ariane.plugin.api<br/>(Plugin SPI: DataServerPlugin, etc.)"]
    end

    subgraph plugin [org.speleodb.ariane.plugin.speleodb]
        SpeleoDBPlugin["SpeleoDBPlugin<br/>implements DataServerPlugin"]
        SpeleoDBController["SpeleoDBController<br/>(Singleton, ~3900 lines)"]
        SpeleoDBService["SpeleoDBService<br/>(HTTP client layer)"]
        SpeleoDBModals["SpeleoDBModals<br/>(Material Design dialogs)"]
        SpeleoDBTooltips["SpeleoDBTooltips<br/>(Popup notifications)"]
        SpeleoDBLogger["SpeleoDBLogger<br/>(Singleton, file + UI logging)"]
        SpeleoDBConstants["SpeleoDBConstants<br/>(All constants & enums)"]
        NewProjectDialog["NewProjectDialog<br/>(Create project UI)"]
        HTTPMultipart["HTTPRequestMultipartBody<br/>(File upload encoding)"]
        NotModified["NotModifiedException<br/>(HTTP 304 signal)"]
    end

    subgraph container [com.arianesline.plugincontainer - Dev Runner]
        ContainerApp["PluginContainerApplication"]
        ContainerCtrl["PluginContainerController"]
    end

    PluginAPI --> CavelibAPI
    SpeleoDBPlugin --> PluginAPI
    SpeleoDBPlugin --> SpeleoDBController
    SpeleoDBController --> SpeleoDBService
    SpeleoDBController --> SpeleoDBModals
    SpeleoDBController --> SpeleoDBTooltips
    SpeleoDBController --> SpeleoDBLogger
    SpeleoDBController --> NewProjectDialog
    SpeleoDBController --> SpeleoDBConstants
    SpeleoDBService --> HTTPMultipart
    SpeleoDBService --> NotModified
    SpeleoDBService --> SpeleoDBLogger
    SpeleoDBService --> SpeleoDBConstants
    ContainerApp --> PluginAPI
    ContainerApp --> CavelibAPI
```

## Component Responsibilities

### SpeleoDBPlugin (SPI Entry Point)
- Implements `DataServerPlugin` from the Ariane plugin API
- Manages lifecycle: `showUI()`, `closeUI()`, `getUINode()`
- Owns the `ExecutorService` for background tasks
- Bridges FXML loading with singleton controller
- Handles JVM shutdown hook for lock release

### SpeleoDBController (UI Controller - Singleton)
- FXML controller for `SpeleoDB.fxml`
- Manages all UI state: authentication, project list, current project, locks
- Coordinates between `SpeleoDBService` (network) and `SpeleoDBModals` (dialogs)
- Uses `CountDownLatch` to guard against FXML initialization races
- Handles keyboard shortcuts (Ctrl+S/Cmd+S for save)

### SpeleoDBService (Network Layer)
- Encapsulates all HTTP communication with the SpeleoDB REST API
- Manages authentication state (`authToken`, `sdbInstance`)
- URL normalization: auto-detects local vs remote hosts for http/https
- Creates protocol-appropriate `HttpClient` instances
- JSON parsing via `jakarta.json` API

### SpeleoDBModals (Dialog Layer)
- All modal dialogs: confirmation, error, warning, info, input, success celebration
- Material Design styling with `createBaseAlert()` and `applySimpleDialogStyle()`
- Unified button styling via `applyMaterialButton()`
- CSS pre-warming for instant display

### SpeleoDBLogger (Logging Layer)
- Thread-safe file logging with automatic rotation (10 MB, 5 backups)
- UI console integration via `SpeleoDBController.appendToUILog()`
- Log levels: DEBUG (file only), INFO/WARN/ERROR (file + UI)
- Graceful shutdown with file flush before flag

## Data Flow: Authentication

```mermaid
sequenceDiagram
    participant User
    participant Controller as SpeleoDBController
    participant Service as SpeleoDBService
    participant API as SpeleoDB API

    User->>Controller: Click Connect
    Controller->>Service: authenticate(email, password, oauth, instanceUrl)
    Service->>Service: setSDBInstance(url) via resolveInstanceUrl()
    Service->>Service: createHttpClient()
    Service->>API: POST /api/v2/user/auth-token/
    API-->>Service: {"token": "abc123..."}
    Service->>Service: parseAuthToken() via jakarta.json
    Service-->>Controller: success
    Controller->>Service: listProjects()
    Service->>API: GET /api/v2/projects/
    API-->>Service: [...]
    Service-->>Controller: filtered JsonArray (ARIANE only)
    Controller->>Controller: updateProjectList()
```

## Data Flow: Upload/Download Cycle

```mermaid
sequenceDiagram
    participant User
    participant Controller as SpeleoDBController
    participant Service as SpeleoDBService
    participant Plugin as SpeleoDBPlugin
    participant API as SpeleoDB API

    User->>Controller: Click Upload
    Controller->>Controller: showSaveModal() via SpeleoDBModals
    User->>Controller: Enter commit message
    Controller->>Plugin: saveSurvey() (trigger host app save)
    Plugin->>Plugin: commandProperty.set(SAVE)
    Controller->>Service: acquireOrRefreshProjectMutex()
    Service->>API: POST /api/v2/projects/{id}/acquire/
    API-->>Service: 200 OK
    Controller->>Service: uploadProject(message, project)
    Service->>Service: calculateSHA256 + empty template check
    Service->>API: PUT /api/v2/projects/{id}/upload/ariane_tml/
    API-->>Service: 200 OK / 304 Not Modified
    Controller->>Controller: showSuccessCelebration()
```

## Data Flow: Plugin Update Download

```mermaid
sequenceDiagram
    participant Controller as SpeleoDBController
    participant Service as SpeleoDBService
    participant Api as SpeleoDBAPI
    participant Host as ReleaseHost
    participant Disk as PluginsDirectory

    Controller->>Service: fetchPluginReleases(instanceUrl)
    Service->>Api: GET /api/v2/plugin_releases/
    Api-->>Service: [...]
    Service-->>Controller: compatible releases
    Controller->>Controller: select latest version
    Controller->>Service: downloadPluginUpdate(downloadUrl)
    Service->>Host: GET downloadUrl
    Host-->>Service: JAR bytes
    Service-->>Controller: fileData
    Controller->>Controller: verify SHA-256
    Controller->>Disk: write *.jar.new
    Controller->>Controller: show restart-required dialog
```

## JPMS Module Structure

```java
module org.speleodb.ariane.plugin.speleodb {
    requires com.arianesline.ariane.plugin.api;
    requires com.arianesline.cavelib.api;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires jakarta.json;
    // ...
    provides DataServerPlugin with SpeleoDBPlugin;
}
```

The plugin is discovered by the host application via Java's `ServiceLoader` mechanism, using the `provides ... with` directive.
