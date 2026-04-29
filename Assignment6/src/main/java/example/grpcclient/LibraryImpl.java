package example.grpcclient;

import io.grpc.stub.StreamObserver;
import services.LibraryGrpc;
import services.LibraryOuterClass.BookList;
import services.LibraryOuterClass.BookReply;
import services.LibraryOuterClass.BookRequest;
import services.LibraryOuterClass.Empty;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LibraryImpl extends LibraryGrpc.LibraryImplBase {
    private final File file = new File("books.json");
    private final List<String> books = new ArrayList<>();

    public LibraryImpl() {
        load();
    }

    @Override
    public void addBook(BookRequest request, StreamObserver<BookReply> responseObserver) {
        String title = request.getTitle().trim();

        if (title.isEmpty()) {
            responseObserver.onNext(BookReply.newBuilder().setMessage("Please enter a real book title.").build());
            responseObserver.onCompleted();
            return;
        }

        books.add(title);
        save();

        responseObserver.onNext(BookReply.newBuilder().setMessage("Book added: " + title).build());
        responseObserver.onCompleted();
    }

    @Override
    public void listBooks(Empty request, StreamObserver<BookList> responseObserver) {
        responseObserver.onNext(BookList.newBuilder().addAllBooks(books).build());
        responseObserver.onCompleted();
    }

    private void load() {
        try {
            if (!file.exists()) {
                save();
                return;
            }

            String text = Files.readString(file.toPath()).trim();
            books.clear();

            if (text.length() <= 2) {
                return;
            }

            text = text.substring(1, text.length() - 1);

            for (String item : text.split(",")) {
                String cleaned = item.trim().replace("\"", "");
                if (!cleaned.isEmpty()) {
                    books.add(cleaned);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[");
            for (int i = 0; i < books.size(); i++) {
                writer.write("\"" + books.get(i).replace("\"", "") + "\"");
                if (i < books.size() - 1) {
                    writer.write(",");
                }
            }
            writer.write("]");
        } catch (Exception ignored) {
        }
    }
}
