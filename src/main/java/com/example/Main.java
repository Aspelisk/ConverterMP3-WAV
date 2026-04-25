package com.example;

// === Импорт необходимых классов JavaFX ===
import javafx.application.Application;      // Базовый класс для всех JavaFX-приложений
import javafx.fxml.FXMLLoader;                // Загрузчик FXML-разметки
import javafx.scene.Parent;                   // Базовый класс для узлов сцены (корневой элемент)
import javafx.scene.Scene;                    // Класс, представляющий сцену (контейнер для UI)
import javafx.stage.Stage;                    // Класс главного окна приложения

// === Импорт стандартных классов Java ===
import java.io.IOException;                   // Обработка исключений ввода-вывода
import java.util.Objects;                     // Утилиты для безопасной работы с объектами (null-check)

/**
 * Главный класс JavaFX-приложения.
 * <p>
 * Точка входа в приложение "MP3 → WAV Converter".
 * Наследуется от Application, что обязательно для JavaFX.
 */
public class Main extends Application {

    /**
     * Основной метод жизненного цикла JavaFX.
     * Вызывается автоматически после launch().
     *
     * @param primaryStage главное окно приложения, предоставляемое системой
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // === 1. Загрузка пользовательского интерфейса из FXML ===

            // Создаём загрузчик FXML
            FXMLLoader loader = new FXMLLoader(
                    // Получаем путь к ресурсу внутри classpath
                    // Objects.requireNonNull() выбросит исключение, если файл не найден
                    // Это предотвратит скрытые ошибки при отсутствии FXML-файла
                    Objects.requireNonNull(getClass().getResource("/fxml/App.fxml"))
            );

            // Загружаем иерархию узлов из FXML и получаем корневой элемент
            Parent root = loader.load();

            // === 2. Связывание с контроллером (опционально, но полезно) ===

            // Получаем экземпляр контроллера, указанный в FXML через fx:controller
            ControllerApp controller = loader.getController();

            // Передаём контроллеру ссылку на Stage для управления окном из логики приложения
            // Например: закрытие окна, изменение заголовка, полноэкранный режим
            controller.setStage(primaryStage);

            // === 3. Настройка сцены и главного окна ===

            // Создаём сцену с корневым элементом и фиксированными размерами (600×400 пикселей)
            Scene scene = new Scene(root, 600, 400);

            // Устанавливаем заголовок окна
            primaryStage.setTitle("🎵 MP3 → WAV Converter");

            // Запрещаем пользователю изменять размер окна (интерфейс фиксирован)
            primaryStage.setResizable(false);

            // Привязываем сцену к окну
            primaryStage.setScene(scene);

            // Отображаем окно на экране
            primaryStage.show();

        } catch (IOException e) {
            // === Обработка ошибок загрузки интерфейса ===

            // Вывод полного стека вызовов для отладки
            e.printStackTrace();

            // Вывод понятного сообщения об ошибке в консоль
            System.err.println("❌ Не удалось загрузить интерфейс: " + e.getMessage());

            // 💡 Рекомендация: в продакшене можно добавить показ Alert-окна пользователю
        }
    }

    /**
     * Точка входа в приложение (стандартный main-метод Java).
     * <p>
     * Вызывает метод launch(), который:
     * 1. Инициализирует среду выполнения JavaFX
     * 2. Создаёт экземпляр класса Main
     * 3. Вызывает метод start() на потоке JavaFX Application Thread
     *
     * @param args аргументы командной строки (передаются в JavaFX)
     */
    public static void main(String[] args) {
        launch(args);
    }
}