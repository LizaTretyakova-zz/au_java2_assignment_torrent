import org.junit.Rule;
import org.junit.Test;
import static junit.framework.Assert.assertEquals;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class ClientTest {

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
        new DataOutputStream(new FileOutputStream(file)).writeUTF(text);
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
    public void testConnection() throws IOException, InterruptedException {
        String trackerAddr = "127.0.0.1";
        client1.list(trackerAddr);
        int id = client1.newfile(trackerAddr, file.getPath());
        client2.get(trackerAddr, Integer.toString(id));

        client1.run(trackerAddr);
        client2.run(trackerAddr);

        client1.stop();
        client2.stop();

        byte[][] contents1 = client1.getFileContents(id);
        byte[][] contents2 = client2.getFileContents(id);

        assertEquals(contents1, contents2);
    }

    private static final String text =
            " Forms FORM-29827281-12:\n" +
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
                    "\n";
}