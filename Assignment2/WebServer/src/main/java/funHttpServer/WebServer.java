package funHttpServer;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.charset.Charset;

class WebServer {
  public static void main(String args[]) {
    new WebServer(9000);
  }

  public WebServer(int port) {
    ServerSocket server = null;

    try {
      server = new ServerSocket(port);

      while (true) {
        Socket sock = null;
        InputStream in = null;
        OutputStream out = null;

        try {
          sock = server.accept();
          out = sock.getOutputStream();
          in = sock.getInputStream();

          byte[] response = createResponse(in);
          if (response == null) {
            response = buildTextResponse(500, "Internal Server Error", "Internal Server Error");
          }
          out.write(response);
          out.flush();
        } catch (Exception ex) {
          ex.printStackTrace();
        } finally {
          try { if (in != null) in.close(); } catch (Exception ignored) {}
          try { if (out != null) out.close(); } catch (Exception ignored) {}
          try { if (sock != null) sock.close(); } catch (Exception ignored) {}
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (server != null) {
        try { server.close(); } catch (IOException e) { e.printStackTrace(); }
      }
    }
  }

  private final static HashMap<String, String> _images = new HashMap<>() {
    {
      put("streets", "https://iili.io/JV1pSV.jpg");
      put("bread", "https://iili.io/Jj9MWG.jpg");
    }
  };

  private Random random = new Random();

  public byte[] createResponse(InputStream inStream) {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(inStream, "UTF-8"));

      String request = null;

      boolean done = false;
      while (!done) {
        String line = in.readLine();
        System.out.println("Received: " + line);

        if (line == null || line.equals("")) {
          done = true;
        } else if (line.startsWith("GET")) {
          int firstSpace = line.indexOf(" ");
          int secondSpace = line.indexOf(" ", firstSpace + 1);

          // FIX: extract path correctly
          request = line.substring(firstSpace + 1, secondSpace);
          if (request.startsWith("/")) request = request.substring(1); // normalize
        }
      }

      if (request == null) {
        return buildTextResponse(400, "Bad Request", "Illegal request: no GET");
      }

      StringBuilder builder = new StringBuilder();

      // ---------------- Root page ----------------
      if (request.length() == 0) {
        String page = new String(readFileInBytes(new File("www/root.html")));
        page = page.replace("${links}", buildFileList());

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append(page);

      // ---------------- Random JSON ----------------
      } else if (request.equalsIgnoreCase("json")) {
        int index = random.nextInt(_images.size());
        String header = (String) _images.keySet().toArray()[index];
        String url = _images.get(header);

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: application/json; charset=utf-8\n\n");
        builder.append("{");
        builder.append("\"header\":\"").append(header).append("\",");
        builder.append("\"image\":\"").append(url).append("\"");
        builder.append("}");

      // ---------------- Random HTML ----------------
      } else if (request.equalsIgnoreCase("random")) {
        File file = new File("www/index.html");

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append(new String(readFileInBytes(file)));

      // ---------------- File ----------------
      } else if (request.contains("file/")) {
        File file = new File(request.replace("file/", ""));

        if (file.exists()) {
          builder.append("HTTP/1.1 200 OK\n");
          builder.append("Content-Type: text/html; charset=utf-8\n\n");
          builder.append("Would theoretically be a file but removed this part, you do not have to do anything with it for the assignment");
        } else {
          builder.append("HTTP/1.1 404 Not Found\n");
          builder.append("Content-Type: text/html; charset=utf-8\n\n");
          builder.append("File not found: ").append(file);
        }

      // ---------------- 2.5.1 Multiply ----------------
      } else if (request.contains("multiply?")) {
        Map<String, String> qp;
        try {
          qp = splitQuery(request.replace("multiply?", ""));
        } catch (Exception e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: malformed query string. Use /multiply?num1=NUMBER&num2=NUMBER");
        }

        String num1Str = qp.get("num1");
        String num2Str = qp.get("num2");

        if (num1Str == null || num2Str == null || num1Str.isBlank() || num2Str.isBlank()) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: missing num1 or num2. Example: /multiply?num1=3&num2=4");
        }

        int num1, num2;
        try {
          num1 = Integer.parseInt(num1Str);
          num2 = Integer.parseInt(num2Str);
        } catch (NumberFormatException e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: num1 and num2 must be integers. Example: /multiply?num1=3&num2=4");
        }

        int result = num1 * num2;

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append("Result is: ").append(result);

      // ---------------- 2.5.2 GitHub (missing query) ----------------
      } else if (request.equalsIgnoreCase("github")) {
        return buildTextResponse(400, "Bad Request",
            "Bad Request: missing query parameter. Example: /github?query=users/amehlhase316/repos");

      // ---------------- 2.5.2 GitHub ----------------
      } else if (request.contains("github?")) {
        Map<String, String> qp;
        try {
          qp = splitQuery(request.replace("github?", ""));
        } catch (Exception e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: malformed query. Example: /github?query=users/amehlhase316/repos");
        }

        String query = qp.get("query");
        if (query == null || query.isBlank()) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: missing query parameter. Example: /github?query=users/amehlhase316/repos");
        }

        String url = "https://api.github.com/" + query;
        String json = fetchURL(url);

        if (json == null || json.isBlank()) {
          return buildTextResponse(404, "Not Found",
              "Not Found: GitHub returned no data. Check the query path.");
        }
        if (json.contains("\"message\"") && json.contains("Not Found")) {
          return buildTextResponse(404, "Not Found",
              "GitHub returned Not Found. Check your query path.");
        }
        if (json.contains("\"message\"") && json.contains("API rate limit exceeded")) {
          return buildTextResponse(429, "Too Many Requests",
              "GitHub rate limit exceeded. Try again later.");
        }

        ArrayList<Map<String, String>> rows = parseGitHubRepos(json);

        StringBuilder page = new StringBuilder();
        page.append("<html><body>");
        page.append("<h2>GitHub Repositories</h2>");
        page.append("<p>Fetched from: ").append(escapeHtml(url)).append("</p>");

        if (rows.size() == 0) {
          page.append("<p>No repos found (or response was not a repo list).</p>");
        } else {
          page.append("<table border='1' cellpadding='6' cellspacing='0'>");
          page.append("<tr><th>full_name</th><th>id</th><th>owner.login</th></tr>");
          for (Map<String, String> r : rows) {
            page.append("<tr>");
            page.append("<td>").append(escapeHtml(r.getOrDefault("full_name", ""))).append("</td>");
            page.append("<td>").append(escapeHtml(r.getOrDefault("id", ""))).append("</td>");
            page.append("<td>").append(escapeHtml(r.getOrDefault("owner_login", ""))).append("</td>");
            page.append("</tr>");
          }
          page.append("</table>");
        }

        page.append("<p>Example: /github?query=users/amehlhase316/repos</p>");
        page.append("</body></html>");

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append(page);

      // ---------------- 2.5.3 #1 Password generator ----------------
      } else if (request.contains("password?")) {
        Map<String, String> qp;
        try {
          qp = splitQuery(request.replace("password?", ""));
        } catch (Exception e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: malformed query. Example: /password?length=16&symbols=true");
        }

        String lenStr = qp.get("length");
        String symStr = qp.get("symbols");

        if (lenStr == null || symStr == null || lenStr.isBlank() || symStr.isBlank()) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: missing length or symbols. Example: /password?length=16&symbols=true");
        }

        int length;
        try {
          length = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: length must be an integer. Example: /password?length=16&symbols=true");
        }

        if (length < 8 || length > 64) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: length must be between 8 and 64.");
        }

        boolean symbols;
        if (symStr.equalsIgnoreCase("true") || symStr.equalsIgnoreCase("false")) {
          symbols = Boolean.parseBoolean(symStr);
        } else {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: symbols must be true or false. Example: /password?length=16&symbols=true");
        }

        String pw = generatePassword(length, symbols);

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append("<html><body>");
        builder.append("<h2>Password Generator</h2>");
        builder.append("<p><b>length</b>=").append(length).append(", <b>symbols</b>=").append(symbols).append("</p>");
        builder.append("<p><b>Password:</b> ").append(escapeHtml(pw)).append("</p>");
        builder.append("</body></html>");

      // ---------------- 2.5.3 #2 Temperature converter ----------------
      } else if (request.contains("convert?")) {
        Map<String, String> qp;
        try {
          qp = splitQuery(request.replace("convert?", ""));
        } catch (Exception e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: malformed query. Example: /convert?value=72&from=F&to=C");
        }

        String valueStr = qp.get("value");
        String fromStr = qp.get("from");
        String toStr = qp.get("to");

        if (valueStr == null || fromStr == null || toStr == null ||
            valueStr.isBlank() || fromStr.isBlank() || toStr.isBlank()) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: missing value/from/to. Example: /convert?value=72&from=F&to=C");
        }

        double value;
        try {
          value = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: value must be numeric. Example: /convert?value=72&from=F&to=C");
        }

        String from = fromStr.trim().toUpperCase();
        String to = toStr.trim().toUpperCase();

        if (!isTempUnit(from) || !isTempUnit(to)) {
          return buildTextResponse(400, "Bad Request",
              "Bad Request: from/to must be one of C, F, K. Example: /convert?value=72&from=F&to=C");
        }

        double converted = convertTemp(value, from, to);

        builder.append("HTTP/1.1 200 OK\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append("<html><body>");
        builder.append("<h2>Temperature Converter</h2>");
        builder.append("<p><b>Input:</b> ").append(value).append(" ").append(from).append("</p>");
        builder.append("<p><b>Output:</b> ").append(converted).append(" ").append(to).append("</p>");
        builder.append("</body></html>");

      } else {
        builder.append("HTTP/1.1 400 Bad Request\n");
        builder.append("Content-Type: text/html; charset=utf-8\n\n");
        builder.append("I am not sure what you want me to do...");
      }

      return builder.toString().getBytes();

    } catch (Exception e) {
      e.printStackTrace();
      return buildTextResponse(500, "Internal Server Error", "Internal Server Error");
    }
  }

  private static byte[] buildTextResponse(int code, String statusText, String body) {
    String resp =
        "HTTP/1.1 " + code + " " + statusText + "\n" +
        "Content-Type: text/html; charset=utf-8\n\n" +
        body;
    return resp.getBytes();
  }

  public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
    Map<String, String> query_pairs = new LinkedHashMap<>();
    if (query == null || query.isBlank()) return query_pairs;

    String[] pairs = query.split("&");
    for (String pair : pairs) {
      if (pair == null || pair.isBlank()) continue;
      int idx = pair.indexOf("=");

      if (idx < 0) {
        String key = URLDecoder.decode(pair, "UTF-8");
        query_pairs.put(key, "");
        continue;
      }

      String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
      String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
      query_pairs.put(key, value);
    }
    return query_pairs;
  }

  public static String buildFileList() {
    ArrayList<String> filenames = new ArrayList<>();
    File directoryPath = new File("www/");
    String[] listed = directoryPath.list();
    if (listed != null) filenames.addAll(Arrays.asList(listed));

    if (filenames.size() > 0) {
      StringBuilder builder = new StringBuilder();
      builder.append("<ul>\n");
      for (var filename : filenames) builder.append("<li>").append(filename).append("</li>");
      builder.append("</ul>\n");
      return builder.toString();
    }
    return "No files in directory";
  }

  public static byte[] readFileInBytes(File f) throws IOException {
    FileInputStream file = new FileInputStream(f);
    ByteArrayOutputStream data = new ByteArrayOutputStream(file.available());

    byte buffer[] = new byte[512];
    int numRead = file.read(buffer);
    while (numRead > 0) {
      data.write(buffer, 0, numRead);
      numRead = file.read(buffer);
    }
    file.close();

    byte[] result = data.toByteArray();
    data.close();
    return result;
  }

  public String fetchURL(String aUrl) {
    StringBuilder sb = new StringBuilder();
    URLConnection conn = null;
    InputStreamReader in = null;
    try {
      URL url = new URL(aUrl);
      conn = url.openConnection();
      if (conn != null) conn.setReadTimeout(20 * 1000);

      if (conn != null && conn.getInputStream() != null) {
        in = new InputStreamReader(conn.getInputStream(), Charset.defaultCharset());
        BufferedReader br = new BufferedReader(in);
        int ch;
        while ((ch = br.read()) != -1) sb.append((char) ch);
        br.close();
      }
      if (in != null) in.close();
    } catch (Exception ex) {
      System.out.println("Exception in url request:" + ex.getMessage());
    }
    return sb.toString();
  }

  // ---------- 2.5.2 JSON parsing (no external libs) ----------

  private static ArrayList<Map<String, String>> parseGitHubRepos(String json) {
    ArrayList<Map<String, String>> out = new ArrayList<>();
    ArrayList<String> objects = splitTopLevelJsonObjects(json);

    for (String obj : objects) {
      String fullName = jsonGetString(obj, "full_name");
      String id = jsonGetNumber(obj, "id");

      String ownerObj = jsonGetObject(obj, "owner");
      String ownerLogin = (ownerObj == null) ? "" : jsonGetString(ownerObj, "login");

      if ((fullName != null && !fullName.isBlank()) || (id != null && !id.isBlank())) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("full_name", fullName == null ? "" : fullName);
        row.put("id", id == null ? "" : id);
        row.put("owner_login", ownerLogin == null ? "" : ownerLogin);
        out.add(row);
      }
    }
    return out;
  }

  private static ArrayList<String> splitTopLevelJsonObjects(String json) {
    ArrayList<String> objs = new ArrayList<>();
    if (json == null) return objs;

    int i = 0;
    while (i < json.length() && json.charAt(i) != '[') i++;
    if (i >= json.length()) return objs;
    i++;

    int depth = 0;
    boolean inString = false;
    boolean escape = false;
    int start = -1;

    for (; i < json.length(); i++) {
      char c = json.charAt(i);

      if (escape) { escape = false; continue; }
      if (c == '\\') { escape = true; continue; }
      if (c == '"') { inString = !inString; continue; }
      if (inString) continue;

      if (c == '{') {
        if (depth == 0) start = i;
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          objs.add(json.substring(start, i + 1));
          start = -1;
        }
      }
    }
    return objs;
  }

  private static String jsonGetString(String obj, String key) {
    if (obj == null) return null;
    String needle = "\"" + key + "\"";
    int k = obj.indexOf(needle);
    if (k < 0) return null;

    int colon = obj.indexOf(':', k + needle.length());
    if (colon < 0) return null;

    int i = colon + 1;
    while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;
    if (i >= obj.length() || obj.charAt(i) != '"') return null;
    i++;

    StringBuilder sb = new StringBuilder();
    boolean esc = false;
    for (; i < obj.length(); i++) {
      char c = obj.charAt(i);
      if (esc) { sb.append(c); esc = false; }
      else if (c == '\\') esc = true;
      else if (c == '"') return sb.toString();
      else sb.append(c);
    }
    return null;
  }

  private static String jsonGetNumber(String obj, String key) {
    if (obj == null) return null;
    String needle = "\"" + key + "\"";
    int k = obj.indexOf(needle);
    if (k < 0) return null;

    int colon = obj.indexOf(':', k + needle.length());
    if (colon < 0) return null;

    int i = colon + 1;
    while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;

    int start = i;
    while (i < obj.length()) {
      char c = obj.charAt(i);
      if (!(Character.isDigit(c) || c == '-')) break;
      i++;
    }
    if (start == i) return null;
    return obj.substring(start, i);
  }

  private static String jsonGetObject(String obj, String key) {
    if (obj == null) return null;
    String needle = "\"" + key + "\"";
    int k = obj.indexOf(needle);
    if (k < 0) return null;

    int colon = obj.indexOf(':', k + needle.length());
    if (colon < 0) return null;

    int i = colon + 1;
    while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;
    if (i >= obj.length() || obj.charAt(i) != '{') return null;

    int start = i;
    int depth = 0;
    boolean inString = false;
    boolean escape = false;

    for (; i < obj.length(); i++) {
      char c = obj.charAt(i);

      if (escape) { escape = false; continue; }
      if (c == '\\') { escape = true; continue; }
      if (c == '"') { inString = !inString; continue; }
      if (inString) continue;

      if (c == '{') depth++;
      else if (c == '}') {
        depth--;
        if (depth == 0) return obj.substring(start, i + 1);
      }
    }
    return null;
  }

  private static String escapeHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  // ---------- 2.5.3 helpers ----------

  private static String generatePassword(int length, boolean symbols) {
    String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    String digits = "0123456789";
    String sym = "!@#$%^&*()-_=+[]{};:,.?/";
    String alphabet = letters + digits + (symbols ? sym : "");

    Random r = new Random();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  private static boolean isTempUnit(String u) {
    return u.equals("C") || u.equals("F") || u.equals("K");
  }

  private static double convertTemp(double value, String from, String to) {
    double c;
    switch (from) {
      case "C": c = value; break;
      case "F": c = (value - 32.0) * (5.0 / 9.0); break;
      case "K": c = value - 273.15; break;
      default: c = value;
    }

    switch (to) {
      case "C": return round4(c);
      case "F": return round4((c * (9.0 / 5.0)) + 32.0);
      case "K": return round4(c + 273.15);
      default: return round4(c);
    }
  }

  private static double round4(double x) {
    return Math.round(x * 10000.0) / 10000.0;
  }
}

