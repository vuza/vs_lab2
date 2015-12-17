package communication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public interface Channel{
    String readLine() throws IOException;

    void sendLine(String line) throws IOException;

    byte[] readLineBytes() throws IOException;
    void sendLineBytes(byte[] line) throws IOException;
}
