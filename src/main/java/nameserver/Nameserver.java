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
import java.util.*;

import cli.Command;
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
	//Key: Domain, Value: Remote Object (INameserver)
	private HashMap<String, INameserver> children;
	//Key: username, Value: address
	private HashMap<String, String> users;

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
		this.children = new HashMap<>();
		this.users = new HashMap<>();

		shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		shell.register(this);
		new Thread(shell).start();
	}

	@Override
	public void run() {

		String host = config.getString("registry.host");
		Integer port = config.getInt("registry.port");

		//root nameserver is the only nameserver that does not have the domain property
		if(!config.listKeys().contains("domain")){

			//create registry and a remote object of this nameserver
			try {
				registry = LocateRegistry.createRegistry(config.getInt("registry.port"));

				//Exports the remote object to make it available to receive incoming calls, using the particular supplied port.
				INameserver remoteObject = (INameserver) UnicastRemoteObject.exportObject(this,0);

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
			String domain = config.getString("domain");
			try {
				Registry registry = LocateRegistry.getRegistry(host,port);
				//lookup the remote object which is bound to registry
				INameserver root = (INameserver) registry.lookup(config.getString("root_id"));

				//create remote object of this nameserver class and register this nameserver in the network
				INameserver remoteObject = (INameserver) UnicastRemoteObject.exportObject(this, 0);
				root.registerNameserver(domain, remoteObject, remoteObject);

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

	@Command
	@Override
	public String nameservers() throws IOException {
		SortedSet<String> keys = new TreeSet<String>(children.keySet());
		String nameservers = "";
		int i = 1;
		for (String key : keys) {
			nameservers += i + ". " + key + "\n";
			i++;
		}
		return nameservers;
	}

	/**
	 * Prints out all users that are registered under this domain
	 * @throws IOException
     */
	@Command
	@Override
	public String addresses() throws IOException {
		SortedSet<String> keys = new TreeSet<String>(users.keySet());
		String addresses = "";
		int i = 1;
		for (String key : keys) {
			addresses += i + ". " + key + " " + users.get(key) + "\n";
			i++;
		}
		return addresses;
	}

	@Override
	@Command
	public String exit() throws IOException {
		shell.close();
		UnicastRemoteObject.unexportObject(this, true);

		if(registry != null)
			UnicastRemoteObject.unexportObject(registry, true);

		return null;
	}

	/**
	 * This method registers a new nameserver in the network at specific location which is stated by the domain
	 * @param domain the location of the new nameserver
	 * @param nameserver
	 * @param nameserverForChatserver
	 * @throws RemoteException
	 * @throws AlreadyRegisteredException is thrown when the domain is already registered and saved as a child in the hashmap
	 * @throws InvalidDomainException is thrown when a nameserver tries to register with one or more subdomains even though the highest level domain was not yet registered
     */
	@Override
	public void registerNameserver(String domain, INameserver nameserver, INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		//split domain
		String[] domains = domain.split("\\.");

		if(domains.length == 1){
			//in this case the we are on the deepest level -> if already in children than the server is already registered
			if(children.get(domain)!=null){
				throw new AlreadyRegisteredException("The domain " + domain + " is already registered.");
			}else {
				userResponseStream.println("Successfully registered " + domain);
				children.put(domain,nameserver);
			}
		}else{
			String highestDomain = domains[domains.length-1];
			String subDomain = domain.substring(0,domain.length()-1-highestDomain.length());
			//is the highestDomain already in children?
			INameserver nameserverOfDomain = children.get(highestDomain);
			if(children.get(highestDomain)!=null){
				nameserverOfDomain.registerNameserver(subDomain, nameserver, nameserverForChatserver);
			}else{
				throw new InvalidDomainException("To register " + domain + " you have to register " + highestDomain + " first.");
			}
		}


	}


	/**
	 * Registers a user. the username contains the domain it wants to be registered to. domains are splitted by "."
	 * @param username
	 * @param address
	 * @throws RemoteException
	 * @throws AlreadyRegisteredException
	 * @throws InvalidDomainException
     */
	@Command
	@Override
	public void registerUser(String username, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		String[] splittedUsername = username.split("\\.");
		if(splittedUsername.length == 1){
			if(users.containsKey(username))
				throw new AlreadyRegisteredException("The user " + username + " is already registered");
			else {
				users.put(username,address);
			}
		}else {
			//In this case the request has to be delegated to next nameserver
			String domain = splittedUsername[splittedUsername.length-1];
			if(children.containsKey(domain)){
				children.get(domain).registerUser(username.substring(0,username.length()-domain.length()-1),address);
			}else
				throw new InvalidDomainException("The domain "+ domain + " is not registered!");
		}
	}

	/**
	 * checks if requested zone is on this nameserver as a child
	 * @param zone the domain string of the zone
	 * @return the nameserver of the requested zone
	 * @throws RemoteException
	 * @throws InvalidDomainException
     */
	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException, InvalidDomainException {
		if(children.containsKey(zone)){
			return children.get(zone);
		}else
			throw new InvalidDomainException("The requested zone: " + zone + " does not exist");
	}

	/**
	 * checks if the user is on this nameserver and returns the address
	 * @param username
	 * @return String of the private address of the user
	 * @throws RemoteException
     */
	@Override
	public String lookup(String username) throws RemoteException {
		if(users.containsKey(username))
			return users.get(username);
		else{
			try {
				shell.writeLine("The user \""+username + "\" is not yet registered.");
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
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
