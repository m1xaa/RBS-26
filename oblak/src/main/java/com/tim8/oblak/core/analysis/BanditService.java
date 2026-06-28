package com.tim8.oblak.core.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class BanditService {

    private final ObjectMapper mapper = new ObjectMapper();

    public BanditResult scan(String code) {

        try {
            Process process = new ProcessBuilder(
                    "bandit",
                    "-q",
                    "-f",
                    "json",
                    "-"
            ).start();

            process.getOutputStream().write(code.getBytes());
            process.getOutputStream().close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            JsonNode root = mapper.readTree(output.toString());

            int high = root.path("metrics")
                    .path("_totals")
                    .path("SEVERITY.HIGH")
                    .asInt();

            int medium = root.path("metrics")
                    .path("_totals")
                    .path("SEVERITY.MEDIUM")
                    .asInt();

            int low = root.path("metrics")
                    .path("_totals")
                    .path("SEVERITY.LOW")
                    .asInt();

            return new BanditResult(high, medium, low, output.toString());

        } catch (Exception e) {
            return new BanditResult(0, 0, 0, "ERROR: " + e.getMessage());
        }
    }
}