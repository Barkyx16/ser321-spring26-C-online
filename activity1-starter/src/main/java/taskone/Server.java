package taskone;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private static final int DEFAULT_PORT = 8888;
    private static TaskList taskList = new TaskList();

    public static void main(String[] args) {

        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Using default port instead.");
            }
        }

        System.out.println("Task Management Server starting on port " + port);
        System.out.println("Mode: multi-threaded");
        System.out.println("Waiting for clients...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println("Client connected: "
                        + clientSocket.getInetAddress().getHostAddress());

                new Thread(() -> {

                    try {
                        Performer performer =
                                new Performer(clientSocket, taskList);

                        performer.doPerform();

                    } finally {

                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            System.out.println("Problem closing connection.");
                        }

                        System.out.println("Client disconnected");
                    }

                }).start();
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}
