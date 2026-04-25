package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventWebServer {
    private static Connection dbConnection;
    private static final int PORT = AppConfig.webPort();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<String, SessionInfo> SESSIONS = new ConcurrentHashMap<>();
    private static final long SESSION_HOURS = 24;
    private static final String SESSION_COOKIE = "eventrsvp_session";
    private static final Path PROJECT_ROOT = AppConfig.projectRoot();
    private static final String DATABASE_URL = AppConfig.databaseUrl();

    private static class SessionInfo {
        private final String username;
        private final String role;
        private final LocalDateTime expiresAt;

        private SessionInfo(String username, String role, LocalDateTime expiresAt) {
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }
    }

    public static void main(String[] args) throws Exception {
        start();
    }

    public static HttpServer start() throws IOException {
        initDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", EventWebServer::handleStaticFiles);
        server.createContext("/api/health", EventWebServer::handleHealthCheck);

        server.createContext("/api/auth/register", EventWebServer::handleRegister);
        server.createContext("/api/auth/login", EventWebServer::handleLogin);
        server.createContext("/api/auth/logout", EventWebServer::handleLogout);
        server.createContext("/api/auth/me", EventWebServer::handleCurrentUser);

        server.createContext("/api/events", EventWebServer::handleGetEvents);
        server.createContext("/api/events/next", EventWebServer::handleNextEvent);
        server.createContext("/api/events/recommend", EventWebServer::handleRecommendations);
        server.createContext("/api/events/my", EventWebServer::handleMyEvents);
        server.createContext("/api/events/search", EventWebServer::handleSearchEvents);
        server.createContext("/api/rsvp", EventWebServer::handleRSVP);
        server.createContext("/api/rsvp/cancel", EventWebServer::handleCancelRSVP);

        server.createContext("/api/admin/stats", EventWebServer::handleAdminStats);
        server.createContext("/api/admin/events", EventWebServer::handleAdminEvents);
        server.createContext("/api/admin/events/update", EventWebServer::handleAdminUpdateEvent);
        server.createContext("/api/admin/events/delete", EventWebServer::handleAdminDeleteEvent);
        server.createContext("/api/admin/events/close", EventWebServer::handleAdminCloseEvent);
        server.createContext("/api/admin/attendees", EventWebServer::handleAdminAttendees);

        server.setExecutor(null);
        server.start();
        System.out.println("Web server started on port " + PORT);
        System.out.println("Using database: " + AppConfig.dataDir().resolve("eventrsvp.db"));
        System.out.println("Default admin username: " + AppConfig.defaultAdminUsername());
        return server;
    }

    private static void initDatabase() {
        try {
            dbConnection = DriverManager.getConnection(DATABASE_URL);
            ensureCoreTables();
            ensureDefaultAdmin();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize database.", e);
        }
    }

    private static void ensureCoreTables() throws SQLException {
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS events (" +
                    "name TEXT PRIMARY KEY, " +
                    "dateTime TEXT NOT NULL, " +
                    "location TEXT NOT NULL, " +
                    "maxAttendees INTEGER NOT NULL, " +
                    "closed INTEGER NOT NULL DEFAULT 0)"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS attendees (" +
                    "eventName TEXT NOT NULL, " +
                    "username TEXT NOT NULL, " +
                    "chatId TEXT, " +
                    "PRIMARY KEY (eventName, username))"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS feedback (" +
                    "username TEXT NOT NULL, " +
                    "eventName TEXT NOT NULL, " +
                    "feedback TEXT, " +
                    "PRIMARY KEY (username, eventName))"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                    "username TEXT PRIMARY KEY, " +
                    "passwordHash TEXT NOT NULL, " +
                    "role TEXT NOT NULL DEFAULT 'user', " +
                    "createdAt TEXT NOT NULL)"
            );
        }
    }

    private static void ensureDefaultAdmin() throws SQLException {
        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "INSERT OR IGNORE INTO users (username, passwordHash, role, createdAt) VALUES (?, ?, ?, ?)"
        )) {
            pstmt.setString(1, AppConfig.defaultAdminUsername());
            pstmt.setString(2, hashPassword(AppConfig.defaultAdminPassword()));
            pstmt.setString(3, "admin");
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        }
    }

    private static void handleHealthCheck(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        sendJson(exchange, 200, new JSONObject()
            .put("ok", true)
            .put("port", PORT));
    }

    private static void handleStaticFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }

        Path filePath = PROJECT_ROOT.resolve("web" + path).normalize();
        File file = filePath.toFile();

        if (file.exists() && !file.isDirectory()) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", getContentType(filePath.toString()));
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        sendPlainText(exchange, 404, "404 Not Found");
    }

    private static String getContentType(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=utf-8";
        if (filePath.endsWith(".css")) return "text/css; charset=utf-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (filePath.endsWith(".json")) return "application/json; charset=utf-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) return "image/jpeg";
        if (filePath.endsWith(".svg")) return "image/svg+xml";
        if (filePath.endsWith(".ico")) return "image/x-icon";
        return "text/plain; charset=utf-8";
    }

    private static void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String username = sanitizeUsername(body.optString("username"));
        String password = body.optString("password");

        if (username == null || username.length() < 3) {
            sendJson(exchange, 400, new JSONObject().put("error", "Username must be at least 3 characters."));
            return;
        }
        if (password == null || password.length() < 6) {
            sendJson(exchange, 400, new JSONObject().put("error", "Password must be at least 6 characters."));
            return;
        }

        try (PreparedStatement check = dbConnection.prepareStatement("SELECT username FROM users WHERE username = ?")) {
            check.setString(1, username);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                rs.close();
                sendJson(exchange, 409, new JSONObject().put("error", "Username already exists."));
                return;
            }
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
            return;
        }

        try (PreparedStatement insert = dbConnection.prepareStatement(
            "INSERT INTO users (username, passwordHash, role, createdAt) VALUES (?, ?, 'user', ?)"
        )) {
            insert.setString(1, username);
            insert.setString(2, hashPassword(password));
            insert.setString(3, LocalDateTime.now().toString());
            insert.executeUpdate();
            establishSession(exchange, username, "user");
            sendJson(exchange, 200, userPayload(username, "user"));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String username = sanitizeUsername(body.optString("username"));
        String password = body.optString("password");

        if (username == null || password == null) {
            sendJson(exchange, 400, new JSONObject().put("error", "Username and password are required."));
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT username, passwordHash, role FROM users WHERE username = ?"
        )) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                rs.close();
                sendJson(exchange, 401, new JSONObject().put("error", "Invalid username or password."));
                return;
            }

            String storedHash = rs.getString("passwordHash");
            String role = rs.getString("role");
            rs.close();

            if (!storedHash.equals(hashPassword(password))) {
                sendJson(exchange, 401, new JSONObject().put("error", "Invalid username or password."));
                return;
            }

            establishSession(exchange, username, role);
            sendJson(exchange, 200, userPayload(username, role));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        String token = getSessionToken(exchange);
        if (token != null) {
            SESSIONS.remove(token);
        }
        clearSessionCookie(exchange);
        sendJson(exchange, 200, new JSONObject().put("success", true));
    }

    private static void handleCurrentUser(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = getSession(exchange);
        if (session == null) {
            sendJson(exchange, 200, new JSONObject().put("authenticated", false));
            return;
        }

        sendJson(exchange, 200, userPayload(session.username, session.role));
    }

    private static void handleGetEvents(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        try {
            sendJson(exchange, 200, fetchEventsJson());
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static JSONArray fetchEventsJson() throws SQLException {
        JSONArray events = new JSONArray();
        try (Statement stmt = dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT e.*, COUNT(a.username) as attendeeCount " +
                     "FROM events e LEFT JOIN attendees a ON e.name = a.eventName " +
                     "GROUP BY e.name ORDER BY e.dateTime"
             )) {
            while (rs.next()) {
                events.put(eventFromResultSet(rs));
            }
        }
        return events;
    }

    private static void handleNextEvent(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        try (Statement stmt = dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT e.*, COUNT(a.username) as attendeeCount " +
                     "FROM events e LEFT JOIN attendees a ON e.name = a.eventName " +
                     "GROUP BY e.name"
             )) {

            LocalDateTime now = LocalDateTime.now();
            JSONObject nextEvent = null;
            long minTimeDiff = Long.MAX_VALUE;

            while (rs.next()) {
                LocalDateTime eventTime = LocalDateTime.parse(rs.getString("dateTime"));
                if (eventTime.isAfter(now)) {
                    long timeDiff = ChronoUnit.MILLIS.between(now, eventTime);
                    if (timeDiff < minTimeDiff) {
                        minTimeDiff = timeDiff;
                        nextEvent = eventFromResultSet(rs);
                    }
                }
            }

            if (nextEvent == null) {
                sendJson(exchange, 200, new JSONObject().put("error", "No upcoming events"));
                return;
            }

            long hours = minTimeDiff / (1000 * 60 * 60);
            long minutes = (minTimeDiff / (1000 * 60)) % 60;
            String timeRemaining = hours > 0
                ? hours + " hour" + (hours > 1 ? "s" : "") + (minutes > 0 ? " " + minutes + " min" : "")
                : minutes + " minute" + (minutes > 1 ? "s" : "");

            sendJson(exchange, 200, new JSONObject()
                .put("event", nextEvent)
                .put("timeRemaining", timeRemaining));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleRecommendations(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAuthenticated(exchange);
        if (session == null) {
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            List<JSONObject> pastEvents = new ArrayList<>();
            List<JSONObject> upcomingEvents = new ArrayList<>();

            try (PreparedStatement pastStmt = dbConnection.prepareStatement(
                "SELECT e.name, e.dateTime, e.location " +
                    "FROM events e JOIN attendees a ON e.name = a.eventName " +
                    "WHERE a.username = ?"
            )) {
                pastStmt.setString(1, session.username);
                ResultSet pastRs = pastStmt.executeQuery();
                while (pastRs.next()) {
                    JSONObject event = new JSONObject()
                        .put("name", pastRs.getString("name"))
                        .put("dateTime", pastRs.getString("dateTime"))
                        .put("location", pastRs.getString("location"));
                    if (LocalDateTime.parse(pastRs.getString("dateTime")).isBefore(now)) {
                        pastEvents.add(event);
                    }
                }
                pastRs.close();
            }

            try (PreparedStatement upcomingStmt = dbConnection.prepareStatement(
                "SELECT e.*, COUNT(a.username) as attendeeCount " +
                    "FROM events e LEFT JOIN attendees a ON e.name = a.eventName " +
                    "WHERE e.closed = 0 GROUP BY e.name"
            )) {
                ResultSet upcomingRs = upcomingStmt.executeQuery();
                while (upcomingRs.next()) {
                    LocalDateTime eventTime = LocalDateTime.parse(upcomingRs.getString("dateTime"));
                    if (!eventTime.isAfter(now)) {
                        continue;
                    }
                    if (isAttending(session.username, upcomingRs.getString("name"))) {
                        continue;
                    }
                    upcomingEvents.add(eventFromResultSet(upcomingRs));
                }
                upcomingRs.close();
            }

            if (pastEvents.isEmpty()) {
                sendJson(exchange, 200, new JSONObject().put("error", "Attend some events first to get personalized recommendations!"));
                return;
            }

            if (upcomingEvents.isEmpty()) {
                sendJson(exchange, 200, new JSONObject().put("error", "No upcoming events to recommend"));
                return;
            }

            Map<JSONObject, Integer> scores = new HashMap<>();
            for (JSONObject upcoming : upcomingEvents) {
                int score = 0;
                for (JSONObject past : pastEvents) {
                    score += calculateSimilarity(past, upcoming);
                }
                scores.put(upcoming, score);
            }

            upcomingEvents.sort((e1, e2) -> scores.get(e2).compareTo(scores.get(e1)));

            JSONArray recommendations = new JSONArray();
            int count = 0;
            for (JSONObject event : upcomingEvents) {
                if (count >= 3) {
                    break;
                }
                if (scores.get(event) > 0) {
                    recommendations.put(event);
                    count++;
                }
            }

            JSONArray pastEventNames = new JSONArray();
            for (JSONObject event : pastEvents) {
                pastEventNames.put(event.getString("name"));
            }

            sendJson(exchange, 200, new JSONObject()
                .put("pastEvents", pastEventNames)
                .put("recommendations", recommendations));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static int calculateSimilarity(JSONObject past, JSONObject upcoming) {
        int score = 0;
        String pastName = past.getString("name").toLowerCase();
        String upcomingName = upcoming.getString("name").toLowerCase();
        String pastLocation = past.getString("location").toLowerCase();
        String upcomingLocation = upcoming.getString("location").toLowerCase();

        String[] keywords = {"tech", "ai", "coding", "workshop", "talk", "contest", "hackathon",
            "conference", "seminar", "meetup", "training", "webinar", "summit"};

        for (String keyword : keywords) {
            if (pastName.contains(keyword) && upcomingName.contains(keyword)) {
                score += 10;
            }
        }

        if (pastLocation.equals(upcomingLocation)) {
            score += 5;
        }

        String[] pastWords = pastName.split("\\s+");
        String[] upcomingWords = upcomingName.split("\\s+");
        for (String pastWord : pastWords) {
            if (pastWord.length() <= 3) {
                continue;
            }
            for (String upcomingWord : upcomingWords) {
                if (pastWord.equals(upcomingWord)) {
                    score += 3;
                }
            }
        }

        return score;
    }

    private static void handleMyEvents(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAuthenticated(exchange);
        if (session == null) {
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT e.*, COUNT(a2.username) as attendeeCount " +
                "FROM events e " +
                "JOIN attendees a ON e.name = a.eventName " +
                "LEFT JOIN attendees a2 ON e.name = a2.eventName " +
                "WHERE a.username = ? GROUP BY e.name ORDER BY e.dateTime"
        )) {
            pstmt.setString(1, session.username);
            ResultSet rs = pstmt.executeQuery();
            JSONArray events = new JSONArray();
            while (rs.next()) {
                JSONObject event = eventFromResultSet(rs);
                event.put("isAttending", true);
                events.put(event);
            }
            rs.close();
            sendJson(exchange, 200, events);
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleSearchEvents(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        String keyword = getQueryParam(exchange.getRequestURI().getQuery(), "keyword");
        if (keyword == null || keyword.isBlank()) {
            sendJson(exchange, 400, new JSONObject().put("error", "Keyword required"));
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT e.*, COUNT(a.username) as attendeeCount " +
                "FROM events e LEFT JOIN attendees a ON e.name = a.eventName " +
                "WHERE LOWER(e.name) LIKE ? OR LOWER(e.location) LIKE ? " +
                "GROUP BY e.name ORDER BY e.dateTime"
        )) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);

            ResultSet rs = pstmt.executeQuery();
            JSONArray events = new JSONArray();
            while (rs.next()) {
                events.put(eventFromResultSet(rs));
            }
            rs.close();
            sendJson(exchange, 200, events);
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleRSVP(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAuthenticated(exchange);
        if (session == null) {
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String eventName = body.optString("eventName");
        if (eventName == null || eventName.isBlank()) {
            sendJson(exchange, 400, new JSONObject().put("error", "Event name required"));
            return;
        }

        try (PreparedStatement eventStmt = dbConnection.prepareStatement("SELECT * FROM events WHERE name = ?")) {
            eventStmt.setString(1, eventName);
            ResultSet rs = eventStmt.executeQuery();
            if (!rs.next()) {
                rs.close();
                sendJson(exchange, 404, new JSONObject().put("error", "Event not found"));
                return;
            }

            LocalDateTime eventDateTime = LocalDateTime.parse(rs.getString("dateTime"));
            boolean closed = rs.getInt("closed") == 1;
            int maxAttendees = rs.getInt("maxAttendees");
            rs.close();

            if (closed) {
                sendJson(exchange, 400, new JSONObject().put("error", "Event is closed for RSVPs"));
                return;
            }

            int currentAttendees = countAttendees(eventName);
            if (currentAttendees >= maxAttendees) {
                sendJson(exchange, 400, new JSONObject().put("error", "Event is full"));
                return;
            }

            JSONObject conflict = detectConflict(session.username, eventName, eventDateTime);
            if (conflict != null) {
                sendJson(exchange, 200, new JSONObject()
                    .put("conflict", true)
                    .put("conflictingEvent", conflict));
                return;
            }

            try (PreparedStatement insert = dbConnection.prepareStatement(
                "INSERT OR IGNORE INTO attendees (eventName, username, chatId) VALUES (?, ?, ?)"
            )) {
                insert.setString(1, eventName);
                insert.setString(2, session.username);
                insert.setString(3, "web");
                insert.executeUpdate();
            }

            sendJson(exchange, 200, new JSONObject().put("success", true));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static JSONObject detectConflict(String username, String newEventName, LocalDateTime newEventTime) throws SQLException {
        LocalDateTime newStart = newEventTime;
        LocalDateTime newEnd = newEventTime.plusHours(2);

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT e.* FROM events e JOIN attendees a ON e.name = a.eventName " +
                "WHERE a.username = ? AND e.name != ?"
        )) {
            pstmt.setString(1, username);
            pstmt.setString(2, newEventName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                LocalDateTime existingStart = LocalDateTime.parse(rs.getString("dateTime"));
                LocalDateTime existingEnd = existingStart.plusHours(2);

                if (!(newEnd.isBefore(existingStart) || newEnd.isEqual(existingStart) ||
                    newStart.isAfter(existingEnd) || newStart.isEqual(existingEnd))) {
                    JSONObject conflict = new JSONObject();
                    conflict.put("name", rs.getString("name"));
                    conflict.put("dateTime", rs.getString("dateTime"));
                    conflict.put("location", rs.getString("location"));
                    rs.close();
                    return conflict;
                }
            }
            rs.close();
        }
        return null;
    }

    private static void handleCancelRSVP(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAuthenticated(exchange);
        if (session == null) {
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String eventName = body.optString("eventName");
        if (eventName == null || eventName.isBlank()) {
            sendJson(exchange, 400, new JSONObject().put("error", "Event name required"));
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "DELETE FROM attendees WHERE eventName = ? AND username = ?"
        )) {
            pstmt.setString(1, eventName);
            pstmt.setString(2, session.username);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                sendJson(exchange, 200, new JSONObject().put("success", true));
            } else {
                sendJson(exchange, 404, new JSONObject().put("error", "RSVP not found"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleAdminStats(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAdmin(exchange);
        if (session == null) {
            return;
        }

        try {
            JSONObject stats = new JSONObject();
            stats.put("totalEvents", countScalar("SELECT COUNT(*) FROM events"));
            stats.put("openEvents", countScalar("SELECT COUNT(*) FROM events WHERE closed = 0"));
            stats.put("totalUsers", countScalar("SELECT COUNT(*) FROM users"));
            stats.put("totalRsvps", countScalar("SELECT COUNT(*) FROM attendees"));
            sendJson(exchange, 200, stats);
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleAdminEvents(HttpExchange exchange) throws IOException {
        SessionInfo session = requireAdmin(exchange);
        if (session == null) {
            return;
        }

        if ("GET".equals(exchange.getRequestMethod())) {
            try {
                sendJson(exchange, 200, fetchEventsJson());
            } catch (SQLException e) {
                e.printStackTrace();
                sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
            }
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String name = body.optString("name");
        String dateTime = body.optString("dateTime");
        String location = body.optString("location");
        int maxAttendees = body.optInt("maxAttendees", 0);

        if (name == null || name.isBlank() || location == null || location.isBlank() || maxAttendees <= 0) {
            sendJson(exchange, 400, new JSONObject().put("error", "Name, location, and attendee limit are required."));
            return;
        }

        try {
            LocalDateTime.parse(dateTime);
        } catch (Exception ex) {
            sendJson(exchange, 400, new JSONObject().put("error", "Date/time must be a valid local date time."));
            return;
        }

        try (PreparedStatement insert = dbConnection.prepareStatement(
            "INSERT INTO events (name, dateTime, location, maxAttendees, closed) VALUES (?, ?, ?, ?, 0)"
        )) {
            insert.setString(1, name.trim());
            insert.setString(2, dateTime);
            insert.setString(3, location.trim());
            insert.setInt(4, maxAttendees);
            insert.executeUpdate();
            sendJson(exchange, 200, new JSONObject().put("success", true));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 400, new JSONObject().put("error", "Could not create event. It may already exist."));
        }
    }

    private static void handleAdminCloseEvent(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAdmin(exchange);
        if (session == null) {
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String eventName = body.optString("eventName");
        if (eventName == null || eventName.isBlank()) {
            sendJson(exchange, 400, new JSONObject().put("error", "Event name required."));
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "UPDATE events SET closed = 1 WHERE name = ?"
        )) {
            pstmt.setString(1, eventName);
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                sendJson(exchange, 404, new JSONObject().put("error", "Event not found."));
                return;
            }
            sendJson(exchange, 200, new JSONObject().put("success", true));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static void handleAdminUpdateEvent(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAdmin(exchange);
        if (session == null) {
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String originalName = body.optString("originalName");
        String name = body.optString("name");
        String dateTime = body.optString("dateTime");
        String location = body.optString("location");
        int maxAttendees = body.optInt("maxAttendees", 0);

        if (originalName == null || originalName.isBlank() || name == null || name.isBlank()
            || location == null || location.isBlank() || maxAttendees <= 0) {
            sendJson(exchange, 400, new JSONObject().put("error", "All event fields are required."));
            return;
        }

        try {
            LocalDateTime.parse(dateTime);
        } catch (Exception ex) {
            sendJson(exchange, 400, new JSONObject().put("error", "Date/time must be a valid local date time."));
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "UPDATE events SET name = ?, dateTime = ?, location = ?, maxAttendees = ? WHERE name = ?"
        )) {
            pstmt.setString(1, name.trim());
            pstmt.setString(2, dateTime);
            pstmt.setString(3, location.trim());
            pstmt.setInt(4, maxAttendees);
            pstmt.setString(5, originalName);
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                sendJson(exchange, 404, new JSONObject().put("error", "Event not found."));
                return;
            }

            if (!originalName.equals(name.trim())) {
                try (PreparedStatement attendeeUpdate = dbConnection.prepareStatement(
                    "UPDATE attendees SET eventName = ? WHERE eventName = ?"
                )) {
                    attendeeUpdate.setString(1, name.trim());
                    attendeeUpdate.setString(2, originalName);
                    attendeeUpdate.executeUpdate();
                }
                try (PreparedStatement feedbackUpdate = dbConnection.prepareStatement(
                    "UPDATE feedback SET eventName = ? WHERE eventName = ?"
                )) {
                    feedbackUpdate.setString(1, name.trim());
                    feedbackUpdate.setString(2, originalName);
                    feedbackUpdate.executeUpdate();
                }
            }

            sendJson(exchange, 200, new JSONObject().put("success", true));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 400, new JSONObject().put("error", "Could not update event."));
        }
    }

    private static void handleAdminDeleteEvent(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAdmin(exchange);
        if (session == null) {
            return;
        }

        JSONObject body = readJsonBody(exchange);
        String eventName = body.optString("eventName");
        if (eventName == null || eventName.isBlank()) {
            sendJson(exchange, 400, new JSONObject().put("error", "Event name required."));
            return;
        }

        try {
            try (PreparedStatement attendeeDelete = dbConnection.prepareStatement(
                "DELETE FROM attendees WHERE eventName = ?"
            )) {
                attendeeDelete.setString(1, eventName);
                attendeeDelete.executeUpdate();
            }
            try (PreparedStatement feedbackDelete = dbConnection.prepareStatement(
                "DELETE FROM feedback WHERE eventName = ?"
            )) {
                feedbackDelete.setString(1, eventName);
                feedbackDelete.executeUpdate();
            }
            try (PreparedStatement eventDelete = dbConnection.prepareStatement(
                "DELETE FROM events WHERE name = ?"
            )) {
                eventDelete.setString(1, eventName);
                int deleted = eventDelete.executeUpdate();
                if (deleted == 0) {
                    sendJson(exchange, 404, new JSONObject().put("error", "Event not found."));
                    return;
                }
            }

            sendJson(exchange, 200, new JSONObject().put("success", true));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Could not delete event."));
        }
    }

    private static void handleAdminAttendees(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, new JSONObject().put("error", "Method not allowed"));
            return;
        }

        SessionInfo session = requireAdmin(exchange);
        if (session == null) {
            return;
        }

        String eventName = getQueryParam(exchange.getRequestURI().getQuery(), "eventName");
        if (eventName == null || eventName.isBlank()) {
            sendJson(exchange, 400, new JSONObject().put("error", "Event name required."));
            return;
        }

        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT username FROM attendees WHERE eventName = ? ORDER BY username"
        )) {
            pstmt.setString(1, eventName);
            ResultSet rs = pstmt.executeQuery();
            JSONArray attendees = new JSONArray();
            while (rs.next()) {
                attendees.put(rs.getString("username"));
            }
            rs.close();
            sendJson(exchange, 200, new JSONObject()
                .put("eventName", eventName)
                .put("attendees", attendees));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, new JSONObject().put("error", "Database error"));
        }
    }

    private static JSONObject eventFromResultSet(ResultSet rs) throws SQLException {
        return new JSONObject()
            .put("name", rs.getString("name"))
            .put("dateTime", rs.getString("dateTime"))
            .put("location", rs.getString("location"))
            .put("maxAttendees", rs.getInt("maxAttendees"))
            .put("attendees", rs.getInt("attendeeCount"))
            .put("closed", rs.getInt("closed") == 1);
    }

    private static boolean isAttending(String username, String eventName) throws SQLException {
        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT 1 FROM attendees WHERE username = ? AND eventName = ?"
        )) {
            pstmt.setString(1, username);
            pstmt.setString(2, eventName);
            ResultSet rs = pstmt.executeQuery();
            boolean attending = rs.next();
            rs.close();
            return attending;
        }
    }

    private static int countAttendees(String eventName) throws SQLException {
        try (PreparedStatement pstmt = dbConnection.prepareStatement(
            "SELECT COUNT(*) FROM attendees WHERE eventName = ?"
        )) {
            pstmt.setString(1, eventName);
            ResultSet rs = pstmt.executeQuery();
            int count = rs.getInt(1);
            rs.close();
            return count;
        }
    }

    private static int countScalar(String query) throws SQLException {
        try (Statement stmt = dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.getInt(1);
        }
    }

    private static JSONObject readJsonBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return body.isBlank() ? new JSONObject() : new JSONObject(body);
    }

    private static SessionInfo requireAuthenticated(HttpExchange exchange) throws IOException {
        SessionInfo session = getSession(exchange);
        if (session == null) {
            sendJson(exchange, 401, new JSONObject().put("error", "Please log in first."));
            return null;
        }
        return session;
    }

    private static SessionInfo requireAdmin(HttpExchange exchange) throws IOException {
        SessionInfo session = requireAuthenticated(exchange);
        if (session == null) {
            return null;
        }
        if (!"admin".equalsIgnoreCase(session.role)) {
            sendJson(exchange, 403, new JSONObject().put("error", "Admin access required."));
            return null;
        }
        return session;
    }

    private static SessionInfo getSession(HttpExchange exchange) {
        String token = getSessionToken(exchange);
        if (token == null) {
            return null;
        }

        SessionInfo session = SESSIONS.get(token);
        if (session == null) {
            return null;
        }

        if (session.expiresAt.isBefore(LocalDateTime.now())) {
            SESSIONS.remove(token);
            return null;
        }

        return session;
    }

    private static String getSessionToken(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }

        for (String header : cookies) {
            String[] parts = header.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.startsWith(SESSION_COOKIE + "=")) {
                    return trimmed.substring((SESSION_COOKIE + "=").length());
                }
            }
        }
        return null;
    }

    private static void establishSession(HttpExchange exchange, String username, String role) {
        String token = generateToken();
        SESSIONS.put(token, new SessionInfo(username, role, LocalDateTime.now().plusHours(SESSION_HOURS)));
        StringBuilder cookieValue = new StringBuilder()
            .append(SESSION_COOKIE)
            .append("=")
            .append(token)
            .append("; Path=/; HttpOnly; SameSite=Lax");
        if (AppConfig.secureCookies()) {
            cookieValue.append("; Secure");
        }
        exchange.getResponseHeaders().add(
            "Set-Cookie",
            cookieValue.toString()
        );
    }

    private static void clearSessionCookie(HttpExchange exchange) {
        StringBuilder cookieValue = new StringBuilder()
            .append(SESSION_COOKIE)
            .append("=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
        if (AppConfig.secureCookies()) {
            cookieValue.append("; Secure");
        }
        exchange.getResponseHeaders().add(
            "Set-Cookie",
            cookieValue.toString()
        );
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        return token.toString();
    }
    private static String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim().toLowerCase();
        if (!trimmed.matches("[a-z0-9_\\-]{3,32}")) {
            return null;
        }
        return trimmed;
    }

    private static JSONObject userPayload(String username, String role) {
        return new JSONObject()
            .put("authenticated", true)
            .put("username", username)
            .put("role", role)
            .put("isAdmin", "admin".equalsIgnoreCase(role));
    }

    private static String hashPassword(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String getQueryParam(String query, String param) {
        if (query == null) {
            return null;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals(param)) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, JSONObject body) throws IOException {
        sendBytes(exchange, statusCode, body.toString().getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, JSONArray body) throws IOException {
        sendBytes(exchange, statusCode, body.toString().getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    private static void sendPlainText(HttpExchange exchange, int statusCode, String body) throws IOException {
        sendBytes(exchange, statusCode, body.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
    }

    private static void sendBytes(HttpExchange exchange, int statusCode, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
