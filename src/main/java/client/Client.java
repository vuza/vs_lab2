package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.*;
import java.net.*;
import util.Config;
import cli.*;
import org.bouncycastle.util.encoders.Base64;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

    private Socket clientSock;
    private Shell clientShell;
    private Thread clientThread;
    private BufferedReader tcpIn;
    private PrintWriter tcpOut;
    private Thread shellThread;

    private String lastMesg=null;
    private ClientPrivateListener pmListener;
    private boolean expectingResponse=false;

    private String response= null;
	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Client(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

        this.clientShell = new Shell(componentName, userRequestStream, userResponseStream);
        this.clientShell.register(this);
        this.clientThread = new Thread(this);

        this.shellThread = new Thread(clientShell);

        try{
            this.clientSock = new Socket(config.getString("chatserver.host"),
                        config.getInt("chatserver.tcp.port"));

            this.tcpIn = new BufferedReader(new InputStreamReader(clientSock.getInputStream()));
            this.tcpOut = new PrintWriter(clientSock.getOutputStream(),true);
            this.clientThread.start();
            this.shellThread.start();
        }
        catch(Exception e){
            System.out.println("Connection failed");
        }
	}

	@Override
	public void run() {
        try{
            String fromServ;
            while(!clientSock.isClosed()){
                String res = tcpIn.readLine();
                if(!expectingResponse){
                    lastMesg = res;
                    clientShell.writeLine(res);
                }else{
                    expectingResponse = false;
                    response = res;
                }
                Thread.yield();
            }
        }catch (Exception e){
        }
	}

    private synchronized String tcpRespond(String command) throws IOException{
        if(!clientSock.isClosed()){
            tcpOut.print(command+'\n');
            tcpOut.flush();
            expectingResponse=true;
            while(expectingResponse)Thread.yield();
            return response;
        }else
            return "Connection failed";
    }
    
    @Command
	@Override
	public String login(String username, String password) throws IOException {
		return tcpRespond("!login "+username +" " + password);
	}
    
    @Command
	@Override
	public String logout() throws IOException {
		return tcpRespond("!logout");
	}
    
    @Command
	@Override
	public String send(String message) throws IOException {
		return tcpRespond("!send "+message);
	}
    
    @Command
	@Override
	public String list() throws IOException {
        DatagramSocket ds = new DatagramSocket();
        byte[] buffer = "!list".getBytes();
        DatagramPacket pac = new DatagramPacket(buffer, buffer.length,
            InetAddress.getByName(config.getString("chatserver.host")), 
            config.getInt("chatserver.udp.port"));
        ds.send(pac);

        buffer = new byte[1024];
        pac = new DatagramPacket(buffer, buffer.length);
        ds.receive(pac);
		return new String(pac.getData());
	}
    
    @Command
	@Override
	public String msg(String username, String message) throws IOException {
        //Message Integrity
        String hmac;
        try {
            //Create sha256_HMAC instance
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");

            //Read secret key from file and create SecretKeySpec
            Key secretKey = new SecretKeySpec(readFile(config.getString("hmac.key")).getBytes("UTF-8"), "HmacSHA256");

            //Init sha256_HMAC with our key
            sha256_HMAC.init(secretKey);

            //Encrypt sha256_HMAC with our message
            sha256_HMAC.update(message.getBytes());

            hmac = new String(Base64.encode(sha256_HMAC.doFinal()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "Error sending Message";
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return "Error sending Message";
        }

        String addr =tcpRespond("!lookup "+username);
        try{
            String[] addrsplit = addr.split(":");
            Socket usrSock = new Socket(addrsplit[0],Integer.parseInt(addrsplit[1]));
            BufferedReader usrIn = new BufferedReader(new InputStreamReader(usrSock.getInputStream()));
            PrintWriter usrOut = new PrintWriter(usrSock.getOutputStream(),true);
            usrOut.print(hmac + " " + message + "\n");
            usrOut.flush();
            String res = usrIn.readLine();
            usrSock.close();
            String[] resSplit = res.split(" ");
            if(resSplit.length >= 2 && resSplit[1].equals("!tampered"))
                return "Message got tampered!";

            return res;
        }catch(Exception e){
            return "Error sending Message";
        }
	}
    
    @Command
	@Override
	public String lookup(String username) throws IOException {
		return tcpRespond("!lookup "+username);
	}
    
    @Command
	@Override
	public String register(String privateAddress) throws IOException {
        try{
            this.pmListener = new ClientPrivateListener(this.clientShell, privateAddress);
            this.pmListener.start();
        }catch(Exception e){
            return "Failed to create Listener";
        }
		return tcpRespond("!register "+privateAddress);
	}

    @Command
	@Override
	public String lastMsg() throws IOException {
        if(this.lastMesg == null) return "No Messages available";
		return lastMesg;
	}
    
    @Command
	@Override
	public String exit() throws IOException {
        String res = logout();
        this.close();
        this.clientShell.close();
        return res;

	}

    private void close(){
        if(this.pmListener!=null){
            this.pmListener.stop();
            this.pmListener=null;
        }
        this.clientThread.interrupt();
    }

    /**
     * Reads all content from a file
     * @param path to read content from
     * @return Filecontent from @path
     * @throws IOException if file not readable
     */
    static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, "UTF-8");
    }

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
