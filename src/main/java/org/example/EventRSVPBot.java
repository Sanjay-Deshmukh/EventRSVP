//Répondez s'il vous plaît
package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EventRSVPBot extends TelegramLongPollingBot {

    static class Event {
        String name;
        LocalDateTime dateTime;
        String location;
        int maxAttendees;
        Set<String> attendees = new HashSet<>();

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
                                "/rsvp <event name> - RSVP to an event\n" +
                                "/cancel <event name> - Cancel RSVP\n" +
                                "/attendees <event name> - Admin only: View attendees\n" +
                                "/createevent - Admin only: Create a new event\n" +
                                "/save - Admin only: Save RSVP data\n" +
                                "/feedback <event name> <your feedback> - Leave feedback"
                );
            } else if (text.equalsIgnoreCase("/events")) {
                showEvents(chatId);
            } else if (text.startsWith("/rsvp")) {
                handleRSVP(text, chatId, username);
            } else if (text.startsWith("/cancel")) {
                handleCancelRSVP(text, chatId, username);
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
            } else {
                sendMessage(chatId, "Unknown command. Type /start to begin.");
            }
        }
    }

    private void showEvents(String chatId) {
        if (events.isEmpty()) {
            sendMessage(chatId, " No events available.");
            return;
        }

        StringBuilder sb = new StringBuilder(" *Upcoming Events:*\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Event e : events.values()) {
            sb.append("\n *").append(e.name).append("*\n")
                    .append(" ").append(e.location).append("\n")
                    .append(" ").append(e.dateTime.format(fmt)).append("\n")
                    .append(" ").append(e.attendees.size()).append("/").append(e.maxAttendees).append(" RSVP'd\n")
                    .append("Type `/rsvp ").append(e.name).append("` to join.\n");
        }

        sendMessage(chatId, sb.toString());
    }

    private void handleRSVP(String text, String chatId, String username) {
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

        if (e.attendees.contains(username)) {
            sendMessage(chatId, " You’ve already RSVP’d.");
        } else if (e.attendees.size() >= e.maxAttendees) {
            sendMessage(chatId, " Event is full!");
        } else {
            e.attendees.add(username);
            sendMessage(chatId, " You have RSVP'd for *" + eventName + "*.");
        }
    }

    private void handleCancelRSVP(String text, String chatId, String username) {
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
        sendMessage(chatId, " Your RSVP has been cancelled for *" + eventName + "*.");
    }

    private void showAttendees(String text, String chatId) {
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
        sendMessage(chatId, " Thank you for your feedback!");
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

    public static void main(String[] args) {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            EventRSVPBot bot = new EventRSVPBot();
            telegramBotsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
