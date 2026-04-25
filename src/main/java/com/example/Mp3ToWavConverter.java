package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * MP3 → WAV Converter
 * Рефакторинг для поддержки GUI с колбэками
 */
public class Mp3ToWavConverter {

    /**
     * Интерфейс для передачи сообщений в UI
     */
    @FunctionalInterface
    public interface LogCallback {
        void onLog(String message);
    }

    /**
     * Интерфейс для обновления прогресса
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(double value); // 0.0 - 1.0
    }

    /**
     * Проверка наличия ffmpeg (без вывода в консоль)
     */
    public static boolean checkFfmpeg() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            List<String> command = os.contains("win")
                    ? Arrays.asList("where", "ffmpeg")
                    : Arrays.asList("which", "ffmpeg");

            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start();

            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Конвертация с колбэками для UI
     */
    public static boolean convertMp3ToWavWithCallback(
            Path inputPath,
            Path outputPath,
            LogCallback logCallback,
            ProgressCallback progressCallback) {

        try {
            // Валидация входного файла
            if (!Files.exists(inputPath)) {
                String msg = "❌ Файл не найден: " + inputPath;
                if (logCallback != null) logCallback.onLog(msg);
                return false;
            }

            if (!inputPath.getFileName().toString().toLowerCase().endsWith(".mp3")) {
                if (logCallback != null)
                    logCallback.onLog("⚠️  Предупреждение: файл не имеет расширения .mp3");
            }

            // Создание директории вывода
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            // Команда ffmpeg
            List<String> command = Arrays.asList(
                    "ffmpeg", "-y",
                    "-i", inputPath.toString(),
                    "-acodec", "pcm_s16le",
                    "-ar", "44100",
                    outputPath.toString()
            );

            String fileName = inputPath.getFileName().toString();
            if (logCallback != null)
                logCallback.onLog("🔄 Конвертация: " + fileName + " → " + outputPath.getFileName());
            if (progressCallback != null)
                progressCallback.onProgress(0.2);

            // Запуск процесса
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (progressCallback != null)
                progressCallback.onProgress(0.5);

            // Чтение вывода ffmpeg (можно парсить для реального прогресса)
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Здесь можно парсить вывод ffmpeg для точного прогресса
                    // Пример: if (line.contains("time=")) { ... }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String msg = "❌ ffmpeg вернул код ошибки: " + exitCode;
                if (logCallback != null) logCallback.onLog(msg);
                return false;
            }

            if (progressCallback != null)
                progressCallback.onProgress(0.9);

            // Пост-проверка
            if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                long sizeMb = Files.size(outputPath) / (1024 * 1024);
                String msg = "✅ Успешно! Файл: " + outputPath + " (" + sizeMb + " MB)";
                if (logCallback != null) logCallback.onLog(msg);
                if (progressCallback != null) progressCallback.onProgress(1.0);
                return true;
            } else {
                String msg = "❌ Ошибка: выходной файл не создан или пустой";
                if (logCallback != null) logCallback.onLog(msg);
                return false;
            }

        } catch (IOException e) {
            String msg = "❌ Ошибка ввода/вывода: " + e.getMessage();
            if (logCallback != null) {
                logCallback.onLog(msg);
                logCallback.onLog("\n💡 Возможные причины:\n" +
                        "   1. Не установлен ffmpeg\n" +
                        "   2. Файл повреждён\n" +
                        "   3. Нет прав на запись");
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (logCallback != null)
                logCallback.onLog("❌ Процесс был прерван");
            return false;
        } catch (Exception e) {
            if (logCallback != null)
                logCallback.onLog("❌ Ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Обратная совместимость: старый метод без колбэков
     */
    @Deprecated
    public static boolean convertMp3ToWav(Path inputPath, Path outputPath) {
        return convertMp3ToWavWithCallback(inputPath, outputPath,
                System.out::println, null);
    }
}