package chatserver;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Vector;

public class ChatServerSocketRunnable implements Runnable{

    private boolean _stopped = false;
    private ServerSocket servSoc;
    private int _port;

    private ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private ChatServerModel model;
    private List<Socket> socs = new Vector<>();

    public ChatServerSocketRunnable(int port, ChatServerModel model){
        this._port=port;
        this.model = model;
    }

    @Override
    public void run(){
        try{
            servSoc= new ServerSocket(_port);
        }
        catch(Exception e){
            
        }
        while(!stopped()){
            Socket incConnection;
            try{
                incConnection = servSoc.accept();
                socs.add(incConnection);
                this.threadPool.execute(new ChatServerConnectionRunnable(incConnection,model));
            }
            catch (Exception e){
                if(!stopped()){
                    //real error
                }
            }
        }
        this.threadPool.shutdownNow();
    }

    public synchronized boolean stopped(){
        return this._stopped;
    }

    public synchronized void stop() {
        this._stopped=true;
        try{
            this.threadPool.shutdownNow();
            this.servSoc.close();
            for(Socket soc : socs){
                if(soc!=null) soc.close();
            }
        }catch(Exception e){
            // couldn't stop server ??
        }
    }
}
