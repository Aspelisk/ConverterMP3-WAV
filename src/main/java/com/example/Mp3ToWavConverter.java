package com.example;

// Классы для работы с вводом/выводом и процессами
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
// Классы для работы с файловой системой (современный API java.nio)
import java.nio.file.Files;
import java.nio.file.Path;
// Утилиты для работы с коллекциями
import java.util.Arrays;
import java.util.List;
// Функциональный интерфейс для реализации паттерна Callback
import java.util.function.Consumer;

/**
 * MP3 → WAV Converter
 * Рефакторинг для поддержки GUI с колбэками
 *
 * Класс предоставляет статические методы для конвертации аудио через ffmpeg.
 * Основная особенность: асинхронное взаимодействие с UI через функциональные интерфейсы.
 */
public class Mp3ToWavConverter {

    /**
     * Интерфейс для передачи текстовых сообщений (логов) в UI
     * @FunctionalInterface позволяет использовать лямбда-выражения и метод-референсы
     * Пример использования: (msg) -> textArea.append(msg + "\n")
     */
    @FunctionalInterface
    public interface LogCallback {
        /**
         * Вызывается при необходимости вывести сообщение пользователю
         * @param message Текст сообщения (может содержать эмодзи для наглядности)
         */
        void onLog(String message);
    }

    /**
     * Интерфейс для обновления индикатора прогресса в GUI
     * Позволяет конвертеру сообщать о ходе выполнения без привязки к конкретному UI-фреймворку
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Обновление прогресс-бара
         * @param value Значение от 0.0 (начало) до 1.0 (завершение)
         */
        void onProgress(double value); // 0.0 - 1.0
    }

    /**
     * Проверка наличия ffmpeg в системном PATH (без вывода в консоль)
     * Используется для предварительной валидации окружения перед запуском конвертации
     * @return true если утилита найдена и доступна для запуска
     */
    public static boolean checkFfmpeg() {
        try {
            // Определение ОС для выбора правильной команды поиска исполняемого файла
            String os = System.getProperty("os.name").toLowerCase();
            // Windows использует 'where', Unix-системы (Linux/macOS) используют 'which'
            List<String> command = os.contains("win")
                    ? Arrays.asList("where", "ffmpeg")
                    : Arrays.asList("which", "ffmpeg");

            // Настройка процесса: перенаправляем stdout/stderr в PIPE, чтобы не засорять консоль приложения
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start();

            // Код возврата 0 означает, что команда нашла исполняемый файл в PATH
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            // Восстанавливаем флаг прерывания потока при InterruptedException
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Основной метод конвертации с поддержкой колбэков для интеграции с GUI
     *
     * @param inputPath Путь к исходному MP3 файлу
     * @param outputPath Путь для сохранения результирующего WAV файла
     * @param logCallback Функция обратного вызова для логгирования (может быть null)
     * @param progressCallback Функция для обновления прогресса (может быть null)
     * @return true при успешной конвертации, false при ошибке
     */
    public static boolean convertMp3ToWavWithCallback(
            Path inputPath,
            Path outputPath,
            LogCallback logCallback,
            ProgressCallback progressCallback) {

        try {
            // --- 1. Валидация входных данных ---

            // Проверка существования файла на диске
            if (!Files.exists(inputPath)) {
                String msg = "❌ Файл не найден: " + inputPath;
                if (logCallback != null) logCallback.onLog(msg);
                return false;
            }

            // Мягкая проверка расширения (для информирования пользователя, не блокирует выполнение)
            if (!inputPath.getFileName().toString().toLowerCase().endsWith(".mp3")) {
                if (logCallback != null)
                    logCallback.onLog("⚠️  Предупреждение: файл не имеет расширения .mp3");
            }

            // --- 2. Подготовка окружения ---

            // Автоматическое создание родительских директорий для выходного файла, если их нет
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            // --- 3. Формирование команды ffmpeg ---
            // Параметры:
            // -y : автоматическая перезапись выходного файла без запроса подтверждения
            // -i : входной файл
            // -acodec pcm_s16le : кодек PCM (стандартный для WAV), 16 бит, little-endian
            // -ar 44100 : частота дискретизации 44.1 кГц (CD quality)
            List<String> command = Arrays.asList(
                    "ffmpeg", "-y",
                    "-i", inputPath.toString(),
                    "-acodec", "pcm_s16le",
                    "-ar", "44100",
                    outputPath.toString()
            );

            // Логирование начала процесса
            String fileName = inputPath.getFileName().toString();
            if (logCallback != null)
                logCallback.onLog("🔄 Конвертация: " + fileName + " → " + outputPath.getFileName());

            // Обновление прогресса: этап инициализации завершен (20%)
            if (progressCallback != null)
                progressCallback.onProgress(0.2);

            // --- 4. Запуск внешнего процесса ---
            ProcessBuilder pb = new ProcessBuilder(command);
            // Объединяем stderr с stdout, чтобы читать весь вывод ffmpeg из одного потока
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Прогресс: процесс запущен (50%)
            if (progressCallback != null)
                progressCallback.onProgress(0.5);

            // --- 5. Обработка вывода процесса ---
            // Читаем stdout процесса, чтобы буфер не переполнился и процесс не завис (deadlock)
            // Примечание: для реального прогресса здесь нужно парсить строки вида "time=00:01:23.45"
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // TODO: Реализовать парсинг вывода ffmpeg для точного расчета прогресса
                    // Пример логики: извлечь время обработки и разделить на общую длительность
                }
            }

            // Ожидание завершения процесса и получение кода возврата
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String msg = "❌ ffmpeg вернул код ошибки: " + exitCode;
                if (logCallback != null) logCallback.onLog(msg);
                return false;
            }

            // Прогресс: вычисления завершены, идет финализация (90%)
            if (progressCallback != null)
                progressCallback.onProgress(0.9);

            // --- 6. Пост-проверка результата ---
            // Убеждаемся, что файл физически создан и не пустой
            if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                long sizeMb = Files.size(outputPath) / (1024 * 1024);
                String msg = "✅ Успешно! Файл: " + outputPath + " (" + sizeMb + " MB)";
                if (logCallback != null) logCallback.onLog(msg);

                // Прогресс: 100%
                if (progressCallback != null) progressCallback.onProgress(1.0);
                return true;
            } else {
                String msg = "❌ Ошибка: выходной файл не создан или пустой";
                if (logCallback != null) logCallback.onLog(msg);
                return false;
            }

        } catch (IOException e) {
            // Обработка ошибок ввода-вывода (файл занят, нет прав, диск переполнен)
            String msg = "❌ Ошибка ввода/вывода: " + e.getMessage();
            if (logCallback != null) {
                logCallback.onLog(msg);
                // Подсказки для пользователя по устранению типичных проблем
                logCallback.onLog("\n💡 Возможные причины:\n" +
                        "   1. Не установлен ffmpeg или он не добавлен в PATH\n" +
                        "   2. Исходный файл поврежден или защищен от чтения\n" +
                        "   3. Нет прав на запись в целевую директорию");
            }
            return false;
        } catch (InterruptedException e) {
            // Обработка прерывания потока (например, пользователь нажал "Отмена")
            Thread.currentThread().interrupt(); // Важно: восстановить статус прерывания
            if (logCallback != null)
                logCallback.onLog("❌ Процесс был прерван пользователем или системой");
            return false;
        } catch (Exception e) {
            // Catch-all для любых других непредвиденных исключений
            if (logCallback != null)
                logCallback.onLog("❌ Ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Метод-обертка для обратной совместимости
     * Позволяет старому коду, не поддерживающему колбэки, продолжать работать
     *
     * @deprecated Используйте {@link #convertMp3ToWavWithCallback} для лучшего контроля над процессом
     */
    @Deprecated
    public static boolean convertMp3ToWav(Path inputPath, Path outputPath) {
        // Перенаправляем логи в консоль (System.out), прогресс отключен (null)
        return convertMp3ToWavWithCallback(inputPath, outputPath,
                System.out::println, null);
    }
}