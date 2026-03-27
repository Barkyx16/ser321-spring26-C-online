package taskone;

/**
 * Represents a task in the task management system.
 */
public class Task {
    private final int id;
    private final String description;
    private final String category;
    private String assignee;
    private boolean finished;

    public Task(int id, String description, String category) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.assignee = "unassigned";
        this.finished = false;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    @Override
    public String toString() {
        return String.format("Task #%d: %s [Category: %s, Assigned to: %s, Finished: %s]",
                id, description, category, assignee, finished);
    }
}
