package ch.heigvd.gui;

import ch.heigvd.ImageToolBox;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import picocli.CommandLine;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI "user friendly" pour générer des pages A4 de logos (PDF) sans ligne de commande.
 *
 * Hypothèse: tes options tileA4 existent déjà (mirror, boost-colors, rect sizes, etc.).
 * Optionnel: si tu as ajouté --logo-background / --page-background, la GUI les pilotera aussi.
 */
public final class ImageToolBoxApp extends Application {

    private File inputFile;
    private File outputFile;

    // UI
    private Label inputPath = new Label("(aucun fichier)");
    private Label outputPath = new Label("(aucune sortie)");
    private TextArea logs = new TextArea();
    private Button btnGenerate = new Button("Générer");
    private Button btnOpen = new Button("Ouvrir le PDF");
    private ProgressIndicator spinner = new ProgressIndicator();

    // Helpers
    private static Spinner<Double> dblSpinner(double min, double max, double initial, double step) {
        Spinner<Double> sp = new Spinner<>();
        sp.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step));
        sp.setEditable(true);
        sp.getEditor().setPrefColumnCount(7);
        return sp;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> sp = new Spinner<>();
        sp.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
        sp.setEditable(true);
        sp.getEditor().setPrefColumnCount(7);
        return sp;
    }

    private static void showWarning(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    enum BgMode { NONE, WHITE, TRANSPARENT, CUSTOM_HEX }

    @Override
    public void start(Stage stage) {
        stage.setTitle("ImageToolBox – Générateur A4");

        // --- Inputs
        Button pickInput = new Button("Choisir un logo…");
        pickInput.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choisir un logo (PNG/JPG)");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp")
            );
            File f = fc.showOpenDialog(stage);
            if (f != null) {
                inputFile = f;
                inputPath.setText(f.getAbsolutePath());
                suggestOutputIfEmpty();
                validateForm();
            }
        });

        Button pickOutput = new Button("Choisir sortie…");
        pickOutput.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            fc.setInitialFileName("a4_logos.pdf");
            File f = fc.showSaveDialog(stage);
            if (f != null) {
                outputFile = ensurePdfExtension(f);
                outputPath.setText(outputFile.getAbsolutePath());
                validateForm();
            }
        });

        // --- Core options (spinners)
        ChoiceBox<String> shape = new ChoiceBox<>();
        shape.getItems().addAll("rect", "circle");
        shape.setValue("rect");

        Spinner<Double> rectWcm = dblSpinner(0.1, 30.0, 1.9, 0.1);
        Spinner<Double> rectHcm = dblSpinner(0.1, 30.0, 1.9, 0.1);

        Spinner<Double> gapMm = dblSpinner(0.0, 50.0, 10.0, 0.5);
        Spinner<Double> marginMm = dblSpinner(0.0, 30.0, 7.0, 0.5);
        Spinner<Integer> dpi = intSpinner(72, 1200, 300, 10);

        // --- Toggles
        CheckBox mirrorH = new CheckBox("Miroir horizontal");
        mirrorH.setSelected(true);
        CheckBox mirrorV = new CheckBox("Miroir vertical");
        CheckBox boostColors = new CheckBox("Couleurs plus vives");
        Spinner<Double> boostFactor = dblSpinner(1.0, 3.0, 1.0, 0.1);
        boostFactor.setDisable(true);
        boostColors.selectedProperty().addListener((obs, o, on) -> boostFactor.setDisable(!on));

        // --- Background options (optionnelles)
        // Logo background (aplatit le PNG pour éviter halos)
        ChoiceBox<BgMode> logoBgMode = new ChoiceBox<>();
        logoBgMode.getItems().addAll(BgMode.NONE, BgMode.WHITE, BgMode.TRANSPARENT, BgMode.CUSTOM_HEX);
        logoBgMode.setValue(BgMode.NONE);

        TextField logoBgHex = new TextField("#FFFFFF");
        logoBgHex.setDisable(true);
        logoBgMode.getSelectionModel().selectedItemProperty().addListener((obs, o, v) -> {
            logoBgHex.setDisable(v != BgMode.CUSTOM_HEX);
        });

        // Page background (utile surtout si tu sors en PNG; si PDF, ça peut rester blanc)
        ChoiceBox<BgMode> pageBgMode = new ChoiceBox<>();
        pageBgMode.getItems().addAll(BgMode.NONE, BgMode.WHITE, BgMode.TRANSPARENT, BgMode.CUSTOM_HEX);
        pageBgMode.setValue(BgMode.NONE);

        TextField pageBgHex = new TextField("#FFFFFF");
        pageBgHex.setDisable(true);
        pageBgMode.getSelectionModel().selectedItemProperty().addListener((obs, o, v) -> {
            pageBgHex.setDisable(v != BgMode.CUSTOM_HEX);
        });

        // --- Presets
        Button presetMeringue = new Button("Preset meringues (1.8 cm)");
        presetMeringue.setOnAction(e -> {
            shape.setValue("rect");
            rectWcm.getValueFactory().setValue(1.8);
            rectHcm.getValueFactory().setValue(1.8);
            gapMm.getValueFactory().setValue(10.0);
            marginMm.getValueFactory().setValue(7.0);
            dpi.getValueFactory().setValue(300);
            mirrorH.setSelected(true);
            boostColors.setSelected(false);
            logoBgMode.setValue(BgMode.WHITE);
            validateForm();
        });

        // --- Logs
        logs.setEditable(false);
        logs.setWrapText(true);
        logs.setPrefRowCount(12);

        spinner.setVisible(false);
        spinner.setMaxSize(20, 20);

        btnGenerate.setOnAction(e -> runGeneration(
                shape.getValue(),
                rectWcm.getValue(),
                rectHcm.getValue(),
                gapMm.getValue(),
                marginMm.getValue(),
                dpi.getValue(),
                mirrorH.isSelected(),
                mirrorV.isSelected(),
                boostColors.isSelected() ? boostFactor.getValue() : null,
                logoBgMode.getValue(),
                logoBgHex.getText().trim(),
                pageBgMode.getValue(),
                pageBgHex.getText().trim()
        ));

        btnOpen.setDisable(true);
        btnOpen.setOnAction(e -> {
            if (outputFile == null || !outputFile.exists()) return;

            // Important: ne jamais bloquer le thread JavaFX (sinon "freeze" de la fenêtre).
            javafx.concurrent.Task<Void> openTask = new javafx.concurrent.Task<>() {
                @Override protected Void call() throws Exception {
                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().open(outputFile);
                            return null;
                        }
                    } catch (Exception ignored) {
                        // fallback ci-dessous
                    }

                    // Fallback Windows: "start" via cmd
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("cmd", "/c", "start", "", outputFile.getAbsolutePath()).start();
                    } else {
                        // Fallback Linux
                        new ProcessBuilder("xdg-open", outputFile.getAbsolutePath()).start();
                    }
                    return null;
                }
            };

            openTask.setOnFailed(ev -> javafx.application.Platform.runLater(() ->
                    showWarning("Impossible d'ouvrir le fichier: " +
                            (openTask.getException() != null ? openTask.getException().getMessage() : "inconnu"))));

            Thread t = new Thread(openTask, "OpenPdf-Task");
            t.setDaemon(true);
            t.start();
        });

        Button btnCopyCmd = new Button("Copier la commande");
        btnCopyCmd.setOnAction(e -> {
            String cmd = buildCommandPreview(
                    shape.getValue(),
                    rectWcm.getValue(),
                    rectHcm.getValue(),
                    gapMm.getValue(),
                    marginMm.getValue(),
                    dpi.getValue(),
                    mirrorH.isSelected(),
                    mirrorV.isSelected(),
                    boostColors.isSelected() ? boostFactor.getValue() : null,
                    logoBgMode.getValue(),
                    logoBgHex.getText().trim(),
                    pageBgMode.getValue(),
                    pageBgHex.getText().trim()
            );
            ClipboardContent cc = new ClipboardContent();
            cc.putString(cmd);
            Clipboard.getSystemClipboard().setContent(cc);
            showInfo("Commande copiée dans le presse-papier.");
        });

        // --- Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int r = 0;
        grid.add(new Label("Logo"), 0, r);
        grid.add(new HBox(10, pickInput, inputPath), 1, r++);

        grid.add(new Label("Sortie"), 0, r);
        grid.add(new HBox(10, pickOutput, outputPath), 1, r++);

        grid.add(new Label("Forme"), 0, r);
        grid.add(shape, 1, r++);

        grid.add(new Label("Largeur (cm)"), 0, r);
        grid.add(rectWcm, 1, r++);

        grid.add(new Label("Hauteur (cm)"), 0, r);
        grid.add(rectHcm, 1, r++);

        grid.add(new Label("Espace (mm)"), 0, r);
        grid.add(gapMm, 1, r++);

        grid.add(new Label("Marge (mm)"), 0, r);
        grid.add(marginMm, 1, r++);

        grid.add(new Label("DPI"), 0, r);
        grid.add(dpi, 1, r++);

        HBox toggles = new HBox(14, mirrorH, mirrorV, boostColors, new Label("facteur:"), boostFactor);
        toggles.setAlignment(Pos.CENTER_LEFT);

        GridPane bgGrid = new GridPane();
        bgGrid.setHgap(10);
        bgGrid.setVgap(10);
        bgGrid.add(new Label("Fond logo"), 0, 0);
        bgGrid.add(new HBox(10, logoBgMode, logoBgHex), 1, 0);
        bgGrid.add(new Label("Fond page"), 0, 1);
        bgGrid.add(new HBox(10, pageBgMode, pageBgHex), 1, 1);

        VBox left = new VBox(12,
                grid,
                new Separator(),
                toggles,
                new Separator(),
                bgGrid,
                new Separator(),
                presetMeringue
        );

        HBox actions = new HBox(10, btnGenerate, spinner, btnOpen, btnCopyCmd);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Astuce: à l'impression, choisir “Taille réelle / 100%” (pas “Adapter à la page”).");

        VBox root = new VBox(12, left, actions, hint, new Label("Logs:"), logs);
        root.setPadding(new Insets(14));

        Scene scene = new Scene(root, 860, 620);
        stage.setScene(scene);

        // Permet de fermer proprement même si une tâche tourne.
        stage.setOnCloseRequest(ev -> {
            javafx.application.Platform.exit();
            System.exit(0);
        });

        stage.show();

        validateForm();
    }

    private void suggestOutputIfEmpty() {
        if (inputFile == null) return;
        if (outputFile != null) return;

        String base = inputFile.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        // Par défaut: <working dir>/image/output/<nomLogo>.pdf
        File outDir = new File(System.getProperty("user.dir"), "image" + File.separator + "output");
        try { Files.createDirectories(outDir.toPath()); } catch (Exception ignored) {}

        File suggested = new File(outDir, base + ".pdf");
        outputFile = suggested;
        outputPath.setText(suggested.getAbsolutePath());
    }

    private static File ensurePdfExtension(File f) {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".pdf")) return f;
        return new File(f.getParentFile(), f.getName() + ".pdf");
    }

    private void validateForm() {
        boolean ok = (inputFile != null && inputFile.exists() && outputFile != null);
        btnGenerate.setDisable(!ok);
    }

    private void runGeneration(
            String shape,
            double rectWcm,
            double rectHcm,
            double gapMm,
            double marginMm,
            int dpi,
            boolean mirrorH,
            boolean mirrorV,
            Double boostFactorOrNull,
            BgMode logoBgMode, String logoBgHex,
            BgMode pageBgMode, String pageBgHex
    ) {
        if (inputFile == null || !inputFile.exists()) {
            showWarning("Choisis un logo valide.");
            return;
        }
        if (outputFile == null) {
            showWarning("Choisis un fichier de sortie.");
            return;
        }

        // petit garde-fou: évite des PDF verrouillés / répertoires sans droits
        try {
            Files.createDirectories(outputFile.toPath().toAbsolutePath().getParent());
        } catch (Exception ignored) {}

        btnGenerate.setDisable(true);
        btnOpen.setDisable(true);
        spinner.setVisible(true);
        logs.setText("");

        Task<RunResult> task = new Task<>() {
            @Override protected RunResult call() throws Exception {
                String[] args = buildArgs(
                        inputFile, outputFile,
                        shape, rectWcm, rectHcm, gapMm, marginMm, dpi,
                        mirrorH, mirrorV, boostFactorOrNull,
                        logoBgMode, logoBgHex, pageBgMode, pageBgHex
                );

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);

                CommandLine cmd = new CommandLine(new ImageToolBox());
                cmd.setOut(pw);
                cmd.setErr(pw);

                int exit = cmd.execute(args);
                pw.flush();

                return new RunResult(exit, baos.toString(StandardCharsets.UTF_8));
            }
        };

        task.setOnSucceeded(e -> {
            RunResult rr = task.getValue();
            logs.setText(rr.output
                    + "\n\nExit code: " + rr.exitCode
                    + "\nRappel: imprimer à 100% (sans “fit to page”).\n");
            spinner.setVisible(false);
            btnGenerate.setDisable(false);
            btnOpen.setDisable(rr.exitCode != 0 || outputFile == null || !outputFile.exists());
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            logs.setText("Erreur:\n" + (ex != null ? ex.toString() : "inconnue"));
            spinner.setVisible(false);
            btnGenerate.setDisable(false);
        });

        Thread t = new Thread(task, "ImageToolBoxGUI-Task");
        t.setDaemon(true);
        t.start();
    }

    private static String normalizeHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;
        // accepte #RRGGBB ou #AARRGGBB
        if (!(s.length() == 7 || s.length() == 9)) return null;
        return s.toUpperCase();
    }

    private static String bgArg(BgMode mode, String hex, String whiteName) {
        if (mode == null || mode == BgMode.NONE) return null;
        return switch (mode) {
            case WHITE -> whiteName;
            case TRANSPARENT -> "transparent";
            case CUSTOM_HEX -> normalizeHex(hex);
            default -> null;
        };
    }

    private static String[] buildArgs(
            File in, File out,
            String shape,
            double rectWcm, double rectHcm,
            double gapMm, double marginMm, int dpi,
            boolean mirrorH, boolean mirrorV,
            Double boostFactorOrNull,
            BgMode logoBgMode, String logoBgHex,
            BgMode pageBgMode, String pageBgHex
    ) {
        List<String> a = new ArrayList<>();

        a.add("-i"); a.add(in.getAbsolutePath());
        a.add("-o"); a.add(out.getAbsolutePath());

        a.add("tileA4");

        a.add("--shape"); a.add(shape);

        a.add("--dpi"); a.add(Integer.toString(dpi));
        a.add("--gap-mm"); a.add(Double.toString(gapMm));
        a.add("--margin-mm"); a.add(Double.toString(marginMm));

        if ("rect".equalsIgnoreCase(shape)) {
            a.add("--rect-width-cm"); a.add(Double.toString(rectWcm));
            a.add("--rect-height-cm"); a.add(Double.toString(rectHcm));
        }

        if (mirrorH) a.add("--mirror-horizontal");
        if (mirrorV) a.add("--mirror-vertical");

        if (boostFactorOrNull != null) {
            a.add("--boost-colors");
            a.add(Double.toString(boostFactorOrNull));
        }

        // options "fond" si ton TileA4 les supporte:
        String logoBg = bgArg(logoBgMode, logoBgHex, "white");
        if (logoBg != null) { a.add("--logo-background"); a.add(logoBg); }

        String pageBg = bgArg(pageBgMode, pageBgHex, "white");
        if (pageBg != null) { a.add("--page-background"); a.add(pageBg); }

        return a.toArray(new String[0]);
    }

    private String buildCommandPreview(
            String shape,
            double rectWcm,
            double rectHcm,
            double gapMm,
            double marginMm,
            int dpi,
            boolean mirrorH,
            boolean mirrorV,
            Double boostFactorOrNull,
            BgMode logoBgMode, String logoBgHex,
            BgMode pageBgMode, String pageBgHex
    ) {
        if (inputFile == null || outputFile == null) return "(choisis un logo + une sortie)";
        String[] args = buildArgs(
                inputFile, outputFile,
                shape, rectWcm, rectHcm, gapMm, marginMm, dpi,
                mirrorH, mirrorV, boostFactorOrNull,
                logoBgMode, logoBgHex, pageBgMode, pageBgHex
        );
        // Affiche une commande "humaine" (en supposant que tu exécutes le jar shaded)
        StringBuilder sb = new StringBuilder("java -jar ImageToolBox.jar ");
        for (String s : args) {
            if (s.contains(" ")) sb.append('"').append(s).append('"');
            else sb.append(s);
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private record RunResult(int exitCode, String output) {}
}
