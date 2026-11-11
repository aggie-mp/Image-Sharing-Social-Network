# Java TCP Social Network for Image Sharing

A command-line social network for image-sharing built in Java, using TCP sockets and a multithreaded server. Users can create accounts, upload images, follow/unfollow other users, browse shared content, comment on posts, and request permission to download images from followed accounts.

The project focuses on socket programming, application-level messaging, concurrent client handling, and persistent state without a database.

## Features

- **User accounts:** Sign up, log in, and maintain a unique four-digit user ID internally.
- **Image uploads:** Upload local images to the server, with accompanying text decription.
- **Image search:** Search content available through followed accounts. The user is given an option to request download for any of the results.
- **Follow system:** request, user can accept, reject, accept & follow back or postpone the request.
- **Unfollow system:** selected account gets unfollowed
- **Profiles:** View your own profile and the profiles of accounts you follow.
- **Comments:** Comment on images through another user's profile. Image owners receive notifications.
- **Permission-based downloads:** Request an image download. The owner can approve, reject, or postpone the request.
- **Notifications:** Review follow requests, download requests, approvals, and comment notifications through the Notifications menu.
- **Multiple clients:** A thread-pooled server handles simultaneous connections.

## Technology Stack

| Area | Implementation |
| --- | --- |
| Language | Java |
| Networking | TCP sockets (`Socket`, `ServerSocket`) |
| Client/server messaging | Java object streams (`ObjectInputStream`, `ObjectOutputStream`) |
| Concurrency | `ExecutorService` with a fixed pool of 8 worker threads |
| File transfer | Custom serializable `Packet` objects containing sequence numbers and byte chunks |
| Persistence | Local text files and filesystem directories |
| User interface | Interactive terminal menus |

No external Java dependencies or database are required by the source code.

## Project Structure

```text
.
├── data/
│   ├── users.txt
│   └── SocialGraph.txt
└── src/
    ├── client/
    │   ├── Client.java
    │   ├── LocalStoragePaths.java
    │   └── users/                 # Client-side account files (runtime data)
    ├── common/
    │   ├── Packet.java
    │   └── SenderState.java
    └── server/
        ├── Server.java
        ├── ClientHandler.java
        ├── UsersLoader.java
        ├── SocialGraphLoader.java
        ├── ProjectPaths.java
        ├── directories/           # Uploaded images, descriptions, notifications
        └── profiles/              # Profile and followed-user activity files
```

## Getting Started

### Prerequisites

- A JDK installed, with `java` and `javac` available in your terminal.
- A terminal or IDE capable of running Java classes.

## Run
1. Open the project

Download or clone the repository and open it in a Java IDE.

2. Start the Server

Open src/server/Server.java and run its main() method.

3. Start the Client

Open src/client/Client.java and run its main() method.

4. Connect multiple users

Run Client.java multiple times to simulate multiple users interacting with the server.

**Default connection settings:** Both `Server.java` and `Client.java` use TCP port **303**, and the client connects to **`localhost`**. To connect from another machine, update the server address in `Client.java` and ensure the server is reachable.

Important: Always start the Server before launching any Clients.

## Using the Application

At startup, the client offers:

```text
1) Log In
2) Sign Up
3) Exit
```

After authentication, the main menu provides:

```text
1) Upload Image from Client to Server
2) Search Image on server
3) Follow
4) Unfollow
5) Access Profile
6) Check Notifications
7) Exit
```

`Server.java` listens for connections and submits a `ClientHandler` for each accepted client to a fixed thread pool. `Client.java` manages the terminal interface and socket communication. Shared transfer classes are located in `src/common/`.

### File transfer

The implementation uses application-level packets (`Packet`) with a sequence number and a byte-array payload. The transfer logic includes acknowledgments and retry handling.

### Data persistence

The application uses files instead of a database:

- `data/users.txt` stores user account records.
- `data/SocialGraph.txt` stores follow relationships.
- `src/server/directories/` stores server-side image files, description files, and notification files.
- `src/server/profiles/` stores profile activity and followed-account activity.
- `src/client/users/` contains account-specific local client files.
