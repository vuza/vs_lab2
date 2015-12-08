package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.*;
import java.net.*;
import util.Config;
import util.Keys;
import cli.*;
import communication.*;
import org.bouncycastle.util.encoders.Base64;
import java.security.*;
import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

    private Socket clientSock;
    private Shell clientShell;
    private Thread clientThread;
    private Thread shellThread;
    private Channel tcpChannel;

    private String lastMesg=null;
    private ClientPrivateListener pmListener;
    private boolean expectingResponse=false;

    private String response= null;
    private boolean authenticated = false;
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

            this.tcpChannel = new TCPChannel(clientSock);
            this.shellThread.start();
            this.clientThread.start();
        }
        catch(Exception e){
            System.out.println("Connection failed");
        }
	}

	@Override
	public void run() {
        try{
            while(!authenticated) Thread.yield();
            while(!clientSock.isClosed()){
                String res = tcpChannel.readLine();
                
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
        if(!authenticated) return "Not authenticated";
        if(!clientSock.isClosed()){
            tcpChannel.sendLine(command);
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
        String addr =tcpRespond("!lookup "+username);
        try{
            String[] addrsplit = addr.split(":");
            Socket usrSock = new Socket(addrsplit[0],Integer.parseInt(addrsplit[1]));
            BufferedReader usrIn = new BufferedReader(new InputStreamReader(usrSock.getInputStream()));
            PrintWriter usrOut = new PrintWriter(usrSock.getOutputStream(),true);
            usrOut.print(message + "\n");
            usrOut.flush();
            String res = usrIn.readLine();
            usrSock.close();
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
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

    @Command
	@Override
	public String authenticate(String username) throws IOException {
        try{
            PublicKey serverKey = Keys.readPublicPEM(new File(config.getString("chatserver.key")));
            PrivateKey secretKey = Keys.readPrivatePEM(
                        new File(config.getString("keys.dir")+"/"+username+".pem"));

            
            
            this.tcpChannel = new Base64Channel(tcpChannel);
            Cipher rsa = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");

            SecureRandom rand = new SecureRandom();
            byte[] cliChall = new byte[32];
            rand.nextBytes(cliChall);
            cliChall = Base64.encode(cliChall);
            String clientChallange = new String(cliChall, StandardCharsets.UTF_8);
            String message = "!authenticate " + username + " " + clientChallange;
        
            rsa.init(Cipher.ENCRYPT_MODE,serverKey);
        
            tcpChannel.sendLineBytes(rsa.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            // 1st Message sent

            rsa.init(Cipher.DECRYPT_MODE, secretKey);
            
            String incMessage = new String(rsa.doFinal(tcpChannel.readLineBytes()),
                                    StandardCharsets.UTF_8);

            String[] messageParts = incMessage.split(" ");
            
            if (messageParts.length != 5 || !incMessage.startsWith("!ok ")) 
                    return "Error authenticating - RSA failed";

            if (!(messageParts[1]).equals(clientChallange))
                    return "Error authenticating - Client Challange failed\n"+incMessage+"\n"+message;

            String servChall = messageParts[2];
            
            byte[] aeskeybytes = Base64.decode(messageParts[3].getBytes(StandardCharsets.UTF_8));
            SecretKey aeskey = new SecretKeySpec(aeskeybytes, 0, aeskeybytes.length, "AES");

            byte[] iv = Base64.decode(messageParts[4].getBytes(StandardCharsets.UTF_8));

            this.tcpChannel = new AESChannel(tcpChannel, aeskey, new IvParameterSpec(iv));

            tcpChannel.sendLine(servChall);
             // send back server challange to finish handshake
            authenticated = true;
            return incMessage;
        }catch(Exception e){

        }
		return "Error authenticating";
	}

}
