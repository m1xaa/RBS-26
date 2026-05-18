package com.tim8.oblak.analysis;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class BanditService {

    public String scan(String code) {
        try {
            Process process = new ProcessBuilder("bandit", "-q", "-")
                    .start();

            process.getOutputStream().write(code.getBytes());
            process.getOutputStream().close();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            return output.toString();

        } catch (Exception e) {
            return "Bandit error: " + e.getMessage();
        }
    }
}