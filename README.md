# EventRSVP Telegram Bot

EventRSVP is a Java-based Telegram bot designed to manage event invitations and RSVPs. Built using Maven, this bot allows users to create events, invite others, and track responses easily with advanced features like event recommendations, conflict detection, and smart scheduling.

## Features

### Core Features
- **Create and manage event invitations** via Telegram
- **Track RSVPs** from users with real-time updates
- **Event listing** with sorted display by date (TimSort algorithm)
- **Search events** by keyword (name or location)
- **Personal event dashboard** - View all your RSVPs
- **Cancel RSVPs** - Change your mind anytime
- **Event reminders** - Get notified 1 hour before events
- **QR code generation** - Get personalized QR codes for events
- **Feedback collection** - Share your experience after events
- **Export data** - Export RSVP data to Excel (.xlsx) or text files
- **SQLite database** - Persistent storage for events and RSVPs

### Advanced Features (Algorithms)

#### 1. **Shortest Time Event (Greedy Algorithm)**
- Find the next upcoming event instantly
- Uses greedy selection to find minimum time difference
- Command: `/next`
- Shows time remaining in hours/minutes

#### 2. **Event Recommendation System (Unique Feature)**
- Personalized event suggestions based on your attendance history
- Analyzes past events and finds similar upcoming events
- Scoring algorithm considers:
  - Keyword matching (tech, AI, coding, workshop, etc.)
  - Location similarity
  - Event name word overlap
- Command: `/recommend`
- Shows top 3 recommended events

#### 3. **Event Conflict Detection (Very Impressive)**
- Prevents double-booking automatically
- Detects time overlaps between events
- Assumes 2-hour event duration
- Shows conflicting event details
- Suggests canceling conflicting event first

### Admin Features
- **Create events** with date, time, location, and capacity
- **Close event RSVPs** when full or deadline reached
- **View attendee lists** for any event
- **Broadcast messages** to all event attendees
- **Statistics dashboard** - View event analytics
- **Excel export** - Professional RSVP reports

## Getting Started

### Prerequisites

- Java 8 or higher
- Maven
- Telegram Bot Token (from [BotFather](https://t.me/botfather))

### Installation

1. **Clone the repository**:
   ```bash
   git clone <your-repo-url>
   cd EventRSVP2
   ```

2. **Configure your bot token**:
   Edit the `EventRSVPBot.java` file to insert your Telegram bot token.

3. **Build the project**:
   ```bash
   mvn clean install
   ```

4. **Run the bot**:
   ```bash
   mvn exec:java -Dexec.mainClass="org.example.EventRSVPBot"
   ```

## Available Commands

### User Commands
- `/start` - Show welcome message and command list
- `/events` - List all upcoming events (sorted by date)
- `/next` - Show next upcoming event with time remaining
- `/recommend` - Get personalized event recommendations
- `/search <keyword>` - Search events by name or location
- `/myevents` - Show all events you've RSVP'd to
- `/rsvp <event name>` - RSVP to an event (with conflict detection)
- `/cancel <event name>` - Cancel your RSVP
- `/remind <event name>` - Set reminder (1 hour before event)
- `/qrcode <event name>` - Generate QR code for event
- `/feedback <event name> <message>` - Leave feedback

### Admin Commands
- `/createevent <name> | <yyyy-MM-dd HH:mm> | <location> | <max attendees>` - Create new event
- `/close <event name>` - Close event for RSVPs
- `/attendees <event name>` - View attendee list
- `/broadcast <event name> <message>` - Send announcement to attendees
- `/stats` - View event statistics
- `/export` - Export RSVP data to Excel

## File Structure

```
src/
├── main/
│   └── java/
│       └── org/
│           └── example/
│               └── EventRSVPBot.java
eventrsvp.db            # SQLite database
rsvp_export.txt         # RSVP data export file
rsvp_export.xlsx        # Excel export file
pom.xml                 # Maven build configuration
README.md              # This file
```

## Algorithms Used

### 1. TimSort (Java Collections.sort)
- Used for sorting events by date/time
- O(n log n) time complexity
- Hybrid sorting algorithm (merge sort + insertion sort)

### 2. Greedy Algorithm (Next Event)
- Finds event with minimum time difference from current time
- O(n) time complexity
- Single pass through all events

### 3. Linear Search (Event Search)
- Searches events by keyword matching
- O(n) time complexity
- Checks both event name and location

### 4. Scoring Algorithm (Recommendations)
- Calculates similarity scores between events
- Considers multiple factors (keywords, location, name overlap)
- Greedy selection of top-scored events
- O(n × m) complexity where n = upcoming events, m = past events

### 5. Interval Overlap Detection (Conflict Detection)
- Detects time conflicts between events
- O(n) time complexity per RSVP
- Prevents double-booking automatically

## Database Schema

### Events Table
- `name` (TEXT, PRIMARY KEY)
- `dateTime` (TEXT)
- `location` (TEXT)
- `maxAttendees` (INTEGER)
- `closed` (INTEGER)

### Attendees Table
- `eventName` (TEXT)
- `username` (TEXT)
- `chatId` (TEXT)
- PRIMARY KEY: (eventName, username)

### Feedback Table
- `username` (TEXT)
- `eventName` (TEXT)
- `feedback` (TEXT)
- PRIMARY KEY: (username, eventName)

## Technologies Used

- **Java 8+** - Core programming language
- **Maven** - Build and dependency management
- **Telegram Bot API** - Bot framework
- **SQLite** - Database for persistent storage
- **Apache POI** - Excel file generation
- **ZXing** - QR code generation
- **ScheduledExecutorService** - Reminder scheduling

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

## Acknowledgments

- [Telegram Bot API](https://core.telegram.org/bots/api)
- Java & Maven open source community
- Apache POI for Excel support
- ZXing for QR code generation
