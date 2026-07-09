package pages.billing.billing_overview;

import app.AppShell;
import app.FxController;
import app.helpers.DialogHelper;
import app.helpers.DialogThemeHelper;
import app.helpers.PermissionHelper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import pages.billing.BillingRecord;
import pages.billing.BillingService;
import pages.notification.NotificationHelper;
import users.Session;

import java.util.Locale;
import java.util.Optional;

public class BillingController implements FxController {

    private final BillingService billingService = new BillingService();
    private final ObservableList<BillingRecord> invoices = FXCollections.observableArrayList();
    private AppShell appShell;

    @FXML private VBox accessDeniedPane;
    @FXML private VBox contentPane;
    @FXML private Label totalRevenueLabel;
    @FXML private Label paidInvoicesLabel;
    @FXML private Label unpaidInvoicesLabel;
    @FXML private Label cancelledInvoicesLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> dateRangeFilter;
    @FXML private Button newInvoiceButton;
    @FXML private TableView<BillingRecord> billingTable;
    @FXML private TableColumn<BillingRecord, String> invoiceColumn;
    @FXML private TableColumn<BillingRecord, BillingRecord> patientColumn;
    @FXML private TableColumn<BillingRecord, BillingRecord> serviceColumn;
    @FXML private TableColumn<BillingRecord, BillingRecord> amountColumn;
    @FXML private TableColumn<BillingRecord, BillingRecord> statusColumn;
    @FXML private TableColumn<BillingRecord, BillingRecord> paymentColumn;
    @FXML private TableColumn<BillingRecord, String> dateColumn;
    @FXML private TableColumn<BillingRecord, BillingRecord> actionsColumn;
    @FXML private Label statusLabel;

    @Override
    public void setAppShell(AppShell appShell) {
        this.appShell = appShell;
        configureAccess();
        configureFilters();
        configureTable();
        configureButtons();
        if (isAuthorized()) {
            loadBilling();
        }
    }

    @FXML
    private void loadBilling() {
        if (!isAuthorized()) {
            NotificationHelper.showError(statusLabel, "Access denied.");
            return;
        }
        try {
            BillingService.BillingOverview overview = billingService.loadOverview(
                    safeText(searchField),
                    statusFilter == null ? "All" : statusFilter.getValue(),
                    dateRangeFilter == null ? "Last 30 days" : dateRangeFilter.getValue()
            );
            invoices.setAll(overview.records());
            billingTable.setItems(invoices);
            totalRevenueLabel.setText(formatAmount(overview.metrics().totalRevenue()));
            paidInvoicesLabel.setText(String.valueOf(overview.metrics().paidInvoices()));
            unpaidInvoicesLabel.setText(String.valueOf(overview.metrics().unpaidInvoices()));
            cancelledInvoicesLabel.setText(String.valueOf(overview.metrics().cancelledInvoices()));
            NotificationHelper.showInfo(statusLabel, "Billing data loaded from the local clinic database. Invoices: " + invoices.size());
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, "Could not load billing data: " + e.getMessage());
        }
    }

    @FXML
    private void clearFilters() {
        if (searchField != null) {
            searchField.clear();
        }
        if (statusFilter != null) {
            statusFilter.getSelectionModel().select("All");
        }
        if (dateRangeFilter != null) {
            dateRangeFilter.getSelectionModel().select("Last 30 days");
        }
        loadBilling();
    }

    @FXML
    private void createInvoice() {
        if (!PermissionHelper.canManageBilling(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Billing access is not available for this user.");
            return;
        }
        Window owner = billingTable == null || billingTable.getScene() == null ? null : billingTable.getScene().getWindow();
        try {
            BillingRecord created = showCreateInvoiceDialog(owner);
            if (created != null) {
                loadBilling();
                NotificationHelper.showSuccess(statusLabel, "Invoice " + created.getInvoiceNo() + " created.");
            }
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private BillingRecord showCreateInvoiceDialog(Window owner) throws Exception {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Invoice");
        DialogThemeHelper.apply(dialog);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType saveType = new ButtonType("Save Invoice", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, saveType);

        TextField patientIdField = new TextField();
        patientIdField.setPromptText("Patient ID");
        TextField serviceNameField = new TextField();
        serviceNameField.setPromptText("Service Name");
        TextField visitTypeField = new TextField();
        visitTypeField.setPromptText("Visit Type");
        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        ComboBox<String> paymentStatusBox = new ComboBox<>(FXCollections.observableArrayList("UNPAID", "PAID", "CANCELLED"));
        paymentStatusBox.getSelectionModel().select("UNPAID");
        ComboBox<String> paymentMethodBox = new ComboBox<>(FXCollections.observableArrayList("Cash", "Card", "Insurance", "Other"));
        paymentMethodBox.setPromptText("Optional");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Visit notes, billing notes, or insurance note");
        notesArea.setPrefRowCount(4);

        configureInvoiceField(patientIdField);
        configureInvoiceField(serviceNameField);
        configureInvoiceField(visitTypeField);
        configureInvoiceField(amountField);
        configureInvoiceField(paymentStatusBox);
        configureInvoiceField(paymentMethodBox);
        configureInvoiceField(notesArea);
        notesArea.getStyleClass().add("invoice-notes-area");
        amountField.setAlignment(Pos.CENTER_LEFT);

        Label headerTitle = new Label("New Invoice");
        headerTitle.getStyleClass().addAll("screen-title", "invoice-section-title");
        Label headerSubtitle = new Label("Create a local clinic invoice for a patient visit.");
        headerSubtitle.getStyleClass().add("muted-text");
        headerSubtitle.setWrapText(true);
        VBox headerCard = new VBox(6, headerTitle, headerSubtitle);
        headerCard.getStyleClass().addAll("invoice-dialog-header");

        Label patientStateBadge = new Label("Patient Lookup");
        patientStateBadge.getStyleClass().addAll("badge-pill", "invoice-patient-warning");
        Label patientNameLabel = new Label("Enter a patient ID to link the invoice.");
        patientNameLabel.getStyleClass().add("detail-field-value");
        patientNameLabel.setWrapText(true);
        Label patientMetaLabel = new Label("ID: -");
        patientMetaLabel.getStyleClass().add("detail-field-name");
        VBox patientSummaryText = new VBox(4, patientNameLabel, patientMetaLabel);
        Region patientSpacer = new Region();
        HBox.setHgrow(patientSpacer, Priority.ALWAYS);
        HBox patientSummaryTop = new HBox(12, patientStateBadge, patientSpacer);
        patientSummaryTop.setAlignment(Pos.CENTER_LEFT);
        VBox patientSummaryCard = new VBox(10, patientSummaryTop, patientSummaryText);
        patientSummaryCard.getStyleClass().add("invoice-patient-card");

        patientIdField.textProperty().addListener((observable, oldValue, newValue) ->
                refreshPatientLookup(patientStateBadge, patientNameLabel, patientMetaLabel, newValue));
        refreshPatientLookup(patientStateBadge, patientNameLabel, patientMetaLabel, patientIdField.getText());

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.getStyleClass().add("invoice-dialog");
        ColumnConstraints leftColumn = new ColumnConstraints();
        leftColumn.setPercentWidth(50);
        leftColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints rightColumn = new ColumnConstraints();
        rightColumn.setPercentWidth(50);
        rightColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(leftColumn, rightColumn);
        grid.add(sectionTitle("Patient"), 0, 0, 2, 1);
        grid.add(detailField("Patient ID", patientIdField), 0, 1);
        grid.add(detailField("Service Name", serviceNameField), 1, 1);
        grid.add(sectionTitle("Service Details"), 0, 2, 2, 1);
        grid.add(detailField("Visit Type", visitTypeField), 0, 3);
        grid.add(detailField("Amount", amountField), 1, 3);
        grid.add(sectionTitle("Payment"), 0, 4, 2, 1);
        grid.add(detailField("Payment Status", paymentStatusBox), 0, 5);
        grid.add(detailField("Payment Method", paymentMethodBox), 1, 5);
        grid.add(sectionTitle("Notes"), 0, 6, 2, 1);
        VBox notesBox = detailField("Notes", notesArea);
        grid.add(notesBox, 0, 7, 2, 1);

        Label dialogStatusLabel = new Label("Review the invoice fields before saving.");
        dialogStatusLabel.getStyleClass().add("muted-text");
        dialogStatusLabel.setWrapText(true);

        VBox content = new VBox(18,
                headerCard,
                patientSummaryCard,
                grid,
                dialogStatusLabel
        );
        content.setPadding(new Insets(26));
        content.getStyleClass().add("invoice-dialog");

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(720, 640);
        dialog.getDialogPane().setMinWidth(700);
        final BillingRecord[] createdHolder = new BillingRecord[1];
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        if (saveButton != null) {
            saveButton.getStyleClass().addAll("primary-button", "invoice-save-button");
            saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    createdHolder[0] = billingService.createInvoice(Session.getCurrentUser(), new BillingService.InvoiceDraft(
                            safeText(patientIdField),
                            safeText(serviceNameField),
                            safeText(visitTypeField),
                            parseAmount(amountField.getText()),
                            paymentStatusBox.getValue(),
                            paymentMethodBox.getValue(),
                            notesArea.getText()
                    ));
                } catch (Exception e) {
                    NotificationHelper.showError(dialogStatusLabel, e.getMessage());
                    event.consume();
                }
            });
        }

        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) {
            cancelButton.getStyleClass().addAll("secondary-button", "invoice-cancel-button");
        }

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType) {
            return createdHolder[0];
        }
        return null;
    }

    private void refreshPatientLookup(Label badge, Label patientNameLabel, Label patientMetaLabel, String patientId) {
        if (badge == null || patientNameLabel == null || patientMetaLabel == null) {
            return;
        }
        String value = patientId == null ? "" : patientId.trim();
        if (value.isBlank()) {
            badge.setText("Patient Lookup");
            badge.getStyleClass().removeAll("invoice-patient-found", "invoice-patient-warning");
            if (!badge.getStyleClass().contains("invoice-patient-warning")) {
                badge.getStyleClass().add("invoice-patient-warning");
            }
            patientNameLabel.setText("Enter a patient ID to link the invoice.");
            patientMetaLabel.setText("ID: -");
            return;
        }
        try {
            Optional<String> patientName = billingService.findPatientName(value);
            if (patientName.isPresent()) {
                badge.setText("Patient Found");
                badge.getStyleClass().removeAll("invoice-patient-warning", "invoice-patient-found");
                if (!badge.getStyleClass().contains("invoice-patient-found")) {
                    badge.getStyleClass().add("invoice-patient-found");
                }
                patientNameLabel.setText(patientName.get());
                patientMetaLabel.setText("ID: " + value);
            } else {
                badge.setText("Patient Not Found");
                badge.getStyleClass().removeAll("invoice-patient-found", "invoice-patient-warning");
                if (!badge.getStyleClass().contains("invoice-patient-warning")) {
                    badge.getStyleClass().add("invoice-patient-warning");
                }
                patientNameLabel.setText("Patient file was not found for this ID.");
                patientMetaLabel.setText("ID: " + value);
            }
        } catch (Exception e) {
            badge.setText("Patient Lookup Error");
            badge.getStyleClass().removeAll("invoice-patient-found", "invoice-patient-warning");
            if (!badge.getStyleClass().contains("invoice-patient-warning")) {
                badge.getStyleClass().add("invoice-patient-warning");
            }
            patientNameLabel.setText("Patient lookup is unavailable: " + e.getMessage());
            patientMetaLabel.setText("ID: " + value);
        }
    }

    private void configureAccess() {
        boolean authorized = isAuthorized();
        if (accessDeniedPane != null) {
            accessDeniedPane.setVisible(!authorized);
            accessDeniedPane.setManaged(!authorized);
        }
        if (contentPane != null) {
            contentPane.setVisible(authorized);
            contentPane.setManaged(authorized);
        }
    }

    private void configureFilters() {
        if (statusFilter != null) {
            statusFilter.setItems(FXCollections.observableArrayList("All", "Paid", "Unpaid", "Cancelled"));
            statusFilter.getSelectionModel().select("All");
            statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadBilling());
        }
        if (dateRangeFilter != null) {
            dateRangeFilter.setItems(FXCollections.observableArrayList("Last 30 days", "All Time"));
            dateRangeFilter.getSelectionModel().select("Last 30 days");
            dateRangeFilter.valueProperty().addListener((observable, oldValue, newValue) -> loadBilling());
        }
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> loadBilling());
        }
    }

    private void configureButtons() {
        boolean canManage = PermissionHelper.canManageBilling(Session.getCurrentUser());
        if (newInvoiceButton != null) {
            newInvoiceButton.setVisible(canManage);
            newInvoiceButton.setManaged(canManage);
        }
    }

    private void configureTable() {
        if (billingTable != null) {
            billingTable.setPlaceholder(new Label("No invoices found."));
            billingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            billingTable.setFixedCellSize(54);
        }
        if (invoiceColumn != null) {
            invoiceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    cell.getValue() == null ? "" : cell.getValue().getInvoiceNo()
            ));
        }
        if (patientColumn != null) {
            patientColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            patientColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(BillingRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Label name = new Label(blankTo(item.getPatientName(), "Unknown Patient"));
                    name.getStyleClass().add("appointments-primary-text");
                    Label id = new Label(item.getPatientId());
                    id.getStyleClass().add("appointments-secondary-text");
                    VBox box = new VBox(3, name, id);
                    setText(null);
                    setGraphic(box);
                }
            });
        }
        if (serviceColumn != null) {
            serviceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            serviceColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(BillingRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Label service = new Label(item.getServiceName());
                    service.getStyleClass().add("appointments-primary-text");
                    Label visitType = new Label(blankTo(item.getVisitType(), "Visit type not set"));
                    visitType.getStyleClass().add("appointments-secondary-text");
                    VBox box = new VBox(3, service, visitType);
                    setText(null);
                    setGraphic(box);
                }
            });
        }
        if (amountColumn != null) {
            amountColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            amountColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(BillingRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(formatAmount(item.getAmount()));
                    setGraphic(null);
                }
            });
        }
        if (statusColumn != null) {
            statusColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            statusColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(BillingRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Label badge = new Label(item.getPaymentStatus());
                    badge.getStyleClass().addAll("badge-pill", "billing-status-badge", billingStatusStyle(item));
                    setText(null);
                    setGraphic(badge);
                }
            });
        }
        if (paymentColumn != null) {
            paymentColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            paymentColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(BillingRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(paymentText(item));
                    setGraphic(null);
                }
            });
        }
        if (dateColumn != null) {
            dateColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    cell.getValue() == null ? "" : cell.getValue().getCreatedAt()
            ));
        }
        if (actionsColumn != null) {
            actionsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            actionsColumn.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(BillingRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    Button viewButton = actionButton("\uD83D\uDC41", event -> showInvoiceDetails(item));
                    Button markPaidButton = actionButton("\u2713", event -> handleMarkPaid(item));
                    Button cancelButton = actionButton("\uD83D\uDDD1", event -> handleCancelInvoice(item));
                    markPaidButton.setDisable(item.isPaid() || item.isCancelled());
                    cancelButton.setDisable(item.isCancelled());
                    HBox actions = new HBox(8, viewButton, markPaidButton, cancelButton);
                    actions.setAlignment(Pos.CENTER);
                    setText(null);
                    setGraphic(actions);
                }
            });
        }
        if (billingTable != null) {
            billingTable.setRowFactory(table -> {
                TableRow<BillingRecord> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty()) {
                        showInvoiceDetails(row.getItem());
                    }
                });
                return row;
            });
        }
    }

    private void showInvoiceDetails(BillingRecord record) {
        if (record == null || billingTable == null || billingTable.getScene() == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Invoice Details");
        DialogThemeHelper.apply(dialog);
        dialog.initOwner(billingTable.getScene().getWindow());

        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType markPaidType = new ButtonType("Mark Paid", ButtonBar.ButtonData.APPLY);
        ButtonType cancelType = new ButtonType("Cancel Invoice", ButtonBar.ButtonData.LEFT);

        dialog.getDialogPane().getButtonTypes().setAll(closeType);
        if (!record.isPaid() && !record.isCancelled()) {
            dialog.getDialogPane().getButtonTypes().add(markPaidType);
        }
        if (!record.isCancelled()) {
            dialog.getDialogPane().getButtonTypes().add(cancelType);
        }

        VBox content = new VBox(12,
                detailBlock("Invoice Number", record.getInvoiceNo()),
                detailBlock("Patient ID", record.getPatientId()),
                detailBlock("Patient Name", blankTo(record.getPatientName(), "-")),
                detailBlock("Service", record.getServiceName()),
                detailBlock("Visit Type", blankTo(record.getVisitType(), "-")),
                detailBlock("Amount", formatAmount(record.getAmount())),
                detailBlock("Payment Status", record.getPaymentStatus()),
                detailBlock("Payment Method", paymentText(record)),
                detailBlock("Created Date", blankTo(record.getCreatedAt(), "-")),
                detailBlock("Paid Date", blankTo(record.getPaidAt(), "-")),
                detailBlock("Created By", blankTo(record.getCreatedBy(), "-")),
                detailBlock("Notes", blankTo(record.getNotes(), "-"))
        );
        content.setPadding(new Insets(8));
        content.getStyleClass().add("invoice-detail-dialog");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setPrefSize(560, 620);

        Button closeButton = (Button) dialog.getDialogPane().lookupButton(closeType);
        if (closeButton != null) {
            closeButton.getStyleClass().add("secondary-button");
        }
        Button markPaidButton = (Button) dialog.getDialogPane().lookupButton(markPaidType);
        if (markPaidButton != null) {
            markPaidButton.getStyleClass().add("primary-button");
        }
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancelType);
        if (cancelButton != null) {
            cancelButton.getStyleClass().add("secondary-button");
        }

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent()) {
            if (result.get() == markPaidType) {
                handleMarkPaid(record);
            } else if (result.get() == cancelType) {
                handleCancelInvoice(record);
            }
        }
    }

    private void handleMarkPaid(BillingRecord record) {
        if (record == null || record.isPaid() || record.isCancelled()) {
            return;
        }
        if (!PermissionHelper.canManageBilling(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Billing access is not available for this user.");
            return;
        }
        try {
            String paymentMethod = record.getPaymentMethod();
            if (paymentMethod == null || paymentMethod.isBlank()) {
                paymentMethod = choosePaymentMethod(record);
                if (paymentMethod == null || paymentMethod.isBlank()) {
                    return;
                }
            }
            billingService.markPaid(Session.getCurrentUser(), record.getId(), paymentMethod);
            loadBilling();
            NotificationHelper.showSuccess(statusLabel, "Invoice marked paid.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private void handleCancelInvoice(BillingRecord record) {
        if (record == null || record.isCancelled()) {
            return;
        }
        if (!PermissionHelper.canManageBilling(Session.getCurrentUser())) {
            NotificationHelper.showError(statusLabel, "Billing access is not available for this user.");
            return;
        }
        if (!DialogHelper.confirm("Cancel Invoice", "Cancel invoice " + record.getInvoiceNo() + "?")) {
            return;
        }
        try {
            billingService.cancelInvoice(Session.getCurrentUser(), record.getId());
            loadBilling();
            NotificationHelper.showSuccess(statusLabel, "Invoice cancelled.");
        } catch (Exception e) {
            NotificationHelper.showError(statusLabel, e.getMessage());
        }
    }

    private String choosePaymentMethod(BillingRecord record) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Select Payment Method");
        DialogThemeHelper.apply(dialog);
        if (billingTable != null && billingTable.getScene() != null) {
            dialog.initOwner(billingTable.getScene().getWindow());
        }
        ButtonType confirmType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirmType);

        ComboBox<String> paymentMethodBox = new ComboBox<>(FXCollections.observableArrayList("Cash", "Card", "Insurance", "Other"));
        if (record != null && record.getPaymentMethod() != null && !record.getPaymentMethod().isBlank()) {
            paymentMethodBox.getSelectionModel().select(record.getPaymentMethod());
        } else {
            paymentMethodBox.getSelectionModel().select("Cash");
        }

        VBox content = new VBox(12,
                new Label("Choose a payment method for " + (record == null ? "this invoice." : record.getInvoiceNo())),
                paymentMethodBox
        );
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == confirmType) {
            return paymentMethodBox.getValue();
        }
        return null;
    }

    private VBox detailField(String label, javafx.scene.Node field) {
        Label name = new Label(label);
        name.getStyleClass().add("field-label");
        VBox box = new VBox(6, name, field);
        VBox.setVgrow(field, Priority.NEVER);
        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("invoice-section-title");
        return label;
    }

    private VBox detailBlock(String title, String value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("detail-field-name");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("detail-field-value");
        valueLabel.setWrapText(true);
        VBox box = new VBox(4, titleLabel, valueLabel);
        return box;
    }

    private Button actionButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.getStyleClass().add("billing-action-button");
        button.setOnAction(handler);
        return button;
    }

    private boolean isAuthorized() {
        return PermissionHelper.canViewBilling(Session.getCurrentUser());
    }

    private String billingStatusStyle(BillingRecord record) {
        if (record == null || record.getPaymentStatus() == null) {
            return "billing-status-unpaid";
        }
        return switch (record.getPaymentStatus().toUpperCase(Locale.ROOT)) {
            case "PAID" -> "billing-status-paid";
            case "CANCELLED" -> "billing-status-cancelled";
            default -> "billing-status-unpaid";
        };
    }

    private String paymentText(BillingRecord record) {
        if (record == null) {
            return "-";
        }
        if (record.getPaymentMethod() != null && !record.getPaymentMethod().isBlank()) {
            return record.getPaymentMethod();
        }
        if (record.isCancelled()) {
            return "Cancelled";
        }
        return "Pending";
    }

    private String safeText(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private double parseAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Amount must be a positive number.");
        }
    }

    private String formatAmount(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void configureInvoiceField(javafx.scene.Node field) {
        if (field == null) {
            return;
        }
        field.getStyleClass().add("invoice-field");
        if (field instanceof TextField textField) {
            textField.setMinHeight(42);
            textField.setPrefHeight(42);
            textField.setMinWidth(240);
            textField.setPrefWidth(280);
        } else if (field instanceof ComboBox<?> comboBox) {
            comboBox.setMinHeight(42);
            comboBox.setPrefHeight(42);
            comboBox.setMinWidth(240);
            comboBox.setPrefWidth(280);
            comboBox.setMaxWidth(Double.MAX_VALUE);
        } else if (field instanceof TextArea textArea) {
            textArea.setMinHeight(108);
            textArea.setPrefHeight(108);
            textArea.setMaxWidth(Double.MAX_VALUE);
        }
    }
}
