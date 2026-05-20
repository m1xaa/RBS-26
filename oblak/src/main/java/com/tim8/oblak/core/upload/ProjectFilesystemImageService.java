package com.tim8.oblak.core.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ProjectFilesystemImageService {

    @Value("${oblak.ext4.image-min-size-bytes}")
    private long imageMinSizeBytes;

    @Value("${oblak.ext4.image-extra-size-bytes}")
    private long imageExtraSizeBytes;

    public Path createExt4Image(Path projectDirectory, UUID projectId) {
        Path imagePath = projectDirectory.resolveSibling(projectId + ".ext4").normalize();

        try {
            Files.deleteIfExists(imagePath);
            long imageSize = calculateImageSize(projectDirectory);

            runCommand("truncate", "-s", String.valueOf(imageSize), imagePath.toString());
            runCommand("mkfs.ext4", "-q", "-F", "-d", projectDirectory.toString(), imagePath.toString());

            return imagePath;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create ext4 project image.", exception);
        }
    }

    public void deleteImage(Path imagePath) {
        if (imagePath == null) {
            return;
        }

        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete ext4 project image: " + imagePath, exception);
        }
    }

    private long calculateImageSize(Path projectDirectory) throws IOException {
        long projectSize;

        try (Stream<Path> paths = Files.walk(projectDirectory)) {
            projectSize = paths
                .filter(Files::isRegularFile)
                .mapToLong(this::sizeOf)
                .sum();
        }

        return Math.max(imageMinSizeBytes, projectSize + imageExtraSizeBytes);
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read file size: " + path, exception);
        }
    }

    private void runCommand(String... command) throws IOException {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + output);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating ext4 project image.", exception);
        }
    }
}
