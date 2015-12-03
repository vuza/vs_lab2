package client;

import cli.Shell;
import org.bouncycastle.util.encoders.Base64;
import util.Config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ClientPrivateListener implements Runnable{


    private ServerSocket listenSock;
    private Shell outShell;
    private boolean stopped=false;
    private Config config;

    public ClientPrivateListener(Shell outShell, String privateAddr) throws Exception{
        String[] addrsplit = privateAddr.split(":");
        this.listenSock = new ServerSocket(Integer.parseInt(addrsplit[1]),10, 
                        InetAddress.getByName(addrsplit[0]));
            
        this.outShell = outShell;

        this.config = new Config("client");
    }

    public void run(){
        try{
            while (!stopped){
                Socket s = listenSock.accept();

                BufferedReader msgIn = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter msgOut = new PrintWriter(s.getOutputStream(), true);

                String req = msgIn.readLine();
                //Message Integrity
                String hmac = req.split(" ")[0];

                String msg = req.substring(hmac.length() + 1);

                String computedHash = "";
                try {
                    //Create sha256_HMAC instance
                    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

                    //Read secret key from file and create SecretKeySpec
                    Key secretKey = new SecretKeySpec(Files.readAllBytes(Paths.get(config.getString("hmac.key"))), "HmacSHA256");

                    //Init sha256_HMAC with our key
                    sha256_HMAC.init(secretKey);

                    //Encrypt sha256_HMAC with our message
                    sha256_HMAC.update(msg.getBytes());

                    computedHash = new String(Base64.encode(sha256_HMAC.doFinal()));
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

                outShell.writeLine(msg);

                if(MessageDigest.isEqual(computedHash.getBytes(), hmac.getBytes())){
                    msgOut.print("!ack\n");
                } else
                    msgOut.print(hmac + " !tampered " + msg + "\n");

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
