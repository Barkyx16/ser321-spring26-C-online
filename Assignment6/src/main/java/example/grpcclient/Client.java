package example.grpcclient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import services.ConverterGrpc;
import services.LibraryGrpc;
import services.StudyBuddyGrpc;
import services.ConverterOuterClass.TempRequest;
import services.LibraryOuterClass.BookRequest;
import services.LibraryOuterClass.Empty;
import services.Studybuddy.TaskRequest;
import services.Studybuddy.StudyEmpty;

import java.util.Scanner;

public class Client {

    public static void main(String[] args) {

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 8000)
                .usePlaintext()
                .build();

        ConverterGrpc.ConverterBlockingStub converterStub =
                ConverterGrpc.newBlockingStub(channel);

        LibraryGrpc.LibraryBlockingStub libraryStub =
                LibraryGrpc.newBlockingStub(channel);

        StudyBuddyGrpc.StudyBuddyBlockingStub studyStub =
                StudyBuddyGrpc.newBlockingStub(channel);

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println("\nChoose a service:");
            System.out.println("1 - Convert Celsius to Fahrenheit");
            System.out.println("2 - Convert Fahrenheit to Celsius");
            System.out.println("3 - Add book");
            System.out.println("4 - List books");
            System.out.println("5 - Add study task");
            System.out.println("6 - List study tasks");
            System.out.println("0 - Exit");

            String choice = scanner.nextLine();

            try {

                if (choice.equals("1")) {

                    System.out.print("Enter Celsius value: ");
                    double value = Double.parseDouble(scanner.nextLine());

                    double result = converterStub
                            .celsiusToFahrenheit(
                                    TempRequest.newBuilder().setValue(value).build()
                            )
                            .getValue();

                    System.out.println("Result: " + result);

                } else if (choice.equals("2")) {

                    System.out.print("Enter Fahrenheit value: ");
                    double value = Double.parseDouble(scanner.nextLine());

                    double result = converterStub
                            .fahrenheitToCelsius(
                                    TempRequest.newBuilder().setValue(value).build()
                            )
                            .getValue();

                    System.out.println("Result: " + result);

                } else if (choice.equals("3")) {

                    System.out.print("Enter book title: ");
                    String title = scanner.nextLine();

                    String message = libraryStub
                            .addBook(
                                    BookRequest.newBuilder().setTitle(title).build()
                            )
                            .getMessage();

                    System.out.println(message);

                } else if (choice.equals("4")) {

                    System.out.println(
                            libraryStub.listBooks(
                                    Empty.newBuilder().build()
                            ).getBooksList()
                    );

                } else if (choice.equals("5")) {

                    System.out.print("Enter study task: ");
                    String task = scanner.nextLine();

                    String message = studyStub
                            .addTask(
                                    TaskRequest.newBuilder().setTask(task).build()
                            )
                            .getMessage();

                    System.out.println(message);

                } else if (choice.equals("6")) {

                    System.out.println(
                            studyStub.listTasks(
                                    StudyEmpty.newBuilder().build()
                            ).getTasksList()
                    );

                } else if (choice.equals("0")) {

                    channel.shutdown();
                    break;

                } else {

                    System.out.println("Invalid option. Try again.");

                }

            } catch (Exception e) {

                System.out.println("Something went wrong. Please try again.");

            }
        }
    }
}
