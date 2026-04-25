package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ControllerApp {

    @FXML private TextField inputPathField;
    @FXML private TextField outputPathField;
    @FXML private Button browseInputBtn;
    @FXML private Button browseOutputBtn;
    @FXML private Button convertBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;
    @FXML private HBox progressBox; // Контейнер для прогресс-бара

    private Stage primaryStage;
    private static final String DEFAULT_INPUT = "assets/input/hello.mp3";
    private static final String DEFAULT_OUTPUT = "assets/output/result.wav";

    /**
     * Установка ссылки на главное окно (опционально)
     */
    public void setStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    public void initialize() {
        // Установка значений по умолчанию
        inputPathField.setText(DEFAULT_INPUT);
        outputPathField.setText(DEFAULT_OUTPUT);

        // Блокировка прогресс-бара до начала конвертации
        progressBox.setVisible(false);
        progressBar.setProgress(0);

        // Привязка кнопок выбора файлов
        browseInputBtn.setOnAction(e -> chooseFile(true));
        browseOutputBtn.setOnAction(e -> chooseFile(false));
        convertBtn.setOnAction(e -> startConversion());

        logMessage("🎵 Приложение готово к работе");
        logMessage("✅ Проверка ffmpeg: " + (Mp3ToWavConverter.checkFfmpeg() ? "найден" : "не найден ⚠️"));
    }

    /**
     * Диалог выбора файла/папки
     */
    private void chooseFile(boolean isInput) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку");
        chooser.setInitialDirectory(new File(System.getProperty("user.dir")));

        File selected = chooser.showDialog(primaryStage);
        if (selected != null) {
            String fileName = isInput ? "hello.mp3" : "result.wav";
            String fullPath = selected.getAbsolutePath() + File.separator + fileName;
            if (isInput) {
                inputPathField.setText(fullPath);
            } else {
                outputPathField.setText(fullPath);
            }
            logMessage("📁 Выбрано: " + fullPath);
        }
    }

    /**
     * Запуск конвертации в отдельном потоке
     */
    private void startConversion() {
        String inputStr = inputPathField.getText().trim();
        String outputStr = outputPathField.getText().trim();

        if (inputStr.isEmpty() || outputStr.isEmpty()) {
            showAlert("Ошибка", "Пути к файлам не могут быть пустыми", Alert.AlertType.WARNING);
            return;
        }

        Path inputPath = Paths.get(inputStr);
        Path outputPath = Paths.get(outputStr);

        // Блокировка интерфейса
        setUIEnabled(false);
        progressBox.setVisible(true);
        progressBar.setProgress(-1); // неопределённый прогресс
        statusLabel.setText("🔄 Конвертация...");
        logArea.clear();

        // Запуск в background-потоке
        new Thread(() -> {
            boolean success = Mp3ToWavConverter.convertMp3ToWavWithCallback(
                    inputPath,
                    outputPath,
                    (message) -> javafx.application.Platform.runLater(() -> {
                        logMessage(message);
                        statusLabel.setText(message);
                    }),
                    (progress) -> javafx.application.Platform.runLater(() ->
                            progressBar.setProgress(progress))
            );

            // Возврат управления в UI-поток
            javafx.application.Platform.runLater(() -> {
                progressBar.setProgress(success ? 1 : 0);
                progressBox.setVisible(false);
                setUIEnabled(true);

                if (success) {
                    statusLabel.setText("✅ Готово!");
                    showAlert("Успех", "Файл успешно конвертирован!\n" + outputPath, Alert.AlertType.INFORMATION);
                } else {
                    statusLabel.setText("❌ Ошибка");
                    showAlert("Ошибка", "Конвертация не удалась. Проверьте лог.", Alert.AlertType.ERROR);
                }
            });
        }).start();
    }

    /**
     * Вывод сообщения в лог и консоль
     */
    private void logMessage(String message) {
        System.out.println(message);
        logArea.appendText(message + "\n");
    }

    /**
     * Показать диалоговое окно
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Включить/отключить элементы управления
     */
    private void setUIEnabled(boolean enabled) {
        inputPathField.setDisable(!enabled);
        outputPathField.setDisable(!enabled);
        browseInputBtn.setDisable(!enabled);
        browseOutputBtn.setDisable(!enabled);
        convertBtn.setDisable(!enabled);
    }
}