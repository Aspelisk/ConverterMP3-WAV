package com.example;

// Импорт компонентов JavaFX для работы с UI
import javafx.fxml.FXML;              // Аннотации для инъекции FXML-элементов
import javafx.scene.control.*;        // Базовые элементы управления (кнопки, поля, диалоги)
import javafx.scene.layout.HBox;      // Контейнер для горизонтального расположения элементов
import javafx.stage.FileChooser;      // Диалог выбора файлов
import javafx.stage.Stage;            // Основное окно приложения
import javafx.event.ActionEvent;      // Обработчик событий от UI-элементов

// Стандартные классы для работы с файловой системой
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Контроллер главного окна приложения MP3 → WAV Converter
 *
 * Этот класс связывает FXML-разметку с бизнес-логикой.
 * Все методы с @FXML вызываются автоматически при взаимодействии пользователя с интерфейсом.
 *
 * Важное правило JavaFX: обновления UI должны выполняться только в JavaFX Application Thread.
 * Для этого используется Platform.runLater() при вызове из фоновых потоков.
 */
public class ControllerApp {

    // === FXML-инъекции: связь элементов разметки с полями класса ===
    // Аннотация @FXML делает поле доступным для FXMLLoader при загрузке FXML-файла

    @FXML private TextField inputPathField;      // Поле для отображения пути к исходному файлу
    @FXML private TextField outputPathField;     // Поле для отображения пути сохранения
    @FXML private Button browseInputBtn;         // Кнопка "Обзор" для выбора MP3
    @FXML private Button browseOutputBtn;        // Кнопка "Обзор" для выбора места сохранения
    @FXML private Button convertBtn;             // Кнопка запуска конвертации
    @FXML private ProgressBar progressBar;       // Индикатор прогресса операции
    @FXML private Label statusLabel;             // Текстовый статус операции (кратко)
    @FXML private TextArea logArea;              // Многострочное поле для детального лога
    @FXML private HBox progressBox;              // Контейнер для прогресс-бара (скрывается/показывается)

    // Ссылка на основное окно приложения (нужна для модальных диалогов)
    private Stage primaryStage;

    /**
     * Установка ссылки на главное окно
     * Вызывается из MainApp после загрузки FXML
     * @param stage Основное окно JavaFX
     */
    public void setStage(Stage stage) {
        this.primaryStage = stage;
    }

    /**
     * Метод инициализации контроллера
     * Вызывается автоматически FXMLLoader после загрузки FXML и инъекции всех @FXML полей
     * Здесь настраиваем начальное состояние интерфейса
     */
    @FXML
    public void initialize() {
        // Настройка подсказок (placeholder) для полей ввода
        inputPathField.setPromptText("Выберите MP3 файл...");
        outputPathField.setPromptText("Будет установлен автоматически");

        // Скрываем блок прогресса при старте и сбрасываем индикатор
        progressBox.setVisible(false);
        progressBar.setProgress(0);

        // Логирование стартового сообщения
        logMessage("🎵 Приложение готово к работе");

        // Проверка наличия ffmpeg в системе при запуске
        // Результат сразу показываем пользователю в логе
        logMessage("✅ Проверка ffmpeg: " + (Mp3ToWavConverter.checkFfmpeg() ? "найден" : "не найден ⚠️"));
    }

    /**
     * Обработчик кнопки выбора входного файла
     * Открывает FileChooser с фильтром *.mp3 и автоматически генерирует путь для выходного файла
     * @param event Событие нажатия кнопки (автоматический параметр JavaFX)
     */
    @FXML
    public void chooseInputFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите MP3 файл для конвертации");

        // Начальная директория диалога - текущая рабочая папка приложения
        chooser.setInitialDirectory(new File(System.getProperty("user.dir")));

        // Фильтр расширений: показываем только .mp3 файлы
        FileChooser.ExtensionFilter mp3Filter =
                new FileChooser.ExtensionFilter("MP3 файлы (*.mp3)", "*.mp3");
        chooser.getExtensionFilters().add(mp3Filter);
        chooser.setSelectedExtensionFilter(mp3Filter); // Активируем фильтр по умолчанию

        // showOpenDialog блокирует выполнение до выбора файла или отмены
        // Передаем primaryStage для модальности (окно поверх главного)
        File selected = chooser.showOpenDialog(primaryStage);

        if (selected != null) { // Пользователь выбрал файл (не нажал "Отмена")
            String inputPath = selected.getAbsolutePath();
            inputPathField.setText(inputPath);

            // Автоматическая генерация имени выходного файла:
            // меняем расширение .mp3 → .wav в той же директории
            String outputFileName = generateOutputFileName(selected.getName());
            File outputFile = new File(selected.getParentFile(), outputFileName);
            outputPathField.setText(outputFile.getAbsolutePath());

            // Логирование выбранных путей для отладки и информирования пользователя
            logMessage("📥 Входной файл: " + inputPath);
            logMessage("📤 Выходной файл: " + outputFile.getAbsolutePath());
        }
    }

    /**
     * Обработчик кнопки выбора выходного файла
     * Позволяет пользователю вручную указать имя и место сохранения WAV-файла
     * @param event Событие нажатия кнопки
     */
    @FXML
    public void chooseOutputFile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить WAV файл как...");

        // Если уже выбран входной файл - предлагаем сохранить рядом с ним
        String inputPath = inputPathField.getText().trim();
        if (!inputPath.isEmpty()) {
            File inputFile = new File(inputPath);
            if (inputFile.exists()) {
                // Предлагаем имя файла на основе входного (автоматическая подстановка)
                chooser.setInitialFileName(generateOutputFileName(inputFile.getName()));
                chooser.setInitialDirectory(inputFile.getParentFile());
            }
        } else {
            // Если входной файл не выбран - начинаем с рабочей директории
            chooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        }

        // Фильтр для сохранения: показываем только *.wav
        FileChooser.ExtensionFilter wavFilter =
                new FileChooser.ExtensionFilter("WAV файлы (*.wav)", "*.wav");
        chooser.getExtensionFilters().add(wavFilter);
        chooser.setSelectedExtensionFilter(wavFilter);

        // showSaveDialog может вернуть null, если пользователь отменил выбор
        File selected = chooser.showSaveDialog(primaryStage);
        if (selected != null) {
            String path = selected.getAbsolutePath();
            // Гарантируем, что файл имеет расширение .wav (добавляем, если пользователь забыл)
            if (!path.toLowerCase().endsWith(".wav")) {
                path += ".wav";
            }
            outputPathField.setText(path);
            logMessage("📤 Выходной файл: " + path);
        }
    }

    /**
     * Основной обработчик кнопки "Конвертировать"
     * Запускает процесс конвертации в фоновом потоке, чтобы не блокировать UI
     * @param event Событие нажатия кнопки
     */
    @FXML
    public void startConversion(ActionEvent event) {
        // Получаем и очищаем пути из текстовых полей
        String inputStr = inputPathField.getText().trim();
        String outputStr = outputPathField.getText().trim();

        // === Валидация входных данных ===
        if (inputStr.isEmpty()) {
            showAlert("Ошибка", "Выберите входной MP3 файл", Alert.AlertType.WARNING);
            return; // Прерываем выполнение, если валидация не пройдена
        }
        if (outputStr.isEmpty()) {
            showAlert("Ошибка", "Укажите путь для сохранения WAV файла", Alert.AlertType.WARNING);
            return;
        }

        // Конвертируем строки путей в объекты Path (type-safe работа с файлами)
        Path inputPath = Paths.get(inputStr);
        Path outputPath = Paths.get(outputStr);

        // Дополнительная проверка существования файла (защита от ручного ввода несуществующего пути)
        if (!inputPath.toFile().exists()) {
            showAlert("Ошибка", "Входной файл не найден: " + inputPath, Alert.AlertType.ERROR);
            return;
        }

        // === Подготовка UI к длительной операции ===
        setUIEnabled(false);              // Блокируем кнопки и поля, чтобы избежать повторного запуска
        progressBox.setVisible(true);     // Показываем прогресс-бар
        progressBar.setProgress(-1);      // -1 = индетерминированный режим (анимация "бегущей строки")
        statusLabel.setText("🔄 Конвертация...");
        logArea.clear();                  // Очищаем лог от предыдущих запусков

        // === Запуск в фоновом потоке ===
        // JavaFX требует: длительные операции НЕ выполнять в JavaFX Application Thread
        // Иначе интерфейс "зависнет" до завершения операции
        new Thread(() -> {
            // Вызов метода конвертации с колбэками
            boolean success = Mp3ToWavConverter.convertMp3ToWavWithCallback(
                    inputPath,
                    outputPath,

                    // Callback для логирования:
                    // ВАЖНО: этот колбэк выполняется в потоке ffmpeg, не в UI-потоке!
                    // Поэтому все обновления UI оборачиваем в Platform.runLater()
                    (message) -> javafx.application.Platform.runLater(() -> {
                        logMessage(message);              // Добавляем сообщение в TextArea
                        statusLabel.setText(message);     // Обновляем краткий статус
                    }),

                    // Callback для прогресса:
                    // Аналогично, обновляем ProgressBar только через Platform.runLater()
                    (progress) -> javafx.application.Platform.runLater(() ->
                            progressBar.setProgress(progress))
            );

            // === Завершение операции: возврат управления в UI-поток ===
            // Этот код выполняется после завершения конвертации (успех или ошибка)
            javafx.application.Platform.runLater(() -> {
                // Финальное состояние прогресс-бара
                progressBar.setProgress(success ? 1 : 0);
                progressBox.setVisible(false);  // Скрываем блок прогресса
                setUIEnabled(true);             // Разблокируем интерфейс для новых действий

                // Обработка результата
                if (success) {
                    statusLabel.setText("✅ Готово!");
                    // Показываем успешное сообщение с путем к файлу
                    showAlert("Успех", "Файл успешно конвертирован!\n" + outputPath, Alert.AlertType.INFORMATION);
                } else {
                    statusLabel.setText("❌ Ошибка");
                    // При ошибке детали уже есть в логе, показываем общее предупреждение
                    showAlert("Ошибка", "Конвертация не удалась. Проверьте лог.", Alert.AlertType.ERROR);
                }
            });
        }).start(); // Обязательно запускаем поток! Без .start() код просто создаст объект Thread, но не выполнит его
    }

    // ========================================================================
    // === Вспомогательные методы (private, используются только внутри класса) ===
    // ========================================================================

    /**
     * Генерация имени выходного файла на основе имени входного
     * Заменяет расширение на .wav, сохраняя базовое имя файла
     * @param inputFileName Имя исходного файла (например, "song.mp3")
     * @return Имя для WAV-файла (например, "song.wav")
     */
    private String generateOutputFileName(String inputFileName) {
        // Ищем последнюю точку в имени файла (разделитель имени и расширения)
        int lastDot = inputFileName.lastIndexOf('.');
        // Если точка найдена и не в начале имени (защита от файлов типа ".hidden")
        if (lastDot > 0) {
            // Заменяем расширение, сохраняя имя до точки
            return inputFileName.substring(0, lastDot) + ".wav";
        }
        // Если расширения нет - просто добавляем .wav в конец
        return inputFileName + ".wav";
    }

    /**
     * Добавление сообщения в лог-окно и дублирование в консоль
     * @param message Текст сообщения для отображения
     */
    private void logMessage(String message) {
        // Дублируем в системную консоль (полезно при отладке через IDE)
        System.out.println(message);
        // Добавляем в TextArea с переносом строки
        // appendText безопасен для вызова из Platform.runLater()
        logArea.appendText(message + "\n");
    }

    /**
     * Показ модального диалогового окна с сообщением
     * Универсальный метод для отображения ошибок, предупреждений и информации
     * @param title Заголовок окна
     * @param content Текст сообщения
     * @param type Тип алерта (информация, предупреждение, ошибка)
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);  // Убираем второй заголовок для компактности
        alert.setContentText(content);
        // showAndWait() блокирует выполнение до закрытия пользователем (модальность)
        alert.showAndWait();
    }

    /**
     * Массовое включение/отключение элементов управления
     * Используется для блокировки интерфейса во время длительных операций
     * @param enabled true = разблокировать, false = заблокировать
     */
    private void setUIEnabled(boolean enabled) {
        // setDisable(!enabled) потому что:
        // - если enabled=true, то setDisable(false) = элемент активен
        // - если enabled=false, то setDisable(true) = элемент неактивен (серый)
        inputPathField.setDisable(!enabled);
        outputPathField.setDisable(!enabled);
        browseInputBtn.setDisable(!enabled);
        browseOutputBtn.setDisable(!enabled);
        convertBtn.setDisable(!enabled);
    }
}