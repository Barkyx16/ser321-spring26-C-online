package auction;

import buffers.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProtocolTest {

    private static final String HOST = "localhost";
    private static final int PORT = 8889;
    private static final int TIMEOUT = 5000;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    @BeforeEach
    public void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        socket.setSoTimeout(TIMEOUT);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    @AfterEach
    public void disconnect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Test
    @Order(1)
    public void testInitialWelcome() throws IOException {
        Response welcome = Response.parseDelimitedFrom(in);

        assertNotNull(welcome);
        assertEquals(Response.ResponseType.WELCOME, welcome.getType());
        assertTrue(welcome.getOk());
        assertTrue(welcome.getMessage().toLowerCase().contains("welcome"));
    }

    @Test
    @Order(2)
    public void testRegisterRequestValid() throws IOException {
        Response.parseDelimitedFrom(in);

        Request registerRequest = Request.newBuilder()
                .setType(Request.RequestType.REGISTER)
                .setName("TestPlayer")
                .build();
        registerRequest.writeDelimitedTo(out);

        Response response = Response.parseDelimitedFrom(in);

        assertNotNull(response);
        assertEquals(Response.ResponseType.WELCOME, response.getType());
        assertTrue(response.getOk());
        assertTrue(response.getMessage().contains("TestPlayer"));
        assertTrue(response.getMessage().contains("150") || response.getMessage().toLowerCase().contains("gold"));
    }

    @Test
    @Order(3)
    public void testRegisterRequestEmpty() throws IOException {
        Response.parseDelimitedFrom(in);

        Request registerRequest = Request.newBuilder()
                .setType(Request.RequestType.REGISTER)
                .setName("")
                .build();
        registerRequest.writeDelimitedTo(out);

        Response response = Response.parseDelimitedFrom(in);

        assertNotNull(response);
        assertEquals(Response.ResponseType.ERROR, response.getType());
        assertFalse(response.getOk());
        assertTrue(response.getMessage().toLowerCase().contains("empty"));
    }

    @Test
    @Order(4)
    public void testQuitRequest() throws IOException {
        Response.parseDelimitedFrom(in);

        Request quitRequest = Request.newBuilder()
                .setType(Request.RequestType.QUIT)
                .build();
        quitRequest.writeDelimitedTo(out);

        Response response = Response.parseDelimitedFrom(in);

        assertEquals(Response.ResponseType.FAREWELL, response.getType());
        assertTrue(response.getOk());
        assertTrue(response.getMessage().toLowerCase().contains("goodbye")
                || response.getMessage().toLowerCase().contains("bye"));
    }

    @Test
    @Order(5)
    public void testJoinStartsGame() throws IOException {
        Response.parseDelimitedFrom(in);

        sendRegister("Tester");
        sendJoin();

        Response response = Response.parseDelimitedFrom(in);

        assertNotNull(response);
        assertEquals(Response.ResponseType.GAME_JOINED, response.getType());
        assertTrue(response.getOk());
        assertTrue(response.hasPlayerStatus());
        assertTrue(response.hasNextItem());
        assertEquals(150, response.getPlayerStatus().getGoldRemaining());
    }

    @Test
    @Order(6)
    public void testSkipBidWorks() throws IOException {
        Response.parseDelimitedFrom(in);

        sendRegister("TesterSkip");
        sendJoin();

        Response joinResponse = Response.parseDelimitedFrom(in);
        int currentItemId = joinResponse.getNextItem().getId();

        Request bidRequest = Request.newBuilder()
                .setType(Request.RequestType.BID)
                .setItemId(currentItemId)
                .setBidAmount(-1)
                .build();
        bidRequest.writeDelimitedTo(out);

        Response response = Response.parseDelimitedFrom(in);

        assertNotNull(response);
        assertEquals(Response.ResponseType.BID_RESULT, response.getType());
        assertTrue(response.getOk());
        assertTrue(response.hasResult());
        assertTrue(response.hasPlayerStatus());
    }

    @Test
    @Order(7)
    public void testLeaderboardRequest() throws IOException {
        Response.parseDelimitedFrom(in);

        sendRegister("TesterBoard");

        Request leaderboardRequest = Request.newBuilder()
                .setType(Request.RequestType.LEADERBOARD)
                .build();
        leaderboardRequest.writeDelimitedTo(out);

        Response response = Response.parseDelimitedFrom(in);

        assertNotNull(response);
        assertEquals(Response.ResponseType.LEADERBOARD_RESPONSE, response.getType());
        assertTrue(response.getOk());
        assertTrue(response.hasLeaderboard());
    }

    private void sendRegister(String name) throws IOException {
        Request registerRequest = Request.newBuilder()
                .setType(Request.RequestType.REGISTER)
                .setName(name)
                .build();
        registerRequest.writeDelimitedTo(out);
        Response.parseDelimitedFrom(in);
    }

    private void sendJoin() throws IOException {
        Request joinRequest = Request.newBuilder()
                .setType(Request.RequestType.JOIN)
                .build();
        joinRequest.writeDelimitedTo(out);
    }
}
