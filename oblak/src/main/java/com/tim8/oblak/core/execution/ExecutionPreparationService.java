package com.tim8.oblak.core.execution;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class ExecutionPreparationService {

    private static final String LIBS_DIR_NAME = "libs";

    /**
     * Instalira zavisnosti iz requirements.txt u 'libs' direktorijum unutar projekta.
     * Koristi 'pip install --target' umesto venv-a jer venv shebang-i hardkoduju
     * apsolutne putanje host-a, koje ne vaze unutar Firecracker VM-a kad se ext4
     * montira na drugu putanju.
     *
     * Ako requirements.txt ne postoji, ne radi nista.
     * Ako install padne, brise libs direktorijum i baca izuzetak.
     */
    public void prepare(Path projectDirectory) {
        Path absProjectDir = projectDirectory.toAbsolutePath().normalize();
        Path requirements = absProjectDir.resolve("requirements.txt");
        if (!Files.exists(requirements)) {
            return;
        }

        Path libsDir = absProjectDir.resolve(LIBS_DIR_NAME);

        try {
            Process installProcess = new ProcessBuilder(
                    "pip3",
                    "install",
                    "--no-cache-dir",
                    "--target", libsDir.toString(),
                    "-r", requirements.toString()
            )
                    .directory(absProjectDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(installProcess.getInputStream().readAllBytes());
            int exitCode = installProcess.waitFor();

            if (exitCode != 0) {
                deleteLibsDirectory(libsDir);
                throw new IllegalStateException(
                        "pip install failed (exit=" + exitCode + "):\n" + output
                );
            }
        } catch (IOException | InterruptedException exception) {
            deleteLibsDirectory(libsDir);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "Could not prepare execution environment.",
                    exception
            );
        }
    }
    private void deleteLibsDirectory(Path libsDir) {
        if (!Files.exists(libsDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(libsDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}