package chatserver;

import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.io.*;

public class ChatServerConnectionRunnable implements Runnable{
    private Socket _con;

    public ChatServerConnectionRunnable(Socket con){
        this._con = con;    
        
    }
    
    @Override
    public void run(){
        synchronized(_con){
            try{
                PrintWriter out = new PrintWriter(_con.getOutputStream(),true);
                BufferedReader in = new BufferedReader(
                                new InputStreamReader(_con.getInputStream()));
                String inp;
                while((inp=in.readLine())!=null){
                    out.print(respond(inp)+"\n");
                    out.flush();
                }
                _con.close();
            }
            catch (Exception e){

            }
            finally{
            }
        }
    }

    private String respond(String in){
        if (in.equals("!quit")){
            try{
                _con.close();
            }catch (Exception e){

            }
        }
        return in;
    }
}
