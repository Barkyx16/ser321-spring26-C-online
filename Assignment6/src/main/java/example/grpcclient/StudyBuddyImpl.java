package example.grpcclient;

import io.grpc.stub.StreamObserver;
import services.Studybuddy;
import services.StudyBuddyGrpc;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class StudyBuddyImpl extends StudyBuddyGrpc.StudyBuddyImplBase {
    private final File file = new File("studybuddy.json");
    private final List<String> tasks = new ArrayList<>();

    public StudyBuddyImpl() {
        load();
    }

    @Override
    public void addTask(Studybuddy.TaskRequest request, StreamObserver<Studybuddy.TaskReply> responseObserver) {
        String task = request.getTask().trim();

        if (task.isEmpty()) {
            responseObserver.onNext(Studybuddy.TaskReply.newBuilder().setMessage("Please enter a real study task.").build());
            responseObserver.onCompleted();
            return;
        }

        tasks.add(task);
        save();

        responseObserver.onNext(Studybuddy.TaskReply.newBuilder().setMessage("Task added: " + task).build());
        responseObserver.onCompleted();
    }

    @Override
    public void listTasks(Studybuddy.StudyEmpty request, StreamObserver<Studybuddy.TaskList> responseObserver) {
        responseObserver.onNext(Studybuddy.TaskList.newBuilder().addAllTasks(tasks).build());
        responseObserver.onCompleted();
    }

    private void load() {
        try {
            if (!file.exists()) {
                save();
                return;
            }

            String text = Files.readString(file.toPath()).trim();
            tasks.clear();

            if (text.length() <= 2) {
                return;
            }

            text = text.substring(1, text.length() - 1);

            for (String item : text.split(",")) {
                String cleaned = item.trim().replace("\"", "");
                if (!cleaned.isEmpty()) {
                    tasks.add(cleaned);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[");
            for (int i = 0; i < tasks.size(); i++) {
                writer.write("\"" + tasks.get(i).replace("\"", "") + "\"");
                if (i < tasks.size() - 1) {
                    writer.write(",");
                }
            }
            writer.write("]");
        } catch (Exception ignored) {
        }
    }
}
