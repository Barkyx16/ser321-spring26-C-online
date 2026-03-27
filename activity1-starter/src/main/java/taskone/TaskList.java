package taskone;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskList {

    private final List<Task> tasks;
    private final AtomicInteger nextId;

    public TaskList() {
        tasks = new ArrayList<>();
        nextId = new AtomicInteger(1);
    }

    public synchronized Task addTask(String description, String category) {

        Task task = new Task(nextId.getAndIncrement(), description, category);
        tasks.add(task);

        return task;
    }

    public synchronized List<Task> listAllTasks() {

        return new ArrayList<>(tasks);
    }

    public synchronized List<Task> listPendingTasks() {

        List<Task> result = new ArrayList<>();

        for (Task task : tasks) {
            if (!task.isFinished()) {
                result.add(task);
            }
        }

        return result;
    }

    public synchronized List<Task> listFinishedTasks() {

        List<Task> result = new ArrayList<>();

        for (Task task : tasks) {
            if (task.isFinished()) {
                result.add(task);
            }
        }

        return result;
    }

    public synchronized Task findTaskById(int id) {

        for (Task task : tasks) {
            if (task.getId() == id) {
                return task;
            }
        }

        return null;
    }

    public synchronized boolean finishTask(int id) {

        Task task = findTaskById(id);

        if (task != null) {
            task.setFinished(true);
            return true;
        }

        return false;
    }

    public synchronized boolean delegateTask(int id, String assignee) {

        Task task = findTaskById(id);

        if (task != null) {
            task.setAssignee(assignee);
            return true;
        }

        return false;
    }

    public synchronized boolean deleteTask(int id) {

        Task task = findTaskById(id);

        if (task != null) {
            tasks.remove(task);
            return true;
        }

        return false;
    }

    public synchronized int getTaskCount() {

        return tasks.size();
    }
}
