package communication;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TCPChannel implements Channel{ 
     
    Socket soc;
    PrintWriter out;
    BufferedReader in;

    public TCPChannel(Socket sc) throws IOException{
        this.soc = sc;
        this.in = new BufferedReader(new InputStreamReader(soc.getInputStream()));
        this.out = new PrintWriter(soc.getOutputStream(),true);
    }

    public String readLine() throws IOException{
        return new String(readLineBytes(),StandardCharsets.UTF_8);
    }

    public byte[] readLineBytes() throws IOException{
        if(soc.isClosed()) return ("Connection reset").getBytes(StandardCharsets.UTF_8);
        return in.readLine().getBytes(StandardCharsets.UTF_8);
    }

    public void sendLineBytes(byte[] line) throws IOException{
        out.print(new String(line,StandardCharsets.UTF_8) +"\n");
        out.flush(); 
    }

    public void sendLine(String line) throws IOException{
        sendLineBytes(line.getBytes(StandardCharsets.UTF_8));
    }

}
