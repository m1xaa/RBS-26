package com.tim8.oblak.firecracker.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FirecrackerConfigBuilder {

    private final ObjectMapper objectMapper;

    public Path buildConfig(
            Path kernelImage,
            Path rootfsImage,
            Path projectImage,
            UUID projectId,
            String kernelBootArgs,
            Path logPath,
            Path metricsPath
    ) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("boot-source", buildBootSource(kernelImage, kernelBootArgs));
            config.put("drives", buildDrives(rootfsImage, projectImage));
            config.put("machine-config", buildMachineConfig());
            config.put("logger", buildLogger(logPath));
            config.put("metrics", buildMetrics(metricsPath));

            Path configFile = Files.createTempFile("firecracker-config-" + projectId + "-", ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);
            return configFile;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write Firecracker config file.", exception);
        }
    }

    private Map<String, Object> buildBootSource(Path kernelImage, String kernelBootArgs) {
        Map<String, Object> bootSource = new HashMap<>();
        bootSource.put("kernel_image_path", kernelImage.toAbsolutePath().toString());
        bootSource.put("boot_args", kernelBootArgs);
        return bootSource;
    }

    private List<Map<String, Object>> buildDrives(Path rootfsImage, Path projectImage) {
        return List.of(
            buildDrive("rootfs", rootfsImage, true, false),
            buildDrive("project", projectImage, false, false)
        );
    }

    private Map<String, Object> buildDrive(String driveId, Path imagePath, boolean isRootDevice, boolean isReadOnly) {
        Map<String, Object> drive = new HashMap<>();
        drive.put("drive_id", driveId);
        drive.put("path_on_host", imagePath.toAbsolutePath().toString());
        drive.put("is_root_device", isRootDevice);
        drive.put("is_read_only", isReadOnly);
        return drive;
    }

    private Map<String, Object> buildMachineConfig() {
        Map<String, Object> machineConfig = new HashMap<>();
        machineConfig.put("vcpu_count", 1);
        machineConfig.put("mem_size_mib", 1024);
        return machineConfig;
    }

    private Map<String, Object> buildLogger(Path logPath) {
        Map<String, Object> logger = new HashMap<>();
        logger.put("log_path", logPath.toAbsolutePath().toString());
        logger.put("level", "Info");
        logger.put("show_level", true);
        logger.put("show_log_origin", false);
        return logger;
    }

    private Map<String, Object> buildMetrics(Path metricsPath) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("metrics_path", metricsPath.toAbsolutePath().toString());
        return metrics;
    }
}
