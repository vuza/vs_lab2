package communication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public interface Channel{
    default String readLine() throws IOException{
        return new String(readLineBytes(),StandardCharsets.UTF_8);
    }

    default void sendLine(String line) throws IOException{
        sendLineBytes(line.getBytes(StandardCharsets.UTF_8));
    }
    byte[] readLineBytes() throws IOException;
    void sendLineBytes(byte[] line) throws IOException;
}
