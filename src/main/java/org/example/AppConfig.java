package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppConfig {
    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final String DEFAULT_BOT_USERNAME = "TELERSVPBOT";
    private static final long DEFAULT_ADMIN_USER_ID = 1795028355L;

    private AppConfig() {
    }

    public static Path projectRoot() {
        return PROJECT_ROOT;
    }

    public static Path dataDir() {
        String configured = System.getenv("EVENTRSVP_DATA_DIR");
        Path dir = configured == null || configured.isBlank()
            ? PROJECT_ROOT
            : Paths.get(configured).toAbsolutePath().normalize();

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create data directory: " + dir, e);
        }

        return dir;
    }

    public static String databaseUrl() {
        return "jdbc:sqlite:" + dataDir().resolve("eventrsvp.db").toAbsolutePath();
    }

    public static int webPort() {
        String configured = System.getenv("PORT");
        if (configured == null || configured.isBlank()) {
            return 8030;
        }

        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid PORT value: " + configured, e);
        }
    }

    public static String botToken() {
        String token = System.getenv("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("BOT_TOKEN is required.");
        }
        return token.trim();
    }

    public static String botUsername() {
        String username = System.getenv("BOT_USERNAME");
        return username == null || username.isBlank() ? DEFAULT_BOT_USERNAME : username.trim();
    }

    public static Set<Long> adminTelegramUserIds() {
        String configured = System.getenv("TELEGRAM_ADMIN_USER_IDS");
        if (configured == null || configured.isBlank()) {
            return Collections.singleton(DEFAULT_ADMIN_USER_ID);
        }

        Set<Long> ids = new HashSet<>();
        for (String part : configured.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid TELEGRAM_ADMIN_USER_IDS value: " + trimmed, e);
            }
        }

        if (ids.isEmpty()) {
            throw new IllegalStateException("TELEGRAM_ADMIN_USER_IDS did not contain any valid IDs.");
        }

        return Collections.unmodifiableSet(ids);
    }

    public static String defaultAdminUsername() {
        String username = System.getenv("WEB_ADMIN_USERNAME");
        return username == null || username.isBlank() ? "admin" : username.trim();
    }

    public static String defaultAdminPassword() {
        String password = System.getenv("WEB_ADMIN_PASSWORD");
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (System.getenv("RENDER_EXTERNAL_URL") != null) {
            throw new IllegalStateException("WEB_ADMIN_PASSWORD is required in hosted environments.");
        }
        return "admin123";
    }

    public static boolean secureCookies() {
        String configured = System.getenv("COOKIE_SECURE");
        if (configured != null && !configured.isBlank()) {
            return Boolean.parseBoolean(configured.trim());
        }
        return System.getenv("RENDER_EXTERNAL_URL") != null;
    }

    private static Path resolveProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("web"))) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }
}
