package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import util.Config;
import cli.*;

import java.net.*;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
    
    // DATA

    private ChatServerSocketRunnable tcpServ;
    private ChatServerModel servModel;
    private Shell servShell;
    private DatagramSocket udpSoc;

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
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
        
        this.servModel = new ChatServerModel(new Config("user"), config);
        this.servShell = new Shell(componentName, userRequestStream, userResponseStream);
        
        servShell.register(this);
        tcpServ = new ChatServerSocketRunnable(config.getInt("tcp.port"), servModel);
        try{
            udpSoc = new DatagramSocket(config.getInt("udp.port"));
        }catch(Exception e){
        }
        Thread servThread = new Thread(this);
        servThread.start();
	}

	@Override
	public void run() {
		Thread tcpServThread = new Thread(tcpServ);
        Thread shellThread = new Thread(this.servShell);
        try{
            tcpServThread.start();
            shellThread.start();

            byte[] buffer;
            DatagramPacket pac;
            while(true){
                buffer = new byte[1024];
                pac = new DatagramPacket(buffer, buffer.length);
                udpSoc.receive(pac);

                String va = new String(pac.getData());
                String res =this.servModel.listOnline();
                buffer = res.getBytes();
                pac = new DatagramPacket(buffer, buffer.length, pac.getAddress(), pac.getPort());
                udpSoc.send(pac);
            }
        }catch(Exception e){
        }finally{
            if(udpSoc != null && !udpSoc.isClosed()){
                udpSoc.close();
            }
        }
	}

    @Command
	@Override
	public String users() throws IOException {
		return servModel.listUser();
	}

    @Command
	@Override
	public String exit() throws IOException {
        udpSoc.close();
		tcpServ.stop();
        servShell.close();
        return "Exited";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0],
				new Config("chatserver"), System.in, System.out);
	}

}
