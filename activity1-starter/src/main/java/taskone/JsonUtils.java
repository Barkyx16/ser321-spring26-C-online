package taskone;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Utility class for working with JSON requests and responses.
 */
public class JsonUtils {

    /**
     * Create a success response.
     * @param type Request type that succeeded
     * @param data Data to include in response
     * @return JSON response object
     */
    public static JSONObject createSuccessResponse(String type, Object data) {
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("type", type);
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }

    /**
     * Create an error response.
     * @param type Request type that failed
     * @param error Error message
     * @return JSON response object
     */
    public static JSONObject createErrorResponse(String type, String error) {
        JSONObject response = new JSONObject();
        response.put("ok", false);
        response.put("type", type);
        JSONObject errorData = new JSONObject();
        errorData.put("error", error);
        response.put("data", errorData);
        return response;
    }

    /**
     * Create an error response with details.
     * @param type Request type that failed
     * @param error Error message
     * @param details Additional error details
     * @return JSON response object
     */
    public static JSONObject createErrorResponse(String type, String error, String details) {
        JSONObject response = new JSONObject();
        response.put("ok", false);
        response.put("type", type);
        JSONObject errorData = new JSONObject();
        errorData.put("error", error);
        errorData.put("details", details);
        response.put("data", errorData);
        return response;
    }

    /**
     * Convert a Task object to JSON.
     * @param task Task to convert
     * @return JSON object representing the task
     */
    public static JSONObject taskToJson(Task task) {
        JSONObject json = new JSONObject();
        json.put("id", task.getId());
        json.put("description", task.getDescription());
        json.put("category", task.getCategory());
        json.put("finished", task.isFinished());
        return json;
    }
}
