# EventRSVP Web Application

Cross-platform web interface for the EventRSVP system that shares the same SQLite database with the Telegram bot.

## Features

### All Telegram Bot Features Available on Web
- ✅ View all upcoming events (sorted by date)
- ✅ Next event with time remaining (Greedy Algorithm)
- ✅ Personalized event recommendations (Scoring Algorithm)
- ✅ Event conflict detection (Interval Overlap Detection)
- ✅ Search events by keyword
- ✅ RSVP to events
- ✅ Cancel RSVPs
- ✅ View your events dashboard
- ✅ Real-time updates from shared database

## Technology Stack

- **Frontend**: HTML5, CSS3, JavaScript (Vanilla)
- **Backend**: Java HTTP Server (com.sun.net.httpserver)
- **Database**: SQLite (shared with Telegram bot)
- **API**: RESTful JSON API

## Setup Instructions

### 1. Build the Project

```bash
mvn clean install
```

### 2. Run the Web Server

```bash
mvn exec:java -Dexec.mainClass="org.example.EventWebServer"
```

### 3. Access the Website

Open your browser and navigate to:
```
http://localhost:8080
```

### 4. Run Both Telegram Bot and Web Server (Optional)

**Terminal 1 - Telegram Bot:**
```bash
mvn exec:java -Dexec.mainClass="org.example.EventRSVPBot"
```

**Terminal 2 - Web Server:**
```bash
mvn exec:java -Dexec.mainClass="org.example.EventWebServer"
```

Both will share the same `eventrsvp.db` database file!

## API Endpoints

### GET Endpoints
- `GET /api/events` - Get all events
- `GET /api/events/next` - Get next upcoming event
- `GET /api/events/recommend?username=<username>` - Get recommendations
- `GET /api/events/my?username=<username>` - Get user's events
- `GET /api/events/search?keyword=<keyword>` - Search events

### POST Endpoints
- `POST /api/rsvp` - RSVP to event
  ```json
  {
    "eventName": "Tech Talk",
    "username": "john"
  }
  ```

- `POST /api/rsvp/cancel` - Cancel RSVP
  ```json
  {
    "eventName": "Tech Talk",
    "username": "john"
  }
  ```

## Cross-Platform Features

### Shared Database
- Both Telegram bot and web app use the same SQLite database
- Events created on Telegram appear instantly on web
- RSVPs made on web are visible in Telegram
- Real-time synchronization

### Conflict Detection
- Works across platforms
- If you RSVP on Telegram, web app detects conflicts
- If you RSVP on web, Telegram bot detects conflicts

### Recommendations
- Based on attendance from both platforms
- Attend events via Telegram, get recommendations on web
- Attend events via web, get recommendations on Telegram

## File Structure

```
web/
├── index.html          # Main HTML page
├── style.css           # Styling
└── script.js           # Frontend logic

src/main/java/org/example/
└── EventWebServer.java # Backend server
```

## Usage

1. **Login**: Enter your username (same as Telegram username for sync)
2. **Browse Events**: View all upcoming events sorted by date
3. **Next Event**: See the next upcoming event with countdown
4. **Recommendations**: Get personalized suggestions based on your history
5. **My Events**: View all events you've RSVP'd to
6. **Search**: Find events by name or location
7. **RSVP**: Click RSVP button (conflict detection automatic)
8. **Cancel**: Cancel your RSVP anytime

## Algorithms Implemented

### 1. Greedy Algorithm (Next Event)
- Finds event with minimum time difference
- O(n) complexity
- Shows time remaining in hours/minutes

### 2. Scoring Algorithm (Recommendations)
- Analyzes past attendance
- Calculates similarity scores
- Considers keywords, location, name overlap
- Returns top 3 matches

### 3. Interval Overlap Detection (Conflict Detection)
- Prevents double-booking
- Checks 2-hour event duration
- Shows conflicting event details
- Suggests canceling first

## Browser Compatibility

- Chrome/Edge (Recommended)
- Firefox
- Safari
- Opera

## Port Configuration

Default port: `8080`

To change the port, edit `EventWebServer.java`:
```java
private static final int PORT = 8080; // Change this
```

## Security Notes

- This is a development server
- For production, use proper authentication
- Add HTTPS support
- Implement rate limiting
- Validate all inputs

## Troubleshooting

**Port already in use:**
```
Change PORT in EventWebServer.java or kill process using port 8080
```

**Database locked:**
```
Make sure only one instance of bot/server is writing at a time
SQLite handles concurrent reads but limited concurrent writes
```

**Events not showing:**
```
Check if eventrsvp.db exists in project root
Run Telegram bot first to create database
```

## Future Enhancements

- [ ] Admin panel for event creation
- [ ] User authentication
- [ ] Real-time notifications (WebSocket)
- [ ] Event calendar view
- [ ] QR code display on web
- [ ] Feedback submission
- [ ] Statistics dashboard
- [ ] Export to Excel from web

## License

MIT License - Same as main EventRSVP project
