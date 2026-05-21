package com.tim8.oblak.core.analysis;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class AntivirusService {

    public ClamResult scan(Path directory) {

        try {
            Process process = new ProcessBuilder(
                    "clamscan",
                    "-r",
                    directory.toString()
            )
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;

            int infected = 0;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");

                if (line.contains("Infected files:")) {
                    infected = Integer.parseInt(
                            line.replaceAll("\\D+", "")
                    );
                }
            }

            process.waitFor();

            return new ClamResult(infected, output.toString());

        } catch (Exception e) {
            return new ClamResult(-1, "ERROR: " + e.getMessage());
        }
    }
}