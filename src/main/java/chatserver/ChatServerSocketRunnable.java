package chatserver;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServerSocketRunnable implements Runnable{

    private boolean _stopped = false;
    private ServerSocket servSoc;
    private int _port;

    private ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public ChatServerSocketRunnable(int port){
        this._port=port;
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
                this.threadPool.execute(new ChatServerConnectionRunnable(incConnection));
            }
            catch (Exception e){
                if(!stopped()){
                    //real error
                }
            }
        }
        this.threadPool.shutdown();
    }

    public synchronized boolean stopped(){
        return this._stopped;
    }

    public synchronized void stop(){
        this._stopped=true;
        try{
            this.servSoc.close();
        }catch(Exception e){
            // couldn't stop server ??
        }
    }
}
