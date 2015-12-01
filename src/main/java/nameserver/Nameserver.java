package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, INameserver, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private Shell shell;

	public static Registry registry;
	private List<Nameserver> children;

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
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);

	}

	@Override
	public void run() {

		//root nameserver is the only nameserver that does not have the domain property
		if(!config.listKeys().contains("domain")){

			//create registry and a remote object of this nameserver
			try {
				registry = LocateRegistry.createRegistry(config.getInt("registry.port"));

				//create the remote object. INameserver is extending Remote
				INameserver remoteObject = (INameserver) RemoteObject.toStub(this);

				//bind the remote object to the registry so that it can be called from other nameservers
				//binding name is specified in .properties xml
				registry.bind(config.getString("root_id"), remoteObject);

				shell.writeLine("The root nameserver has started");
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (AlreadyBoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else{
			//register the nameserver at registry
			String host = config.getString("registry.host");
			Integer port = config.getInt("registry.port");
			String domain = config.getString("domain");

			try {
				Registry registry = LocateRegistry.getRegistry(host,port);
				//lookup the remote object which is bound to registry
				INameserver root = (INameserver) registry.lookup(config.getString("root_id"));

				//create remote object of this nameserver class and pass it to the root nameserver
				root.registerNameserver(domain, root, root);

			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (NotBoundException e) {
				e.printStackTrace();
			} catch (AlreadyRegisteredException e) {
				e.printStackTrace();
			} catch (InvalidDomainException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public String nameservers() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String addresses() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String exit() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

	}

	@Override
	public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		return null;
	}

	@Override
	public String lookup(String username) throws RemoteException {
		return null;
	}



	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);
		nameserver.run();
	}
}
