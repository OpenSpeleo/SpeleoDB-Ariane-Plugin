package org.speleodb.ariane.plugin.speleodb;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.DEBUG;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.DIALOGS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.DIMENSIONS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MISC;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.STYLES;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Dialog for creating new projects with country selection and optional coordinates.
 * Features async country loading and caching for better performance.
 */
public class NewProjectDialog extends Dialog<NewProjectDialog.ProjectData> {

    private static final SpeleoDBLogger logger = SpeleoDBLogger.getInstance();

    // Cache for country data to avoid repeated file loading
    private static volatile Map<String, String> cachedCountries = null;
    private static final Object countryLoadLock = new Object();

    // UI Components
    private TextField nameField;
    private TextArea descriptionField;
    private ComboBox<String> countryComboBox;
    private TextField latitudeField;
    private TextField longitudeField;

    // For searchable country dropdown
    private ObservableList<String> allCountryNames;
    private FilteredList<String> filteredCountryNames;

    // Save button reference for validation
    private Node saveButton;
    private static final String ERROR_STYLE = "-fx-border-color: #e74c3c; -fx-border-width: 2px; -fx-border-radius: 3px;";

    // Async loading executor
    private static final ExecutorService countryLoader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, DEBUG.COUNTRIES_LOADER_THREAD_NAME);
        t.setDaemon(true); // Don't prevent JVM shutdown
        return t;
    });

    // Pre-load countries asynchronously when class is first loaded
    static {
        countryLoader.submit(() -> {
            try {
                loadCountriesFromJson();
            } catch (IOException e) {
                logger.warn(MESSAGES.ERROR_PRE_LOADING_COUNTRIES + e.getMessage());
            }
        });
    }

    /**
     * Public method to pre-load countries data for optimization.
     * Called from the controller during initialization.
     */
    public static void preLoadCountriesData() {
        // Countries are already loaded in static block, but this ensures it's triggered
        countryLoader.submit(() -> {
            try {
                loadCountriesFromJson(); // Will return immediately if already loaded
            } catch (IOException e) {
                logger.warn(MESSAGES.ERROR_PRE_LOADING_COUNTRIES + e.getMessage());
            }
        });
    }

    /**
     * Loads country data from JSON file into cache
     */
    private static void loadCountriesFromJson() throws IOException {
        if (cachedCountries != null) return; // Already loaded

        synchronized (countryLoadLock) {
            if (cachedCountries != null) return; // Double-check

            // Load countries from JSON file in resources
            InputStream inputStream = NewProjectDialog.class.getResourceAsStream(PATHS.COUNTRIES_RESOURCE);
            if (inputStream == null) {
                throw new RuntimeException(MESSAGES.COUNTRIES_NOT_FOUND +
                        NewProjectDialog.class.getPackage().getName().replace('.', '/') + "/" + PATHS.COUNTRIES_RESOURCE);
            }

            // Read and parse JSON
            String jsonContent = new String(inputStream.readAllBytes());
            JsonObject countriesObj;
            try (JsonReader jsonReader = Json.createReader(new StringReader(jsonContent))) {
                countriesObj = jsonReader.readObject();
            }

            // Convert to display format: "Country Name (CODE)" -> "CODE"
            Map<String, String> countryMap = new TreeMap<>();
            for (Map.Entry<String, jakarta.json.JsonValue> entry : countriesObj.entrySet()) {
                String countryCode = entry.getKey();
                String countryName = entry.getValue().toString().replace("\"", "");
                String displayName = countryName + " (" + countryCode + ")";
                countryMap.put(displayName, countryCode);
            }

            cachedCountries = countryMap;
            logger.info(String.format(MESSAGES.COUNTRIES_CACHED_SUCCESS, cachedCountries.size()));
        }
    }

    @SuppressWarnings("this-escape")
    public NewProjectDialog() {
        setupDialog();
        createDialogContent();
        setupResultConverter();
    }

    private void setupDialog() {
        setTitle(DIALOGS.TITLE_CREATE_NEW_PROJECT);
        setResizable(false);

        // Set button types
        ButtonType saveButtonType = new ButtonType(DIALOGS.BUTTON_SAVE_CHANGES, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType(DIALOGS.BUTTON_CANCEL, ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, cancelButtonType);

        // Get reference to save button for validation
        saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true); // Initially disabled until valid country selected

        // Ensure dialog sizing is capped to avoid accidental fullscreen
        getDialogPane().setMinWidth(DIMENSIONS.DIALOG_MIN_WIDTH);
        getDialogPane().setPrefWidth(DIMENSIONS.DIALOG_PREF_WIDTH);
        getDialogPane().setMaxWidth(DIMENSIONS.DIALOG_PREF_WIDTH + 100);
        getDialogPane().setMinHeight(200);
        getDialogPane().setPrefHeight(Region.USE_COMPUTED_SIZE);
        getDialogPane().setMaxHeight(600);

        // Apply CSS stylesheet to the dialog
        getDialogPane().getStylesheets().add(getClass().getResource(STYLES.MAIN_CSS_PATH).toExternalForm());
    }

    private void createDialogContent() {
        // Create main container
        VBox content = new VBox(DIMENSIONS.DIALOG_CONTENT_SPACING);
        content.setPadding(new Insets(DIMENSIONS.DIALOG_PADDING));
        content.setPrefWidth(DIMENSIONS.NEW_PROJECT_DIALOG_PREF_WIDTH);

        // Add form sections
        content.getChildren().addAll(
                createNameSection(),
                createDescriptionSection(),
                createCountrySection(),
                createCoordinatesSection()
        );

        getDialogPane().setContent(content);
    }

    private VBox createNameSection() {
        VBox section = new VBox(DIMENSIONS.SECTION_SPACING);

        // Create label with required indicator in HBox
        Label nameLabel = new Label(DIALOGS.LABEL_PROJECT_NAME);
        Label asterisk = new Label(DIALOGS.ASTERISK_REQUIRED);
        asterisk.setStyle(STYLES.REQUIRED_FIELD_STYLE); // Red color for required asterisk

        HBox labelBox = new HBox(nameLabel, asterisk);
        nameLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.MEDIUM, DIMENSIONS.LABEL_FONT_SIZE));

        nameField = new TextField();
        nameField.setPromptText(DIALOGS.PROMPT_PROJECT_NAME);
        nameField.setPrefWidth(DIMENSIONS.FIELD_PREF_WIDTH);

        section.getChildren().addAll(labelBox, nameField);
        return section;
    }

    private VBox createDescriptionSection() {
        VBox section = new VBox(DIMENSIONS.SECTION_SPACING);

        // Create label with required indicator in HBox
        Label descLabel = new Label(DIALOGS.LABEL_DESCRIPTION);
        Label asterisk = new Label(DIALOGS.ASTERISK_REQUIRED);
        asterisk.setStyle(STYLES.REQUIRED_FIELD_STYLE);

        HBox labelBox = new HBox(descLabel, asterisk);
        descLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.MEDIUM, DIMENSIONS.LABEL_FONT_SIZE));

        descriptionField = new TextArea();
        descriptionField.setPromptText(DIALOGS.PROMPT_DESCRIPTION);
        descriptionField.setPrefRowCount(DIMENSIONS.DESCRIPTION_FIELD_ROWS);
        descriptionField.setPrefWidth(DIMENSIONS.FIELD_PREF_WIDTH);

        section.getChildren().addAll(labelBox, descriptionField);
        return section;
    }

    private VBox createCountrySection() {
        VBox section = new VBox(DIMENSIONS.SECTION_SPACING);

        // Create label with required indicator in HBox
        Label countryLabel = new Label(DIALOGS.LABEL_COUNTRY);
        Label asterisk = new Label(DIALOGS.ASTERISK_REQUIRED);
        asterisk.setStyle(STYLES.REQUIRED_FIELD_STYLE);

        HBox labelBox = new HBox(countryLabel, asterisk);
        countryLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.MEDIUM, DIMENSIONS.LABEL_FONT_SIZE));

        countryComboBox = new ComboBox<>();
        countryComboBox.setPromptText(DIALOGS.PROMPT_COUNTRY);
        countryComboBox.setMaxWidth(Double.MAX_VALUE);
        countryComboBox.setEditable(true); // Enable typing to search

        // Load countries asynchronously
        loadCountriesIntoComboBox();

        // Setup searchable filtering
        setupCountrySearchFilter();

        section.getChildren().addAll(labelBox, countryComboBox);
        return section;
    }

    private VBox createCoordinatesSection() {
        VBox section = new VBox(DIMENSIONS.COORDINATES_SECTION_SPACING);

        // Latitude field
        VBox latSection = new VBox(DIMENSIONS.SECTION_SPACING);
        Label latLabel = new Label(DIALOGS.LABEL_LATITUDE);
        latLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.MEDIUM, DIMENSIONS.LABEL_FONT_SIZE));
        latitudeField = new TextField();
        latitudeField.setPromptText(DIALOGS.PROMPT_LATITUDE);
        latitudeField.setPrefWidth(DIMENSIONS.FIELD_PREF_WIDTH);
        latSection.getChildren().addAll(latLabel, latitudeField);

        // Longitude field
        VBox lonSection = new VBox(DIMENSIONS.SECTION_SPACING);
        Label lonLabel = new Label(DIALOGS.LABEL_LONGITUDE);
        lonLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.MEDIUM, DIMENSIONS.LABEL_FONT_SIZE));
        longitudeField = new TextField();
        longitudeField.setPromptText(DIALOGS.PROMPT_LONGITUDE);
        longitudeField.setPrefWidth(DIMENSIONS.FIELD_PREF_WIDTH);
        lonSection.getChildren().addAll(lonLabel, longitudeField);

        section.getChildren().addAll(latSection, lonSection);
        return section;
    }

    private void loadCountriesIntoComboBox() {
        if (cachedCountries != null) {
            // Countries already loaded, populate immediately
            Platform.runLater(() -> populateCountryComboBox(cachedCountries));
        } else {
            // Load countries asynchronously
            countryLoader.submit(() -> {
                try {
                    loadCountriesFromJson();
                    Platform.runLater(() -> populateCountryComboBox(cachedCountries));
                } catch (IOException e) {
                    logger.warn(MESSAGES.ERROR_LOADING_COUNTRIES + e.getMessage());
                }
            });
        }
    }

    private void populateCountryComboBox(Map<String, String> countries) {
        if (countries == null) return;

        allCountryNames = FXCollections.observableArrayList(countries.keySet());
        allCountryNames.sort(String::compareTo);

        filteredCountryNames = new FilteredList<>(allCountryNames, p -> true);
        countryComboBox.setItems(filteredCountryNames);

        // Re-validate in case user typed something before countries loaded
        validateCountrySelection();

        logger.debug("Loaded " + countries.size() + " countries into combo box" +
                (cachedCountries != null ? " (from cache)" : " (direct load)"));
    }

    private void setupCountrySearchFilter() {
        // Add listener to filter countries as user types
        countryComboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            // Don't filter if a selection was just made
            final String selected = countryComboBox.getSelectionModel().getSelectedItem();
            if (selected != null && selected.equals(newValue)) {
                validateCountrySelection();
                return;
            }

            if (filteredCountryNames == null) return;

            Platform.runLater(() -> {
                // Filter based on typed text (case-insensitive)
                if (newValue == null || newValue.isEmpty()) {
                    filteredCountryNames.setPredicate(country -> true);
                } else {
                    final String lowerCaseFilter = newValue.toLowerCase();
                    filteredCountryNames.setPredicate(country ->
                        country.toLowerCase().contains(lowerCaseFilter)
                    );
                }

                // Show dropdown if there are matching results
                if (!filteredCountryNames.isEmpty() && !countryComboBox.isShowing()) {
                    countryComboBox.show();
                }

                // Validate after filtering
                validateCountrySelection();
            });
        });

        // When an item is selected from the dropdown, set the editor text properly
        countryComboBox.setOnAction(event -> {
            String selected = countryComboBox.getSelectionModel().getSelectedItem();
            if (selected != null) {
                countryComboBox.getEditor().setText(selected);
                validateCountrySelection();
            }
        });

        // Also validate when combo box loses focus
        countryComboBox.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                validateCountrySelection();
            }
        });
    }

    /**
     * Validates that the current country field contains a valid country selection.
     * Updates the Save button state and visual error indicator.
     */
    private void validateCountrySelection() {
        String editorText = countryComboBox.getEditor().getText();
        boolean isValid = isValidCountrySelection(editorText);

        // Update Save button state
        if (saveButton != null) {
            saveButton.setDisable(!isValid);
        }

        // Update visual feedback on the combo box editor
        if (editorText != null && !editorText.isEmpty()) {
            if (isValid) {
                countryComboBox.getEditor().setStyle(""); // Clear error style
            } else {
                countryComboBox.getEditor().setStyle(ERROR_STYLE); // Show error
            }
        } else {
            countryComboBox.getEditor().setStyle(""); // Empty is ok (just not submitted)
        }
    }

    /**
     * Checks if the given text matches a valid country in the list.
     */
    private boolean isValidCountrySelection(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return cachedCountries != null && cachedCountries.containsKey(text);
    }

    private void setupResultConverter() {
        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return new ProjectData(
                        nameField.getText(),
                        descriptionField.getText(),
                        getSelectedCountryCode(),
                        latitudeField.getText(),
                        longitudeField.getText()
                );
            }
            return null;
        });
    }

    private String getSelectedCountryCode() {
        // First try the selection model
        String selected = countryComboBox.getSelectionModel().getSelectedItem();
        if (selected != null && cachedCountries != null && cachedCountries.containsKey(selected)) {
            return cachedCountries.get(selected);
        }

        // If editable, also check the editor text for an exact match
        String editorText = countryComboBox.getEditor().getText();
        if (editorText != null && cachedCountries != null && cachedCountries.containsKey(editorText)) {
            return cachedCountries.get(editorText);
        }

        return null;
    }

    /**
     * Data class for holding project creation data
     */
    public static class ProjectData {
        private final String name;
        private final String description;
        private final String countryCode;
        private final String latitude;
        private final String longitude;

        public ProjectData(String name, String description, String countryCode, String latitude, String longitude) {
            this.name = name;
            this.description = description;
            this.countryCode = countryCode;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCountryCode() { return countryCode; }
        public String getLatitude() { return latitude; }
        public String getLongitude() { return longitude; }

        @Override
        public String toString() {
            return String.format(MISC.PROJECT_DATA_FORMAT, name, description, countryCode, latitude, longitude);
        }
    }
}
