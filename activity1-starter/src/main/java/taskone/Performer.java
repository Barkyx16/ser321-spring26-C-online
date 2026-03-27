package taskone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

import taskone.proto.Request;
import taskone.proto.Response;
import taskone.proto.TaskProto;

public class Performer {

    private Socket socket;
    private TaskList taskList;

    private InputStream inStream;
    private OutputStream outStream;

    public Performer(Socket socket, TaskList taskList) {
        this.socket = socket;
        this.taskList = taskList;
    }

    public void doPerform() {

        try {
            inStream = socket.getInputStream();
            outStream = socket.getOutputStream();

            Response welcome = Response.newBuilder()
                    .setType(Response.ResponseType.SUCCESS)
                    .setMessage("Connected to the task server")
                    .build();

            welcome.writeDelimitedTo(outStream);

            while (true) {

                Request request = Request.parseDelimitedFrom(inStream);

                if (request == null) {
                    break;
                }

                Response response;

                switch (request.getType()) {

                    case ADD:
                        response = handleAdd(request);
                        break;

                    case LIST:
                        response = handleList(request);
                        break;

                    case FINISH:
                        response = handleFinish(request);
                        break;

                    case QUIT:
                        response = Response.newBuilder()
                                .setType(Response.ResponseType.SUCCESS)
                                .setMessage("Disconnected")
                                .build();

                        response.writeDelimitedTo(outStream);
                        return;

                    default:
                        response = Response.newBuilder()
                                .setType(Response.ResponseType.ERROR)
                                .setMessage("Bad request")
                                .build();
                }

                response.writeDelimitedTo(outStream);
            }

        } catch (IOException e) {
            System.out.println("Client connection closed.");
        }
    }

    private Response handleAdd(Request request) {

        String description = request.getDescription();
        String category = request.getCategory();

        if (description == null || description.trim().isEmpty()) {

            return Response.newBuilder()
                    .setType(Response.ResponseType.ERROR)
                    .setMessage("Description missing")
                    .build();
        }

        if (!category.equals("work")
                && !category.equals("personal")
                && !category.equals("school")
                && !category.equals("other")) {

            return Response.newBuilder()
                    .setType(Response.ResponseType.ERROR)
                    .setMessage("Category not valid")
                    .build();
        }

        Task task = taskList.addTask(description, category);

        TaskProto protoTask = TaskProto.newBuilder()
                .setId(task.getId())
                .setDescription(task.getDescription())
                .setCategory(task.getCategory())
                .setFinished(task.isFinished())
                .build();

        return Response.newBuilder()
                .setType(Response.ResponseType.SUCCESS)
                .setTask(protoTask)
                .setMessage("Task saved")
                .build();
    }

    private Response handleList(Request request) {

        String filter = request.getFilter();

        if (filter == null || filter.isEmpty()) {
            filter = "all";
        }

        List<Task> tasks;

        if (filter.equals("all")) {
            tasks = taskList.listAllTasks();
        } else if (filter.equals("pending")) {
            tasks = taskList.listPendingTasks();
        } else if (filter.equals("finished")) {
            tasks = taskList.listFinishedTasks();
        } else {

            return Response.newBuilder()
                    .setType(Response.ResponseType.ERROR)
                    .setMessage("Unknown filter")
                    .build();
        }

        taskone.proto.TaskList.Builder builder =
                taskone.proto.TaskList.newBuilder();

        builder.setCount(tasks.size());

        for (Task task : tasks) {

            TaskProto protoTask = TaskProto.newBuilder()
                    .setId(task.getId())
                    .setDescription(task.getDescription())
                    .setCategory(task.getCategory())
                    .setFinished(task.isFinished())
                    .build();

            builder.addTasks(protoTask);
        }

        return Response.newBuilder()
                .setType(Response.ResponseType.SUCCESS)
                .setTaskList(builder.build())
                .setMessage("Tasks returned")
                .build();
    }

    private Response handleFinish(Request request) {

        int id = request.getId();

        boolean done = taskList.finishTask(id);

        if (done) {

            return Response.newBuilder()
                    .setType(Response.ResponseType.SUCCESS)
                    .setMessage("Task marked finished")
                    .build();
        }

        return Response.newBuilder()
                .setType(Response.ResponseType.ERROR)
                .setMessage("Task not found")
                .build();
    }
}
