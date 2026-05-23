package com.tim8.oblak.core.execution;

import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.firecracker.assets.FirecrackerConfigBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FirecrackerOrchestrator {

    private static final String PROGRAM_STDOUT_BEGIN = "[agent] program_stdout_begin";
    private static final String PROGRAM_STDOUT_END = "[agent] program_stdout_end";
    private static final String PROGRAM_STDERR_BEGIN = "[agent] program_stderr_begin";
    private static final String PROGRAM_STDERR_END = "[agent] program_stderr_end";
    private final FirecrackerConfigBuilder configBuilder;

    @Value("${oblak.firecracker.max-execution-time-seconds:20}")
    private long maxExecutionTimeSeconds;

    public ExecutionResult execute(
            Path kernelImage,
            Path rootfsImage,
            Path projectImage,
            ProjectMetadata metadata
    ) {
        UUID projectId = metadata.getId();
        Path workingDirectory = createTempDirectory(projectId);
        Path logPath = workingDirectory.resolve("firecracker.log");
        Path metricsPath = workingDirectory.resolve("firecracker-metrics.log");

        String bootArgs = buildBootArgs(metadata);
        Path configFile = configBuilder.buildConfig(kernelImage, rootfsImage, projectImage, projectId, bootArgs, logPath, metricsPath);
        Path apiSocket = workingDirectory.resolve("firecracker-api.sock");

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "bash",
                    "-lc",
                    String.format("firecracker --api-sock %s --config-file %s", apiSocket.toAbsolutePath(), configFile.toAbsolutePath())
            );
            process = processBuilder.start();
            final Process firecrackerProcess = process;

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            Future<String> stdoutCapture = executorService.submit(() -> readStream(firecrackerProcess.getInputStream()));
            Future<String> stderrCapture = executorService.submit(() -> readStream(firecrackerProcess.getErrorStream()));

            boolean finished = process.waitFor(maxExecutionTimeSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ExecutionTimeoutException(
                        "Project execution exceeded the maximum allowed time of " + maxExecutionTimeSeconds + " seconds."
                );
            }

            String stdout = stdoutCapture.get(10, TimeUnit.SECONDS);
            System.out.println("Firecracker stdout: " + stdout);
            String stderr = stderrCapture.get(10, TimeUnit.SECONDS);
            System.out.println("Firecracker stderr: " + stderr);
            int exitCode = finished ? process.exitValue() : -1;

            executorService.shutdownNow();
            return new ExecutionResult(
                    exitCode,
                    stdout,
                    stderr,
                    extractSection(stdout, PROGRAM_STDOUT_BEGIN, PROGRAM_STDOUT_END),
                    extractSection(stdout, PROGRAM_STDERR_BEGIN, PROGRAM_STDERR_END),
                    logPath.toAbsolutePath().toString()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to launch or collect Firecracker execution.", exception);
        } finally {
            cleanTempFile(configFile);
            cleanTempDirectoryIfEmpty(workingDirectory);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Path createTempDirectory(UUID projectId) {
        try {
            return Files.createTempDirectory("firecracker-" + projectId + "-");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create temporary Firecracker working directory.", exception);
        }
    }

    private String buildBootArgs(ProjectMetadata metadata) {
        String projectRoot = metadata.getWorkingDirectory() == null ? "." : metadata.getWorkingDirectory();
        String rootFile = metadata.getRootFile() == null ? "main.py" : metadata.getRootFile();

        return String.join(" ", List.of(
                "console=ttyS0",
                "reboot=k",
                "panic=1",
                "pci=off",
                "root=/dev/vda",
                "rw",
                "rootwait",
                "init=/usr/local/bin/agent-runner",
                "PROJECT_DISK=/dev/vdb",
                "PROJECT_MOUNT=/mnt/project",
                "PROJECT_ROOT=" + projectRoot,
                "ROOT_FILE=" + rootFile
        ));
    }

    private String readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        inputStream.transferTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private String extractSection(String output, String startMarker, String endMarker) {
        int start = output.indexOf(startMarker);
        if (start < 0) {
            return "";
        }

        start += startMarker.length();
        if (start < output.length() && output.charAt(start) == '\n') {
            start++;
        }

        int end = output.indexOf(endMarker, start);
        if (end < 0) {
            return "";
        }

        return output.substring(start, end).stripTrailing();
    }

    private void cleanTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void cleanTempDirectoryIfEmpty(Path directory) {
        try {
            if (Files.isDirectory(directory) && Files.list(directory).findAny().isEmpty()) {
                Files.delete(directory);
            }
        } catch (IOException ignored) {
        }
    }
}
