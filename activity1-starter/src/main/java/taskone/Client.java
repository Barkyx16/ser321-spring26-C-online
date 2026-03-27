package taskone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

import taskone.proto.Request;
import taskone.proto.Response;
import taskone.proto.TaskProto;

public class Client {

    private static Socket socket;
    private static InputStream inStream;
    private static OutputStream outStream;
    private static Scanner scanner;

    public static void main(String[] args) {

        String host = "localhost";
        int port = 8888;

        if (args.length > 0) {
            host = args[0];
        }

        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Bad port number, using 8888.");
            }
        }

        scanner = new Scanner(System.in);

        try {
            System.out.println("Connecting to " + host + ":" + port + "...");
            socket = new Socket(host, port);

            inStream = socket.getInputStream();
            outStream = socket.getOutputStream();

            Response welcome = Response.parseDelimitedFrom(inStream);
            if (welcome != null) {
                System.out.println(welcome.getMessage());
            }

            boolean running = true;

            while (running) {
                printMenu();
                int choice = readChoice();

                switch (choice) {
                    case 1:
                        addTask();
                        break;
                    case 2:
                        listTasks();
                        break;
                    case 3:
                        finishTask();
                        break;
                    case 0:
                        quit();
                        running = false;
                        break;
                    default:
                        System.out.println("Not a valid option.");
                }
            }

        } catch (IOException e) {
            System.out.println("Could not connect to the server.");
        } finally {
            cleanup();
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("========== Task Menu ==========");
        System.out.println("1. Add Task");
        System.out.println("2. List Tasks");
        System.out.println("3. Finish Task");
        System.out.println("0. Quit");
        System.out.println("===============================");
        System.out.print("Choose one: ");
    }

    private static int readChoice() {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void addTask() {
        System.out.println();
        System.out.println("Add Task");

        System.out.print("Title: ");
        String title = scanner.nextLine().trim();

        System.out.print("Description: ");
        String description = scanner.nextLine().trim();

        if (description.isEmpty()) {
            System.out.println("Description can't be empty.");
            return;
        }

        System.out.print("Category (work/personal/school/other): ");
        String category = scanner.nextLine().trim().toLowerCase();

        if (!category.equals("work")
                && !category.equals("personal")
                && !category.equals("school")
                && !category.equals("other")) {
            System.out.println("Invalid category.");
            return;
        }

        Request request = Request.newBuilder()
                .setType(Request.RequestType.ADD)
                .setTitle(title)
                .setDescription(description)
                .setCategory(category)
                .build();

        Response response = sendRequest(request);

        if (response == null) {
            return;
        }

        if (response.getType() == Response.ResponseType.SUCCESS) {
            if (response.hasTask()) {
                TaskProto task = response.getTask();
                System.out.println("Task added.");
                System.out.println("ID: " + task.getId());
                System.out.println("Description: " + task.getDescription());
                System.out.println("Category: " + task.getCategory());
            } else {
                System.out.println(response.getMessage());
            }
        } else {
            System.out.println("Error: " + response.getMessage());
        }
    }

    private static void listTasks() {
        System.out.println();
        System.out.println("List Tasks");
        System.out.println("1. All");
        System.out.println("2. Pending");
        System.out.println("3. Finished");
        System.out.print("Choose one: ");

        int choice = readChoice();
        String filter;

        switch (choice) {
            case 1:
                filter = "all";
                break;
            case 2:
                filter = "pending";
                break;
            case 3:
                filter = "finished";
                break;
            default:
                System.out.println("Invalid option.");
                return;
        }

        Request request = Request.newBuilder()
                .setType(Request.RequestType.LIST)
                .setFilter(filter)
                .build();

        Response response = sendRequest(request);

        if (response == null) {
            return;
        }

        if (response.getType() == Response.ResponseType.SUCCESS) {
            if (!response.hasTaskList() || response.getTaskList().getCount() == 0) {
                System.out.println("No tasks found.");
                return;
            }

            for (TaskProto task : response.getTaskList().getTasksList()) {
                System.out.println(formatTask(task));
            }
        } else {
            System.out.println("Error: " + response.getMessage());
        }
    }

    private static void finishTask() {
        System.out.println();
        System.out.print("Task ID: ");

        int id;
        try {
            id = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("That isn't a valid number.");
            return;
        }

        Request request = Request.newBuilder()
                .setType(Request.RequestType.FINISH)
                .setId(id)
                .build();

        Response response = sendRequest(request);

        if (response == null) {
            return;
        }

        if (response.getType() == Response.ResponseType.SUCCESS) {
            System.out.println(response.getMessage());
        } else {
            System.out.println("Error: " + response.getMessage());
        }
    }

    private static void quit() {
        Request request = Request.newBuilder()
                .setType(Request.RequestType.QUIT)
                .build();

        Response response = sendRequest(request);

        if (response != null) {
            System.out.println(response.getMessage());
        }
    }

    private static Response sendRequest(Request request) {
        try {
            request.writeDelimitedTo(outStream);
            return Response.parseDelimitedFrom(inStream);
        } catch (IOException e) {
            System.out.println("Connection was lost.");
            return null;
        }
    }

    private static String formatTask(TaskProto task) {
        String status = task.getFinished() ? "[DONE]" : "[PENDING]";
        String category = task.getCategory().toUpperCase();

        return status + " #" + task.getId() + " [" + category + "] " + task.getDescription();
    }

    private static void cleanup() {
        try {
            if (scanner != null) {
                scanner.close();
            }
            if (inStream != null) {
                inStream.close();
            }
            if (outStream != null) {
                outStream.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error while closing the connection.");
        }
    }
}
