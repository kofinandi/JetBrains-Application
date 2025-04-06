import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.*;
import java.util.Collection;
import java.util.Collections;import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends Application {
    private CodeArea codeArea;
    private TextArea outputArea;
    private Label statusLabel;
    private Button runButton;
    private Button stopButton;
    private Process currentProcess;

    // Kotlin keywords
    private static final String[] KEYWORDS = new String[] {
            "abstract", "annotation", "as", "break", "by", "catch", "class", "companion",
            "const", "constructor", "continue", "data", "do", "else", "enum", "false",
            "final", "finally", "for", "fun", "if", "import", "in", "init", "interface",
            "internal", "is", "it", "lateinit", "null", "object", "open", "out", "override",
            "package", "private", "protected", "public", "return", "sealed", "super",
            "this", "throw", "true", "try", "typealias", "val", "var", "when", "while"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        codeArea.richChanges()
                .filter(ch -> !ch.getInserted().equals(ch.getRemoved()))
                .subscribe(change -> {
                    codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
                });
        codeArea.getStyleClass().add("code-area");

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        Font jetbrains = Font.loadFont(getClass().getResourceAsStream("/fonts/JetBrainsMono-Medium.ttf"), 14);
        outputArea = new TextArea();
        outputArea.getStyleClass().add("output-area");
        outputArea.setEditable(false);
        outputArea.setFont(jetbrains);


        runButton = new Button("Run");
        runButton.setOnAction(e -> {runScript();});
        runButton.getStyleClass().add("button");

        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopScript());
        stopButton.getStyleClass().add("button");
        stopButton.getStyleClass().add("stop-button");
        stopButton.setDisable(true);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        HBox controls = new HBox(10, statusLabel, runButton, stopButton);
        controls.getStyleClass().add("controls");
        controls.setAlignment(Pos.BASELINE_RIGHT);

        SplitPane splitPane = new SplitPane(codeArea, outputArea);
        splitPane.getStyleClass().add("split-pane");
        splitPane.setDividerPositions(0.5);

        BorderPane root = new BorderPane();
        root.setCenter(splitPane);
        root.setTop(controls);

        primaryStage.setTitle("Kotlin Script Runner");
        primaryStage.setScene(new Scene(root, 800, 600));

        Scene scene = primaryStage.getScene();
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        primaryStage.show();
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while(matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("STRING") != null ? "string" :
                                    matcher.group("COMMENT") != null ? "comment" :
                                            null; /* never happens */
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void runScript() {
        try {
            File scriptFile = new File("script.kts");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
                writer.write(codeArea.getText());
            }

            ProcessBuilder builder = new ProcessBuilder("kotlin", scriptFile.getAbsolutePath());
            builder.redirectErrorStream(true);
            currentProcess = builder.start();
            stopButton.setDisable(false);

            statusLabel.setText("Running...");
            outputArea.clear();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        Platform.runLater(() -> {
                            outputArea.appendText(finalLine + "\n");
                            if (finalLine.contains("error:")) {
                                statusLabel.setText("Error detected in script");
                            }
                        });
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                try {
                    int exitCode = currentProcess.waitFor();
                    Platform.runLater(() -> {
                        statusLabel.setText("Finished with exit code: " + exitCode);
                        stopButton.setDisable(true);
                    });
                } catch (InterruptedException ignored) {}
            }).start();
        } catch (IOException e) {
            outputArea.appendText("Error starting process: " + e.getMessage() + "\n");
        }
    }

    private void stopScript() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            statusLabel.setText("Process stopped");
            stopButton.setDisable(true);
        }
    }
}