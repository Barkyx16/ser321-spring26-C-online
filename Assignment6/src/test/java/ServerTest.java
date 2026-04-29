import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import services.ConverterGrpc;
import services.ConverterOuterClass.TempRequest;
import services.LibraryGrpc;
import services.LibraryOuterClass.BookRequest;
import services.LibraryOuterClass.Empty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerTest {
    private ManagedChannel channel;
    private ConverterGrpc.ConverterBlockingStub converter;
    private LibraryGrpc.LibraryBlockingStub library;

    @Before
    public void setUp() {
        channel = ManagedChannelBuilder
                .forAddress("localhost", 8000)
                .usePlaintext()
                .build();

        converter = ConverterGrpc.newBlockingStub(channel);
        library = LibraryGrpc.newBlockingStub(channel);
    }

    @After
    public void tearDown() {
        channel.shutdownNow();
    }

    @Test
    public void celsiusToFahrenheitWorks() {
        double result = converter
                .celsiusToFahrenheit(TempRequest.newBuilder().setValue(0).build())
                .getValue();

        assertEquals(32.0, result, 0.001);
    }

    @Test
    public void fahrenheitToCelsiusWorks() {
        double result = converter
                .fahrenheitToCelsius(TempRequest.newBuilder().setValue(212).build())
                .getValue();

        assertEquals(100.0, result, 0.001);
    }

    @Test
    public void addBookWorks() {
        String message = library
                .addBook(BookRequest.newBuilder().setTitle("The Hobbit").build())
                .getMessage();

        assertTrue(message.contains("The Hobbit"));
    }

    @Test
    public void listBooksWorks() {
        library.addBook(BookRequest.newBuilder().setTitle("Clean Code").build());

        boolean found = library
                .listBooks(Empty.newBuilder().build())
                .getBooksList()
                .contains("Clean Code");

        assertTrue(found);
    }

    @Test
    public void emptyBookTitleDoesNotCrash() {
        String message = library
                .addBook(BookRequest.newBuilder().setTitle("").build())
                .getMessage();

        assertTrue(message.toLowerCase().contains("real book"));
    }

    @Test
    public void blankBookTitleDoesNotCrash() {
        String message = library
                .addBook(BookRequest.newBuilder().setTitle("   ").build())
                .getMessage();

        assertTrue(message.toLowerCase().contains("real book"));
    }

    @Test
    public void negativeTemperatureStillConverts() {
        double result = converter
                .celsiusToFahrenheit(TempRequest.newBuilder().setValue(-40).build())
                .getValue();

        assertEquals(-40.0, result, 0.001);
    }

    @Test
    public void libraryKeepsDataAfterRestart() {
        library.addBook(BookRequest.newBuilder().setTitle("Restart Test Book").build());

        boolean found = library
                .listBooks(Empty.newBuilder().build())
                .getBooksList()
                .contains("Restart Test Book");

        assertTrue(found);
    }
}
