package example.grpcclient;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class Node {
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(8000)
                .addService(new ConverterImpl())
                .addService(new LibraryImpl())
                .addService(new StudyBuddyImpl())
                .build();

        server.start();
        System.out.println("Server started on port 8000");
        server.awaitTermination();
    }
}
