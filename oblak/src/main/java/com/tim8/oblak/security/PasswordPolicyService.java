package com.tim8.oblak.security;

import com.tim8.oblak.exception.PasswordValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PasswordPolicyService {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private final int minLength;
    private final int maxLength;
    private final Set<String> commonPasswords;

    public PasswordPolicyService(
            @Value("${oblak.password.min-length:8}") int minLength,
            @Value("${oblak.password.max-length:128}") int maxLength
    ) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.commonPasswords = loadCommonPasswords();
    }

    /**
     * Validira lozinku po setu pravila. Baca PasswordValidationException
     * sa svim greskama ako lozinka ne ispunjava bilo koje pravilo.
     */
    public void validate(String password, String username) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isBlank()) {
            errors.add("Lozinka ne sme biti prazna.");
            throw new PasswordValidationException(errors);
        }

        if (password.length() < minLength) {
            errors.add("Lozinka mora imati najmanje " + minLength + " karaktera.");
        }
        if (password.length() > maxLength) {
            errors.add("Lozinka ne sme imati vise od " + maxLength + " karaktera.");
        }
        if (!UPPER.matcher(password).find()) {
            errors.add("Lozinka mora sadrzati bar jedno veliko slovo (A-Z).");
        }
        if (!LOWER.matcher(password).find()) {
            errors.add("Lozinka mora sadrzati bar jedno malo slovo (a-z).");
        }
        if (!DIGIT.matcher(password).find()) {
            errors.add("Lozinka mora sadrzati bar jednu cifru (0-9).");
        }
        if (!SPECIAL.matcher(password).find()) {
            errors.add("Lozinka mora sadrzati bar jedan specijalni karakter (!@#$ itd).");
        }
        if (hasRepeatedChars(password, 4)) {
            errors.add("Lozinka ne sme imati 4 ili vise uzastopnih istih karaktera.");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase().contains(username.toLowerCase())) {
            errors.add("Lozinka ne sme da sadrzi korisnicko ime.");
        }
        if (commonPasswords.contains(password.toLowerCase())) {
            errors.add("Lozinka je previse cesta. Izaberi nesto manje predvidivo.");
        }

        if (!errors.isEmpty()) {
            throw new PasswordValidationException(errors);
        }
    }

    private boolean hasRepeatedChars(String password, int threshold) {
        int count = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                count++;
                if (count >= threshold) return true;
            } else {
                count = 1;
            }
        }
        return false;
    }

    /**
     * Ucitava listu cestih lozinki iz resources/common-passwords.txt.
     * Fajl treba da ima po jednu lozinku u liniji.
     */
    private Set<String> loadCommonPasswords() {
        Set<String> set = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("passwords/common-passwords.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    set.add(line);
                }
            }
        } catch (IOException e) {
            // Fajl ne postoji - nije fatal, samo upozorenje
            System.err.println("Upozorenje: common-passwords.txt nije pronadjen, " +
                    "preskacem proveru cestih lozinki.");
        }
        return set;
    }
}