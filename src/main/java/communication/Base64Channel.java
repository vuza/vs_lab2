package communication;


import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.bouncycastle.util.encoders.Base64;

public class Base64Channel implements Channel{

    private Channel _supChan;

    public Base64Channel(Channel superChan) throws IOException{
        this._supChan = superChan;
    }

    public String readLine() throws IOException{
        return new String(readLineBytes(), StandardCharsets.UTF_8);
    }

    public void sendLine(String line) throws IOException{
        sendLineBytes(line.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] readLineBytes() throws IOException{
        return Base64.decode(_supChan.readLineBytes());   
    }

    public void sendLineBytes(byte[] line) throws IOException{
        _supChan.sendLineBytes(Base64.encode(line));
    }

}
