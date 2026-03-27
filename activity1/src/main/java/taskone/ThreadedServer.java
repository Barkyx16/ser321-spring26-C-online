package taskone;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ThreadedServer {

    private static final int DEFAULT_PORT = 8888;
    private static TaskList taskList = new TaskList();

    public static void main(String[] args) {

        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port. Using default 8888.");
            }
        }

        System.out.println("Threaded Task Server running on port " + port);
        System.out.println("Waiting for clients...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {

                Socket clientSocket = serverSocket.accept();

                new Thread(() -> {
                    try {
                        System.out.println("Client connected: "
                                + clientSocket.getInetAddress().getHostAddress());

                        PerformerProto performer =
                                new PerformerProto(clientSocket, taskList);

                        performer.doPerform();

                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            System.out.println("Problem closing client socket.");
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
