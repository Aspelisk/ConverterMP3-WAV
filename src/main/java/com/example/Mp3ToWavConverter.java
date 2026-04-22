package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * MP3 -> WAV Converter
 * Простой конвертер на основе ffmpeg через ProcessBuilder
 *
 * Требования:
 * Установленный ffmpeg в системном PATH
 * Java 11+
 */
public class Mp3ToWavConverter {

    // === Настройки путей ===
    // Базовая директория определяется относительно текущего рабочего каталога (user.dir)
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));

    // Пути к папкам с исходными и результирующими файлами
    private static final Path INPUT_DIR = BASE_DIR.resolve("assets").resolve("input");
    private static final Path OUTPUT_DIR = BASE_DIR.resolve("assets").resolve("output");

    // Имена файлов по умолчанию (можно изменить под свои нужды)
    private static final String INPUT_FILE_NAME = "hello.mp3";
    private static final String OUTPUT_FILE_NAME = "result.wav";


    /**
     * Проверяет, доступен ли ffmpeg в системе
     * @return true если команда найдена в PATH, false иначе
     */
    public static boolean checkFfmpeg() {
        try {
            // Определяем ОС для выбора утилиты поиска: 'where' для Windows, 'which' для Unix-систем
            String os = System.getProperty("os.name").toLowerCase();
            List<String> command = os.contains("win")
                    ? Arrays.asList("where", "ffmpeg")
                    : Arrays.asList("which", "ffmpeg");

            // Запускаем процесс поиска с перенаправлением вывода в PIPE (чтобы не засорять консоль)
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start();

            // Ожидаем завершения и проверяем код возврата: 0 = успех (ffmpeg найден)
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            // Любая ошибка при проверке интерпретируется как "ffmpeg не найден"
            return false;
        }
    }


    /**
     * Конвертирует MP3 в WAV с использованием ffmpeg
     * @param inputPath путь к исходному файлу .mp3
     * @param outputPath путь для сохранения .wav
     * @return true если успешно, false если ошибка
     */
    public static boolean convertMp3ToWav(Path inputPath, Path outputPath) {
        try {
            // 1. Валидация: проверяем существование входного файла
            if (!Files.exists(inputPath)) {
                System.err.println("❌ Файл не найден: " + inputPath);
                return false;
            }

            // 2. Предупреждение, если расширение файла не .mp3 (не блокирующее)
            if (!inputPath.getFileName().toString().toLowerCase().endsWith(".mp3")) {
                System.out.println("⚠️  Предупреждение: файл не имеет расширения .mp3");
            }

            // 3. Создаём директорию для вывода, если она ещё не существует
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            // 4. Формируем команду ffmpeg с параметрами:
            List<String> command = Arrays.asList(
                    "ffmpeg", "-y",                    // -y: автоматически перезаписывать выходной файл
                    "-i", inputPath.toString(),        // -i: указать входной файл
                    "-acodec", "pcm_s16le",            // -acodec: кодек аудио — 16-bit PCM (стандарт для WAV)
                    "-ar", "44100",                    // -ar: частота дискретизации 44.1 kHz (CD-quality)
                    outputPath.toString()              // выходной файл
            );

            System.out.println("🔄 Конвертация: " + inputPath.getFileName() + " → " + outputPath.getFileName());

            // 5. Настраиваем и запускаем процесс
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Объединяем stderr и stdout для упрощённого логирования
            Process process = pb.start();

            // 6. Читаем вывод процесса (чтобы буфер не переполнился и процесс не завис)
            // Вывод можно раскомментировать для отладки ffmpeg
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // System.out.println("ffmpeg: " + line);
                }
            }

            // 7. Ожидаем завершения процесса и проверяем код возврата
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("❌ ffmpeg вернул код ошибки: " + exitCode);
                return false;
            }

            // 8. Пост-проверка: убедимся, что файл создан и не пустой
            if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                long sizeMb = Files.size(outputPath) / (1024 * 1024);
                System.out.println("✅ Успешно! Файл сохранён: " + outputPath);
                System.out.printf("📦 Размер: %d MB%n", sizeMb);
                return true;
            } else {
                System.err.println("❌ Ошибка: выходной файл не создан или пустой");
                return false;
            }

        } catch (IOException e) {
            // Обработка ошибок ввода-вывода (файлы, права доступа, сеть)
            System.err.println("❌ Ошибка ввода/вывода: " + e.getMessage());
            printPossibleCauses();
            return false;
        } catch (InterruptedException e) {
            // Восстанавливаем флаг прерывания потока
            Thread.currentThread().interrupt();
            System.err.println("❌ Процесс был прерван: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // Обработка любых других непредвиденных исключений
            System.err.println("❌ Ошибка при конвертации: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            printPossibleCauses();
            return false;
        }
    }


    /**
     * Выводит подсказки по возможным причинам ошибок для пользователя
     */
    private static void printPossibleCauses() {
        System.out.println("\n💡 Возможные причины:");
        System.out.println("   1. Не установлен ffmpeg (см. инструкцию ниже)");
        System.out.println("   2. Файл hello.mp3 повреждён или в неподдерживаемом формате");
        System.out.println("   3. Нет прав на запись в папку output/");
    }


    /**
     * Точка входа в программу
     */
    public static void main(String[] args) {
        System.out.println("🎵 MP3 → WAV Converter");
        System.out.println("=".repeat(40));

        // Шаг 1: Проверка наличия ffmpeg в системе
        if (!checkFfmpeg()) {
            System.out.println("⚠️  WARNING: ffmpeg не найден в PATH!");
            System.out.println("\n🔧 Как установить:");
            System.out.println("   • Windows: https://ffmpeg.org/download.html");
            System.out.println("              → Скачать build → распаковать → добавить bin в PATH");
            System.out.println("   • Linux:   sudo apt install ffmpeg");
            System.out.println("   • macOS:   brew install ffmpeg");
            System.out.println("\n❗ Без ffmpeg конвертация невозможна!\n");
        }

        // Шаг 2: Формируем полные пути к файлам на основе настроек
        Path inputFile = INPUT_DIR.resolve(INPUT_FILE_NAME);
        Path outputFile = OUTPUT_DIR.resolve(OUTPUT_FILE_NAME);

        // Шаг 3: Запуск процесса конвертации
        boolean success = convertMp3ToWav(inputFile, outputFile);

        // Шаг 4: Вывод итогового статуса и завершение программы с соответствующим кодом
        System.out.println("=".repeat(40));
        if (success) {
            System.out.println("🎉 Готово! Проверьте папку: assets/output/");
            System.exit(0); // Успешное завершение
        } else {
            System.out.println("💥 Конвертация не удалась.");
            System.exit(1); // Завершение с ошибкой
        }
    }
}