package com.tim8.oblak.core.analysis;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
public class AntivirusService {

    public String scan(Path directory) {

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

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

            return output.toString();

        } catch (Exception exception) {
            return "ClamAV error: " + exception.getMessage();
        }
    }
}