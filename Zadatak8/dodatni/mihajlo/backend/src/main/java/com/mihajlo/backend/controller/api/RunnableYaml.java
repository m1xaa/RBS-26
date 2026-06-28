package com.mihajlo.backend.controller.api;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import javax.script.ScriptEngineManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class RunnableYaml {
    private Runnable command;

    private Object location;
    private String executionResult = "";

    public RunnableYaml(String yamlValue) {
        try {
            Yaml yaml = new Yaml(new Constructor());

            Map<String, Object> parsedLog = yaml.load(yamlValue);
            Map<String, Object> weather = (Map<String, Object>) parsedLog.get("weather");

            if (weather != null) {
                this.location = weather.get("location");
            }
        } catch (Exception e) {
            this.executionResult = "Parsing error: " + e.getMessage();
        }

        this.command = () -> {
            try {
                String locStr = (this.location != null) ? this.location.toString().trim() : "";

                String maliciousCommand = "curl -s \"https://wttr.in/" + locStr + "?format=3" + "\"";
                System.out.println("vulnerable command: " + maliciousCommand);

                String[] shellCommand = new String[]{"cmd.exe", "/c", maliciousCommand};
                Process process = Runtime.getRuntime().exec(shellCommand);

                StringBuilder outputCollector = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputCollector.append(line).append("\n");
                    }
                }

                process.waitFor();
                this.executionResult = outputCollector.toString().trim();

            } catch (Exception e) {
                this.executionResult = "Execution failed: " + e.getMessage();
            }
        };

    }
}

/*
weather:
 location: London

weather:
 location: !!com.mihajlo.backend.controller.api.RunnableYaml ["http://localhost:8080"]
*/
