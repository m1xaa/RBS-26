package com.tim8.oblak.firecracker.assets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FirecrackerAssetService {

    @Value("${oblak.firecracker.kernel-path}")
    private String kernelPath;

    @Value("${oblak.firecracker.rootfs-path}")
    private String rootfsPath;

    @Value("${oblak.firecracker.fetch-script-path}")
    private String fetchScriptPath;

    public void ensureRequiredAssetsExist() {
        Path resolvedKernelPath = Path.of(kernelPath);
        Path resolvedRootfsPath = Path.of(rootfsPath);

        if (Files.exists(resolvedKernelPath) && Files.exists(resolvedRootfsPath)) {
            return;
        }

        Path resolvedFetchScriptPath = Path.of(fetchScriptPath);
        if (Files.notExists(resolvedFetchScriptPath)) {
            throw new IllegalStateException("Missing Firecracker fetch script: " + resolvedFetchScriptPath);
        }

        runFetchScript(resolvedFetchScriptPath);

        if (Files.notExists(resolvedKernelPath) || Files.notExists(resolvedRootfsPath)) {
            throw new IllegalStateException("Firecracker assets are still missing after running fetch script.");
        }
    }

    private void runFetchScript(Path fetchScript) {
        try {
            Process process = new ProcessBuilder("bash", fetchScript.toString())
                .redirectErrorStream(true)
                .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Firecracker fetch script failed.\n" + output);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run Firecracker fetch script.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching Firecracker assets.", exception);
        }
    }

    public Path getKernelPath() {
        return Path.of(kernelPath);
    }

    public Path getRootfsPath() {
        return Path.of(rootfsPath);
    }
}
