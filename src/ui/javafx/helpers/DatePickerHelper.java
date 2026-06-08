package ui.javafx.helpers;

import javafx.scene.control.DatePicker;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DatePickerHelper {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String DATE_FORMAT_ERROR = "Date must use format dd-MM-yyyy.";

    private DatePickerHelper() {
    }

    public static void configureDdMmYyyy(DatePicker datePicker) {
        if (datePicker == null) {
            return;
        }
        datePicker.setEditable(true);
        datePicker.setPromptText("dd-MM-yyyy");
        datePicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(DISPLAY_DATE);
            }

            @Override
            public LocalDate fromString(String value) {
                return parseEditorText(value);
            }
        });
        if (datePicker.getEditor() != null) {
            datePicker.getEditor().focusedProperty().addListener((observable, wasFocused, focused) -> {
                if (!focused) {
                    commitEditorTextQuietly(datePicker);
                }
            });
            datePicker.getEditor().setOnAction(event -> commitEditorTextQuietly(datePicker));
        }
        datePicker.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> commitEditorTextQuietly(datePicker));
        datePicker.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F4 || event.getCode() == KeyCode.DOWN) {
                commitEditorTextQuietly(datePicker);
            }
        });
        datePicker.showingProperty().addListener((observable, wasShowing, showing) -> {
            if (showing) {
                commitEditorTextQuietly(datePicker);
            }
        });
    }

    public static void commitEditorText(DatePicker datePicker) {
        if (datePicker == null || datePicker.getEditor() == null) {
            return;
        }
        String typed = datePicker.getEditor().getText();
        if (typed == null || typed.isBlank()) {
            datePicker.setValue(null);
            return;
        }
        LocalDate parsed = parseEditorText(typed);
        datePicker.setValue(parsed);
        datePicker.getEditor().setText(parsed.format(DISPLAY_DATE));
    }

    private static void commitEditorTextQuietly(DatePicker datePicker) {
        try {
            commitEditorText(datePicker);
        } catch (IllegalArgumentException ignored) {
            // Save validation reports the full message. During editing, avoid noisy popups.
        }
    }

    private static LocalDate parseEditorText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DISPLAY_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(DATE_FORMAT_ERROR);
        }
    }
}
