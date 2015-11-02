package client;

import cli.Shell;

import java.net.*;
import java.io.*;

public class ClientPrivateListener implements Runnable{
    

    private ServerSocket listenSock;
    private Shell outShell;
    private boolean stopped=false;

    public ClientPrivateListener(Shell outShell, String privateAddr) throws Exception{
        String[] addrsplit = privateAddr.split(":");
        this.listenSock = new ServerSocket(Integer.parseInt(addrsplit[1]),10, 
                        InetAddress.getByName(addrsplit[0]));
            
        this.outShell = outShell;
    }

    public void run(){
        try{
            while (!stopped){
                Socket s = listenSock.accept();
                BufferedReader msgIn = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter msgOut = new PrintWriter(s.getOutputStream(), true);
                outShell.writeLine(msgIn.readLine());
                msgOut.print("!ack\n");
                msgOut.flush();
                s.close();
            }
        }catch (Exception e){
            if(!stopped){

            }
        }
    }

    public void stop(){
        try{
            stopped=true;
            listenSock.close();
        }catch(Exception e){
        }
    }

    public void start() throws Exception{
        new Thread(this).start();
    }
}
