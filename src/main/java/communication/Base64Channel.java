package communication;


import java.io.IOException;
import org.bouncycastle.util.encoders.Base64;

public class Base64Channel implements Channel{

    private Channel _supChan;

    public Base64Channel(Channel superChan) throws IOException{
        this._supChan = superChan;
    }

    public byte[] readLineBytes() throws IOException{
        return Base64.decode(_supChan.readLineBytes());   
    }

    public void sendLineBytes(byte[] line) throws IOException{
        _supChan.sendLineBytes(Base64.encode(line));
    }

}
