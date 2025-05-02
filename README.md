# EventRSVP Telegram Bot

EventRSVP is a Java-based Telegram bot designed to manage event invitations and RSVPs. Built using Maven, this bot allows users to create events, invite others, and track responses easily.

## Features

- Create and manage event invitations via Telegram
- Track RSVPs from users
- Export RSVP data to a text file
- Built using the Telegram Bot API and Java

## Getting Started

### Prerequisites

- Java 8 or higher
- Maven
- Telegram Bot Token (from [BotFather](https://t.me/botfather))

### Installation

1. **Clone the repository**:
   ```bash
   git clone <your-repo-url>
   cd EventRSVP
   ```

2. **Configure your bot token**:
   Edit the `Main.java` file to insert your Telegram bot token.

3. **Build the project**:
   ```bash
   mvn clean install
   ```

4. **Run the bot**:
   ```bash
   mvn exec:java -Dexec.mainClass="org.example.Main"
   ```

## File Structure

```
src/
├── main/
│   └── java/
│       └── org/
│           └── example/
│               ├── Main.java
│               └── EventRSVPBot.java
rsvp_export.txt         # RSVP data export file
pom.xml                 # Maven build configuration
```

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.

## Acknowledgments

- [Telegram Bot API](https://core.telegram.org/bots/api)
- Java & Maven open source community
