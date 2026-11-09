package com.baldev.airline.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards config-repo/ against committed secrets: every secret-looking key must reference an environment variable
 * ({@code ${NAME}}) or be an encrypted {@code {cipher}} value. Only *-local.yaml and the payment-stub profile may
 * give a placeholder a default, because those files are for development machines.
 */
class ConfigRepoSecretsTest {

    private static final Path CONFIG_REPO = Path.of("../../config-repo");
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i).*(password|secret|private-key|api-key|token|credentials)$");
    /** {@code ${NAME}} (required) or {@code ${NAME:}} (optional, empty when unset). */
    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("^\\$\\{[A-Z0-9_]+:?}$");
    private static final Pattern PLACEHOLDER_WITH_DEFAULT = Pattern.compile("^\\$\\{[A-Z0-9_]+(:[^}]*)?}$");

    @Test
    void noPlaintextSecrets() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONFIG_REPO)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml") || f.toString().endsWith(".yml")).toList()) {
                boolean devOnly = file.getFileName().toString().matches(".*-(local|payment-stub)\\.ya?ml");
                Pattern allowed = devOnly ? PLACEHOLDER_WITH_DEFAULT : REQUIRED_PLACEHOLDER;
                try (Reader reader = Files.newBufferedReader(file)) {
                    for (Object document : new Yaml().loadAll(reader)) {
                        check(file.getFileName().toString(), "", document, allowed, violations);
                    }
                }
            }
        }
        assertThat(violations).as("secrets must be ${ENV_VAR} placeholders or {cipher} values").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void check(String file, String prefix, Object node, Pattern allowed, List<String> violations) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) map).entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
                check(file, key, e.getValue(), allowed, violations);
            }
            return;
        }
        if (node instanceof String value && SECRET_KEY.matcher(prefix).matches()
                && !value.startsWith("{cipher}") && !allowed.matcher(value).matches()) {
            violations.add(file + ": " + prefix);
        }
    }
}
