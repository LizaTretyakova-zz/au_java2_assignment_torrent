import org.junit.Rule;
import org.junit.Test;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.assertEquals;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


public class ClientTest {

    private static final String TRACKER_ADDR = "127.0.0.1";
    private static final int MAGIC_WAIT_TIME = 5;
    //private static final byte[] TEXT = {0};
    //private static final byte[] TEXT = "Hello".getBytes();
    private static final byte[] TEXT =
            (" Forms FORM-29827281-12:\n" +
                    "Test Assessment Report\n" +
                    "\n" +
                    "This was a triumph.\n" +
                    "I'm making a note here:\n" +
                    "HUGE SUCCESS.\n" +
                    "It's hard to overstate\n" +
                    "my satisfaction.\n" +
                    "Aperture Science\n" +
                    "We do what we must\n" +
                    "because we can.\n" +
                    "For the good of all of us.\n" +
                    "Except the ones who are dead.\n" +
                    "\n" +
                    "But there's no sense crying\n" +
                    "over every mistake.\n" +
                    "You just keep on trying\n" +
                    "till you run out of cake.\n" +
                    "And the Science gets done.\n" +
                    "And you make a neat gun.\n" +
                    "For the people who are\n" +
                    "still alive.\n" +
                    "\n" +
                    "Forms FORM-55551-5:\n" +
                    "Personnel File Addendum:\n" +
                    "\n" +
                    "Dear <<Subject Name Here>>,\n" +
                    "\n" +
                    "I'm not even angry.\n" +
                    "I'm being so sincere right now.\n" +
                    "Even though you broke my heart.\n" +
                    "And killed me.\n" +
                    "And tore me to pieces.\n" +
                    "And threw every piece into a fire.\n" +
                    "As they burned it hurt because\n" +
                    "I was so happy for you!\n" +
                    "Now these points of data\n" +
                    "make a beautiful line.\n" +
                    "And we're out of beta.\n" +
                    "We're releasing on time.\n" +
                    "So I'm GLaD. I got burned.\n" +
                    "Think of all the things we learned\n" +
                    "for the people who are\n" +
                    "still alive.\n" +
                    "\n" +
                    "Forms FORM-55551-6:\n" +
                    "Personnel File Addendum Addendum:\n" +
                    "\n" +
                    "One last thing:\n" +
                    "\n" +
                    "Go ahead and leave me.\n" +
                    "I think I prefer to stay inside.\n" +
                    "Maybe you'll find someone else\n" +
                    "to help you.\n" +
                    "Maybe Black Mesa...\n" +
                    "THAT WAS A JOKE. HA HA. FAT CHANCE.\n" +
                    "Anyway, this cake is great.\n" +
                    "It's so delicious and moist.\n" +
                    "Look at me still talking\n" +
                    "when there's Science to do.\n" +
                    "When I look out there,\n" +
                    "it makes me GLaD I'm not you.\n" +
                    "I've experiments to run.\n" +
                    "There is research to be done.\n" +
                    "On the people who are\n" +
                    "still alive.\n" +
                    "\n" +
                    "PS: And believe me I am\n" +
                    "still alive.\n" +
                    "PPS: I'm doing Science and I'm\n" +
                    "still alive.\n" +
                    "PPPS: I feel FANTASTIC and I'm\n" +
                    "still alive.\n" +
                    "\n" +
                    "FINAL THOUGHT:\n" +
                    "While you're dying I'll be\n" +
                    "still alive.\n" +
                    "\n" +
                    "FINAL THOUGHT PS:\n" +
                    "And when you're dead I will be\n" +
                    "still alive.\n" +
                    "\n" +
                    "STILL ALIVE\n" +
                    "\n" +
                    "Still alive.\n" +
                    "\n" +
                    "\n").getBytes();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Tracker tracker = null;
    private Client client1 = null;
    private Client client2 = null;
    private File file = null;

    private void fillTempDir() throws IOException {
        try {
            file = folder.newFile();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        new DataOutputStream(new FileOutputStream(file)).write(TEXT);
    }

    @org.junit.Before
    public void setUp() throws Exception {
        fillTempDir();
        Files.deleteIfExists(Paths.get(Tracker.CONFIG_FILE));
        Files.deleteIfExists(Paths.get("./" + Client.CONFIG_FILE));
        Files.deleteIfExists(Paths.get(folder.getRoot().getPath() + Client.CONFIG_FILE));
        tracker = new Tracker();
        tracker.startTracker();
        client1 = new Client(folder.getRoot().getPath());
        client2 = new Client("./");
    }

    @org.junit.After
    public void tearDown() throws Exception {
        Files.deleteIfExists(Paths.get(Tracker.CONFIG_FILE));

        tracker.stopTracker();
        client1.stop();
        client2.stop();
    }

    @Test
    public void testIdle() {}

    @Test
    public void testListRequest() throws IOException {
        ClientConsoleUtils.list(TRACKER_ADDR);
    }

    @Test
    public void testNewfileRequest() throws IOException {
        ClientConsoleUtils.newfile(TRACKER_ADDR, file.getPath(), client1.getState());
    }

    @Test
    public void testNewFileAndList() throws IOException, InterruptedException {
        ClientConsoleUtils.list(TRACKER_ADDR);
        ClientConsoleUtils.newfile(TRACKER_ADDR, file.getPath(), client1.getState());

        TimeUnit.SECONDS.sleep(MAGIC_WAIT_TIME);

        ClientConsoleUtils.list(TRACKER_ADDR);
    }

    @Test
    public void testGetRequest() throws IOException {
        int id = ClientConsoleUtils.newfile(TRACKER_ADDR, file.getPath(), client1.getState());
        ClientConsoleUtils.get(TRACKER_ADDR, Integer.toString(id), client2.getState());
    }

    @Test
    public void testConnection() throws IOException, InterruptedException {

        int id = ClientConsoleUtils.newfile(TRACKER_ADDR, file.getPath(), client1.getState());
        ClientConsoleUtils.get(TRACKER_ADDR, Integer.toString(id), client2.getState());

        TimeUnit.SECONDS.sleep(MAGIC_WAIT_TIME);

        client1.run(TRACKER_ADDR);
        client2.run(TRACKER_ADDR);

        TimeUnit.SECONDS.sleep(MAGIC_WAIT_TIME);

        byte[][] contents1 = client1.getFileContents(id);
        byte[][] contents2 = client2.getFileContents(id);

        assertTrue(contents1[0][0] == contents2[0][0]);
        assertEquals(Arrays.toString(contents1[0]), Arrays.toString(contents2[0]));
    }

}
