//Répondez s'il vous plaît
package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class EventRSVPBot extends TelegramLongPollingBot {
    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final String DATABASE_URL = "jdbc:sqlite:" + PROJECT_ROOT.resolve("eventrsvp.db").toString();

    static class Event {
        String name;
        LocalDateTime dateTime;
        String location;
        int maxAttendees;
        Set<String> attendees = new HashSet<>();
        Map<String, String> attendeeChatIds = new HashMap<>();
        boolean closed = false;
        Map<String, LocalDateTime> reminderTimes = new HashMap<>();

        Event(String name, LocalDateTime dateTime, String location, int maxAttendees) {
            this.name = name;
            this.dateTime = dateTime;
            this.location = location;
            this.maxAttendees = maxAttendees;
        }
    }

    private final Map<String, Event> events = new HashMap<>();
    private final Set<Long> adminUserIds = new HashSet<>(Collections.singletonList(1795028355L));
    private final Map<String, String> userFeedback = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Connection dbConnection;

    public EventRSVPBot() {
        initDatabase();
        loadEventsFromDatabase();
    }

    private void refreshEventsFromDatabase() {
        events.clear();
        userFeedback.clear();
        loadEventsFromDatabase();
    }

    private final String botToken = "8112144137:AAGJ4_6Ubt07RggBvbtrmMTMXG7jwPJxn8Q";

    @Override
    public String getBotUsername() {
        return "TELERSVPBOT";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();

        if (message != null && message.hasText()) {
            String text = message.getText();
            String chatId = message.getChatId().toString();
            Long userId = message.getFrom().getId();
            String username = message.getFrom().getUserName();
            if (username == null || username.isEmpty()) {
                username = message.getFrom().getFirstName();
            }

            if (text.equalsIgnoreCase("/start")) {
                sendMessage(chatId,
                        " Welcome to the Event RSVP Bot!\n\nAvailable Commands:\n" +
                                "/events - List all upcoming events\n" +
                                "/next - Show next upcoming event\n" +
                                "/recommend - Get personalized event recommendations\n" +
                                "/search <keyword> - Search events\n" +
                                "/myevents - Show your RSVPs\n" +
                                "/rsvp <event name> - RSVP to an event\n" +
                                "/cancel <event name> - Cancel RSVP\n" +
                                "/remind <event name> - Set reminder\n" +
                                "/qrcode <event name> - Get QR code\n" +
                                "/feedback <event name> <feedback> - Leave feedback\n\n" +
                                "*Admin Commands:*\n" +
                                "/createevent - Create event\n" +
                                "/close <event> - Close RSVPs\n" +
                                "/attendees <event> - View attendees\n" +
                                "/broadcast <event> <msg> - Send announcement\n" +
                                "/stats - View statistics\n" +
                                "/export - Export to Excel"
                );
            } else if (text.equalsIgnoreCase("/events")) {
                showEvents(chatId);
            } else if (text.equalsIgnoreCase("/next")) {
                showNextEvent(chatId);
            } else if (text.equalsIgnoreCase("/recommend")) {
                recommendEvents(chatId, username);
            } else if (text.startsWith("/search")) {
                searchEvents(text, chatId);
            } else if (text.equalsIgnoreCase("/myevents")) {
                showMyEvents(chatId, username);
            } else if (text.startsWith("/rsvp")) {
                handleRSVP(text, chatId, username);
            } else if (text.startsWith("/cancel")) {
                handleCancelRSVP(text, chatId, username);
            } else if (text.startsWith("/remind")) {
                setReminder(text, chatId, username);
            } else if (text.startsWith("/qrcode")) {
                generateQRCode(text, chatId, username);
            } else if (text.startsWith("/attendees")) {
                if (!adminUserIds.contains(userId)) {
                    sendMessage(chatId, " You are not authorized.");
                } else {
                    showAttendees(text, chatId);
                }
            } else if (text.equalsIgnoreCase("/createevent")) {
                if (adminUserIds.contains(userId)) {
                    sendMessage(chatId, " Format:\n/createevent EventName | yyyy-MM-dd HH:mm | Location | MaxAttendees");
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else if (text.startsWith("/createevent ")) {
                if (adminUserIds.contains(userId)) {
                    createEvent(text, chatId);
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else if (text.equalsIgnoreCase("/save")) {
                if (adminUserIds.contains(userId)) {
                    saveRSVPToFile(chatId);
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else if (text.startsWith("/feedback")) {
                collectFeedback(text, chatId, username);
            } else if (text.startsWith("/broadcast")) {
                if (adminUserIds.contains(userId)) {
                    broadcastMessage(text, chatId);
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else if (text.equalsIgnoreCase("/stats")) {
                if (adminUserIds.contains(userId)) {
                    showStats(chatId);
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else if (text.equalsIgnoreCase("/export")) {
                if (adminUserIds.contains(userId)) {
                    exportToExcel(chatId);
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else if (text.startsWith("/close")) {
                if (adminUserIds.contains(userId)) {
                    closeEvent(text, chatId);
                } else {
                    sendMessage(chatId, " You are not authorized.");
                }
            } else {
                sendMessage(chatId, "Unknown command. Type /start to begin.");
            }
        }
    }

    private void recommendEvents(String chatId, String username) {
        refreshEventsFromDatabase();
        List<Event> userPastEvents = new ArrayList<>();
        List<Event> upcomingEvents = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Separate past and upcoming events
        for (Event e : events.values()) {
            if (e.attendees.contains(username)) {
                if (e.dateTime.isBefore(now)) {
                    userPastEvents.add(e);
                }
            } else if (e.dateTime.isAfter(now) && !e.closed) {
                upcomingEvents.add(e);
            }
        }

        if (upcomingEvents.isEmpty()) {
            sendMessage(chatId, " No upcoming events to recommend.");
            return;
        }

        if (userPastEvents.isEmpty()) {
            sendMessage(chatId, " Attend some events first to get personalized recommendations!");
            return;
        }

        // Calculate recommendation scores
        Map<Event, Integer> scores = new HashMap<>();
        for (Event upcoming : upcomingEvents) {
            int score = 0;
            for (Event past : userPastEvents) {
                score += calculateSimilarity(past, upcoming);
            }
            scores.put(upcoming, score);
        }

        // Sort by score (greedy selection of highest scores)
        List<Event> recommendations = new ArrayList<>(upcomingEvents);
        Collections.sort(recommendations, (e1, e2) -> scores.get(e2).compareTo(scores.get(e1)));

        // Show top 3 recommendations
        StringBuilder sb = new StringBuilder(" *Recommended Events for You:*\n");
        sb.append("\nBased on your attendance:\n");
        for (Event past : userPastEvents) {
            sb.append(" - ").append(past.name).append("\n");
        }
        sb.append("\n *We recommend:*\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        int count = 0;
        for (Event e : recommendations) {
            if (count >= 3) break;
            if (scores.get(e) > 0) {
                sb.append("\n *").append(e.name).append("*\n")
                        .append(" ").append(e.location).append("\n")
                        .append(" ").append(e.dateTime.format(fmt)).append("\n")
                        .append(" ").append(e.attendees.size()).append("/").append(e.maxAttendees).append(" RSVP'd\n")
                        .append("Type `/rsvp ").append(e.name).append("` to join.\n");
                count++;
            }
        }

        if (count == 0) {
            sb.append("\nNo similar events found at the moment.");
        }

        sendMessage(chatId, sb.toString());
    }

    private int calculateSimilarity(Event past, Event upcoming) {
        int score = 0;
        String pastName = past.name.toLowerCase();
        String upcomingName = upcoming.name.toLowerCase();
        String pastLocation = past.location.toLowerCase();
        String upcomingLocation = upcoming.location.toLowerCase();

        // Check for common keywords
        String[] keywords = {"tech", "ai", "coding", "workshop", "talk", "contest", "hackathon", 
                             "conference", "seminar", "meetup", "training", "webinar", "summit"};
        
        for (String keyword : keywords) {
            if (pastName.contains(keyword) && upcomingName.contains(keyword)) {
                score += 10;
            }
        }

        // Same location bonus
        if (pastLocation.equals(upcomingLocation)) {
            score += 5;
        }

        // Word overlap in event names
        String[] pastWords = pastName.split("\\s+");
        String[] upcomingWords = upcomingName.split("\\s+");
        for (String pw : pastWords) {
            if (pw.length() > 3) {
                for (String uw : upcomingWords) {
                    if (pw.equals(uw)) {
                        score += 3;
                    }
                }
            }
        }

        return score;
    }

    private void showNextEvent(String chatId) {
        refreshEventsFromDatabase();
        if (events.isEmpty()) {
            sendMessage(chatId, " No events available.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Event nextEvent = null;
        long minTimeDiff = Long.MAX_VALUE;

        // Greedy algorithm: find event with minimum time difference from now
        for (Event e : events.values()) {
            if (e.dateTime.isAfter(now)) {
                long timeDiff = java.time.Duration.between(now, e.dateTime).toMillis();
                if (timeDiff < minTimeDiff) {
                    minTimeDiff = timeDiff;
                    nextEvent = e;
                }
            }
        }

        if (nextEvent == null) {
            sendMessage(chatId, " No upcoming events.");
            return;
        }

        long hours = minTimeDiff / (1000 * 60 * 60);
        long minutes = (minTimeDiff / (1000 * 60)) % 60;

        StringBuilder sb = new StringBuilder(" *Next Event:*\n");
        sb.append("\n *").append(nextEvent.name).append("*");
        if (hours > 0) {
            sb.append(" in ").append(hours).append(" hour");
            if (hours > 1) sb.append("s");
            if (minutes > 0) sb.append(" ").append(minutes).append(" min");
        } else if (minutes > 0) {
            sb.append(" in ").append(minutes).append(" minute");
            if (minutes > 1) sb.append("s");
        } else {
            sb.append(" starting soon");
        }
        sb.append("\n")
                .append(" ").append(nextEvent.location).append("\n")
                .append(" ").append(nextEvent.dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n")
                .append(" ").append(nextEvent.attendees.size()).append("/").append(nextEvent.maxAttendees).append(" RSVP'd\n");
        if (!nextEvent.closed) {
            sb.append("Type `/rsvp ").append(nextEvent.name).append("` to join.");
        }

        sendMessage(chatId, sb.toString());
    }

    private void showEvents(String chatId) {
        refreshEventsFromDatabase();
        if (events.isEmpty()) {
            sendMessage(chatId, " No events available.");
            return;
        }

        StringBuilder sb = new StringBuilder(" *Upcoming Events:*\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Sort events by date using TimSort (via Collections.sort)
        List<Event> sortedEvents = new ArrayList<>(events.values());
        Collections.sort(sortedEvents, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                return e1.dateTime.compareTo(e2.dateTime);
            }
        });

        for (Event e : sortedEvents) {
            sb.append("\n *").append(e.name).append("*");
            if (e.closed) sb.append(" [CLOSED]");
            sb.append("\n")
                    .append(" ").append(e.location).append("\n")
                    .append(" ").append(e.dateTime.format(fmt)).append("\n")
                    .append(" ").append(e.attendees.size()).append("/").append(e.maxAttendees).append(" RSVP'd\n");
            if (!e.closed) {
                sb.append("Type `/rsvp ").append(e.name).append("` to join.\n");
            }
        }

        sendMessage(chatId, sb.toString());
    }

    private void showMyEvents(String chatId, String username) {
        refreshEventsFromDatabase();
        List<String> userEvents = new ArrayList<>();
        for (Event e : events.values()) {
            if (e.attendees.contains(username)) {
                userEvents.add(e.name);
            }
        }

        if (userEvents.isEmpty()) {
            sendMessage(chatId, " You haven't RSVP'd to any events.");
        } else {
            StringBuilder sb = new StringBuilder(" *Your Events:*\n");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            
            // Sort user's events by date
            List<Event> sortedUserEvents = new ArrayList<>();
            for (String eventName : userEvents) {
                sortedUserEvents.add(events.get(eventName));
            }
            Collections.sort(sortedUserEvents, (e1, e2) -> e1.dateTime.compareTo(e2.dateTime));
            
            for (Event e : sortedUserEvents) {
                sb.append("\n *").append(e.name).append("*\n")
                        .append(" ").append(e.location).append("\n")
                        .append(" ").append(e.dateTime.format(fmt)).append("\n");
            }
            sendMessage(chatId, sb.toString());
        }
    }

    private void searchEvents(String text, String chatId) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Usage: /search keyword");
            return;
        }

        String keyword = parts[1].trim().toLowerCase();
        List<Event> matchedEvents = new ArrayList<>();

        // Linear search through events - O(n) complexity
        for (Event e : events.values()) {
            if (e.name.toLowerCase().contains(keyword) || 
                e.location.toLowerCase().contains(keyword)) {
                matchedEvents.add(e);
            }
        }

        if (matchedEvents.isEmpty()) {
            sendMessage(chatId, " No events found matching '" + keyword + "'.");
            return;
        }

        // Sort results by date
        Collections.sort(matchedEvents, (e1, e2) -> e1.dateTime.compareTo(e2.dateTime));

        StringBuilder sb = new StringBuilder(" *Search Results for '" + keyword + "':*\n");
        sb.append("Found ").append(matchedEvents.size()).append(" event(s)\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Event e : matchedEvents) {
            sb.append("\n *").append(e.name).append("*");
            if (e.closed) sb.append(" [CLOSED]");
            sb.append("\n")
                    .append(" ").append(e.location).append("\n")
                    .append(" ").append(e.dateTime.format(fmt)).append("\n")
                    .append(" ").append(e.attendees.size()).append("/").append(e.maxAttendees).append(" RSVP'd\n");
            if (!e.closed) {
                sb.append("Type `/rsvp ").append(e.name).append("` to join.\n");
            }
        }

        sendMessage(chatId, sb.toString());
    }

    private void handleRSVP(String text, String chatId, String username) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Please specify event name. Usage: /rsvp EventName");
            return;
        }

        String eventName = parts[1].trim();
        Event e = events.get(eventName);

        if (e == null) {
            sendMessage(chatId, " Event not found.");
            return;
        }

        if (e.closed) {
            sendMessage(chatId, " This event is closed for RSVPs.");
        } else if (e.attendees.contains(username)) {
            sendMessage(chatId, " You've already RSVP'd.");
        } else if (e.attendees.size() >= e.maxAttendees) {
            sendMessage(chatId, " Event is full!");
        } else {
            // Check for time conflicts
            Event conflictingEvent = detectConflict(username, e);
            if (conflictingEvent != null) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                sendMessage(chatId, " You already have another event at this time!\n\n" +
                        "Conflicting Event: *" + conflictingEvent.name + "*\n" +
                        " " + conflictingEvent.dateTime.format(fmt) + "\n\n" +
                        "Please cancel that event first using `/cancel " + conflictingEvent.name + "`");
                return;
            }

            e.attendees.add(username);
            e.attendeeChatIds.put(username, chatId);
            saveAttendeeToDatabase(eventName, username, chatId);
            sendMessage(chatId, " You have RSVP'd for *" + eventName + "*.");
        }
    }

    private Event detectConflict(String username, Event newEvent) {
        LocalDateTime newStart = newEvent.dateTime;
        LocalDateTime newEnd = newEvent.dateTime.plusHours(2); // Assume 2-hour duration

        for (Event e : events.values()) {
            if (e.attendees.contains(username) && !e.name.equals(newEvent.name)) {
                LocalDateTime existingStart = e.dateTime;
                LocalDateTime existingEnd = e.dateTime.plusHours(2);

                // Check for time overlap
                if (!(newEnd.isBefore(existingStart) || newStart.isAfter(existingEnd) || 
                      newEnd.isEqual(existingStart) || newStart.isEqual(existingEnd))) {
                    return e; // Conflict detected
                }
            }
        }
        return null; // No conflict
    }

    private void handleCancelRSVP(String text, String chatId, String username) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Please specify event name. Usage: /cancel EventName");
            return;
        }

        String eventName = parts[1].trim();
        Event e = events.get(eventName);

        if (e == null || !e.attendees.contains(username)) {
            sendMessage(chatId, " You haven't RSVP'd for this event.");
            return;
        }

        e.attendees.remove(username);
        e.attendeeChatIds.remove(username);
        removeAttendeeFromDatabase(eventName, username);
        sendMessage(chatId, " Your RSVP has been cancelled for *" + eventName + "*.");
    }

    private void setReminder(String text, String chatId, String username) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Usage: /remind EventName");
            return;
        }

        String eventName = parts[1].trim();
        Event e = events.get(eventName);

        if (e == null) {
            sendMessage(chatId, " Event not found.");
            return;
        }

        if (!e.attendees.contains(username)) {
            sendMessage(chatId, " You must RSVP first to set a reminder.");
            return;
        }

        LocalDateTime reminderTime = e.dateTime.minusHours(1);
        e.reminderTimes.put(username, reminderTime);

        long delay = java.time.Duration.between(LocalDateTime.now(), reminderTime).toMillis();
        if (delay > 0) {
            scheduler.schedule(() -> {
                sendMessage(chatId, " Reminder: Event *" + eventName + "* starts in 1 hour!");
            }, delay, TimeUnit.MILLISECONDS);
            sendMessage(chatId, " Reminder set for 1 hour before *" + eventName + "*.");
        } else {
            sendMessage(chatId, " Event is too soon to set a reminder.");
        }
    }

    private void generateQRCode(String text, String chatId, String username) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Usage: /qrcode EventName");
            return;
        }

        String eventName = parts[1].trim();
        Event e = events.get(eventName);

        if (e == null) {
            sendMessage(chatId, " Event not found.");
            return;
        }

        try {
            String qrData = "Event: " + e.name + "\nLocation: " + e.location + "\nDate: " + e.dateTime.toString() + "\nAttendee: " + username;
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, 300, 300);

            File qrFile = new File("qr_" + eventName.replaceAll("\\s+", "_") + "_" + username + ".png");
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", qrFile.toPath());

            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(new InputFile(qrFile));
            sendPhoto.setCaption(" QR Code for *" + eventName + "* - @" + username);
            sendPhoto.setParseMode("Markdown");

            execute(sendPhoto);
            qrFile.delete();
        } catch (Exception ex) {
            sendMessage(chatId, " Error generating QR code.");
            ex.printStackTrace();
        }
    }

    private void showAttendees(String text, String chatId) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Usage: /attendees EventName");
            return;
        }

        String eventName = parts[1].trim();
        Event e = events.get(eventName);
        if (e == null) {
            sendMessage(chatId, " Event not found.");
            return;
        }

        if (e.attendees.isEmpty()) {
            sendMessage(chatId, " No one has RSVP'd yet for *" + eventName + "*.");
            return;
        }

        StringBuilder sb = new StringBuilder("*Attendees for " + eventName + ":*\n");
        for (String user : e.attendees) {
            sb.append(" - @").append(user).append("\n");
        }

        sendMessage(chatId, sb.toString());
    }

    private void createEvent(String text, String chatId) {
        refreshEventsFromDatabase();
        try {
            String[] parts = text.substring(13).split("\\|");
            String name = parts[0].trim();
            LocalDateTime dateTime = LocalDateTime.parse(parts[1].trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String location = parts[2].trim();
            int maxAttendees = Integer.parseInt(parts[3].trim());

            if (events.containsKey(name)) {
                sendMessage(chatId, " Event with this name already exists.");
            } else {
                events.put(name, new Event(name, dateTime, location, maxAttendees));
                saveEventToDatabase(events.get(name));
                sendMessage(chatId, " Event created: " + name);
            }
        } catch (Exception e) {
            sendMessage(chatId, " Error creating event. Format:\n/createevent EventName | yyyy-MM-dd HH:mm | Location | MaxAttendees");
        }
    }

    private void collectFeedback(String text, String chatId, String username) {
        String[] parts = text.split(" ", 3);
        if (parts.length < 3) {
            sendMessage(chatId, " Usage: /feedback EventName Your feedback here");
            return;
        }

        String eventName = parts[1].trim();
        String feedback = parts[2].trim();

        userFeedback.put(username + ":" + eventName, feedback);
        saveFeedbackToDatabase(username, eventName, feedback);
        sendMessage(chatId, " Thank you for your feedback!");
    }

    private void broadcastMessage(String text, String chatId) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 3);
        if (parts.length < 3) {
            sendMessage(chatId, " Usage: /broadcast EventName Your message here");
            return;
        }

        String eventName = parts[1].trim();
        String message = parts[2].trim();
        Event e = events.get(eventName);

        if (e == null) {
            sendMessage(chatId, " Event not found.");
            return;
        }

        int sent = 0;
        for (String attendee : e.attendees) {
            String attendeeChatId = e.attendeeChatIds.get(attendee);
            if (attendeeChatId != null) {
                sendMessage(attendeeChatId, " *Announcement for " + eventName + ":*\n\n" + message);
                sent++;
            }
        }

        sendMessage(chatId, " Broadcast sent to " + sent + " attendees.");
    }

    private void showStats(String chatId) {
        refreshEventsFromDatabase();
        int totalEvents = events.size();
        int totalRSVPs = 0;
        String mostPopular = "";
        int maxAttendees = 0;

        for (Event e : events.values()) {
            totalRSVPs += e.attendees.size();
            if (e.attendees.size() > maxAttendees) {
                maxAttendees = e.attendees.size();
                mostPopular = e.name;
            }
        }

        StringBuilder sb = new StringBuilder(" *Event Statistics:*\n\n");
        sb.append(" Total Events: ").append(totalEvents).append("\n");
        sb.append(" Total RSVPs: ").append(totalRSVPs).append("\n");
        if (!mostPopular.isEmpty()) {
            sb.append(" Most Popular: ").append(mostPopular).append(" (").append(maxAttendees).append(" attendees)\n");
        }
        sb.append("\n *Event Breakdown:*\n");
        for (Event e : events.values()) {
            sb.append("\n").append(e.name).append(": ").append(e.attendees.size()).append("/").append(e.maxAttendees);
            if (e.closed) sb.append(" [CLOSED]");
            sb.append("\n");
        }

        sendMessage(chatId, sb.toString());
    }

    private void exportToExcel(String chatId) {
        refreshEventsFromDatabase();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("RSVP Data");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Event Name");
            headerRow.createCell(1).setCellValue("Location");
            headerRow.createCell(2).setCellValue("Date & Time");
            headerRow.createCell(3).setCellValue("Max Attendees");
            headerRow.createCell(4).setCellValue("Current RSVPs");
            headerRow.createCell(5).setCellValue("Status");
            headerRow.createCell(6).setCellValue("Attendees");

            int rowNum = 1;
            for (Event e : events.values()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(e.name);
                row.createCell(1).setCellValue(e.location);
                row.createCell(2).setCellValue(e.dateTime.toString());
                row.createCell(3).setCellValue(e.maxAttendees);
                row.createCell(4).setCellValue(e.attendees.size());
                row.createCell(5).setCellValue(e.closed ? "CLOSED" : "OPEN");
                row.createCell(6).setCellValue(String.join(", ", e.attendees));
            }

            for (int i = 0; i < 7; i++) {
                sheet.autoSizeColumn(i);
            }

            File excelFile = new File("rsvp_export.xlsx");
            try (FileOutputStream fileOut = new FileOutputStream(excelFile)) {
                workbook.write(fileOut);
            }

            sendMessage(chatId, " Excel file created: rsvp_export.xlsx");
        } catch (Exception e) {
            sendMessage(chatId, " Error creating Excel file.");
            e.printStackTrace();
        }
    }

    private void closeEvent(String text, String chatId) {
        refreshEventsFromDatabase();
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, " Usage: /close EventName");
            return;
        }

        String eventName = parts[1].trim();
        Event e = events.get(eventName);

        if (e == null) {
            sendMessage(chatId, " Event not found.");
            return;
        }

        e.closed = true;
        saveEventToDatabase(e);
        sendMessage(chatId, " Event *" + eventName + "* is now closed for RSVPs.");
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.enableMarkdown(true);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void saveRSVPToFile(String chatId) {
        StringBuilder sb = new StringBuilder("RSVP Export:\n");

        for (Event e : events.values()) {
            sb.append("\nEvent: ").append(e.name).append("\n");
            sb.append("Location: ").append(e.location).append("\n");
            sb.append("Date & Time: ").append(e.dateTime.toString()).append("\n");
            sb.append("Attendees:\n");
            for (String user : e.attendees) {
                sb.append(" - ").append(user).append("\n");
            }
        }

        try (FileWriter writer = new FileWriter("rsvp_export.txt")) {
            writer.write(sb.toString());
            sendMessage(chatId, " RSVP data saved.");
        } catch (IOException e) {
            sendMessage(chatId, " Error saving file.");
        }
    }

    private void initDatabase() {
        try {
            dbConnection = DriverManager.getConnection(DATABASE_URL);
            System.out.println("Bot using database: " + PROJECT_ROOT.resolve("eventrsvp.db"));
            Statement stmt = dbConnection.createStatement();
            
            stmt.execute("CREATE TABLE IF NOT EXISTS events (" +
                    "name TEXT PRIMARY KEY, " +
                    "dateTime TEXT, " +
                    "location TEXT, " +
                    "maxAttendees INTEGER, " +
                    "closed INTEGER)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS attendees (" +
                    "eventName TEXT, " +
                    "username TEXT, " +
                    "chatId TEXT, " +
                    "PRIMARY KEY (eventName, username))");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS feedback (" +
                    "username TEXT, " +
                    "eventName TEXT, " +
                    "feedback TEXT, " +
                    "PRIMARY KEY (username, eventName))");
            
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadEventsFromDatabase() {
        try {
            Statement stmt = dbConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM events");
            
            while (rs.next()) {
                String name = rs.getString("name");
                LocalDateTime dateTime = LocalDateTime.parse(rs.getString("dateTime"));
                String location = rs.getString("location");
                int maxAttendees = rs.getInt("maxAttendees");
                boolean closed = rs.getInt("closed") == 1;
                
                Event event = new Event(name, dateTime, location, maxAttendees);
                event.closed = closed;
                events.put(name, event);
            }
            rs.close();
            
            rs = stmt.executeQuery("SELECT * FROM attendees");
            while (rs.next()) {
                String eventName = rs.getString("eventName");
                String username = rs.getString("username");
                String chatId = rs.getString("chatId");
                
                Event event = events.get(eventName);
                if (event != null) {
                    event.attendees.add(username);
                    event.attendeeChatIds.put(username, chatId);
                }
            }
            rs.close();
            
            rs = stmt.executeQuery("SELECT * FROM feedback");
            while (rs.next()) {
                String username = rs.getString("username");
                String eventName = rs.getString("eventName");
                String feedback = rs.getString("feedback");
                userFeedback.put(username + ":" + eventName, feedback);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveEventToDatabase(Event event) {
        try {
            PreparedStatement pstmt = dbConnection.prepareStatement(
                    "INSERT OR REPLACE INTO events (name, dateTime, location, maxAttendees, closed) VALUES (?, ?, ?, ?, ?)");
            pstmt.setString(1, event.name);
            pstmt.setString(2, event.dateTime.toString());
            pstmt.setString(3, event.location);
            pstmt.setInt(4, event.maxAttendees);
            pstmt.setInt(5, event.closed ? 1 : 0);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveAttendeeToDatabase(String eventName, String username, String chatId) {
        try {
            PreparedStatement pstmt = dbConnection.prepareStatement(
                    "INSERT OR REPLACE INTO attendees (eventName, username, chatId) VALUES (?, ?, ?)");
            pstmt.setString(1, eventName);
            pstmt.setString(2, username);
            pstmt.setString(3, chatId);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void removeAttendeeFromDatabase(String eventName, String username) {
        try {
            PreparedStatement pstmt = dbConnection.prepareStatement(
                    "DELETE FROM attendees WHERE eventName = ? AND username = ?");
            pstmt.setString(1, eventName);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveFeedbackToDatabase(String username, String eventName, String feedback) {
        try {
            PreparedStatement pstmt = dbConnection.prepareStatement(
                    "INSERT OR REPLACE INTO feedback (username, eventName, feedback) VALUES (?, ?, ?)");
            pstmt.setString(1, username);
            pstmt.setString(2, eventName);
            pstmt.setString(3, feedback);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            EventRSVPBot bot = new EventRSVPBot();
            telegramBotsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
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
