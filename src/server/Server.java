package server;

import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;

public class Server {

    // clientID → SocketAddress (IP + port)
    public static ConcurrentHashMap<String, SocketAddress> clientDirectory = new ConcurrentHashMap<>();

    // Sockets
    private ServerSocket serverSocket;
    private Socket clientSocket;

    // ObjectStreams
    private ObjectOutputStream out;
    private ObjectInputStream in;
    
    // Port
    private final int port;

    // Thread pool for max 8 clients
    private ExecutorService threadPool = Executors.newFixedThreadPool(8); 

    // Constructor
    public Server(int port) {
      this.port = port;
    }

    // Main
    public static void main(String[] args) {
      Server server = new Server(303);
      server.start();
    }

    // Start server
    public void start () {

      try {

        serverSocket = new ServerSocket(port);
        System.out.println("Awaiting for connections...");
        
        while (true) {

          clientSocket = serverSocket.accept();
          System.out.println("Connection established!");

          ClientHandler clientHandler = new ClientHandler(clientSocket);
          threadPool.execute(clientHandler);

        }

      }
      catch (IOException e) {
        System.out.println("Error starting server: " + e.getMessage());
      }
    }

    // Close server by stopping all threads, sockets and streams
    public void stop() {
      try {
        in.close();
        out.close();
        if(clientSocket != null)clientSocket.close();
        if(serverSocket != null)serverSocket.close();
        if(threadPool != null)threadPool.shutdown();
      } catch (IOException e) {
        System.out.println("Error closing server: " + e.getMessage());
      }

    }

}