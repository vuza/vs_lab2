package chatserver;

import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class ChatServerModel{
    
    private List<ChatServerConnectionRunnable> clients = new Vector<>();
    protected Config chatserverConfig;

    class ClientData{
        boolean loggedin;
        String username;
        String password;
        boolean registered;
        String addr;

        public ClientData(String username){
            this.username=username;
        }

    }
    
    private Map<String, ClientData> users = new TreeMap<>();

    // user config
    public ChatServerModel(Config con, Config chatserverConfig){
        this.chatserverConfig = chatserverConfig;

        Set<String> names = con.listKeys();
        for(String entry : names){
            String uname = entry.substring(0,entry.lastIndexOf('.'));
            String prop = entry.substring(entry.lastIndexOf('.')+1);
            ClientData usr = users.containsKey(uname) ? users.get(uname) : new ClientData(uname);
            if(prop.equals("password")){
                usr.password = con.getString(entry);
            }
            users.put(uname,usr);
        }
    }

    public synchronized void addClient(ChatServerConnectionRunnable clnt){
        clients.add(clnt);
    }

    public synchronized void removeClient(ChatServerConnectionRunnable clnt){
        clients.remove(clnt);
    }

    public synchronized void sendMessage(String username, String msg){
        for(ChatServerConnectionRunnable clnt : clients){
            clnt.sendMessage(username,msg);
        }
    }

    public synchronized boolean loginEncrypted(String uname){
        if (!users.containsKey(uname)) return false;
        ClientData uobj = users.get(uname);
        if (!uobj.loggedin){
            uobj.loggedin = true;
            users.put(uname,uobj);
            return true;
        }
        return false;
    }

    public synchronized boolean logIn(String username, String passwd){
        if (!users.containsKey(username)) return false;
        ClientData uobj = users.get(username);
        if (uobj.password.equals(passwd)){
            uobj.loggedin = true;
            users.put(username,uobj);
            return true;
        }
        return false;
    }

    public synchronized boolean logout(String username){
        if(!users.containsKey(username)) return false;
        ClientData uobj = users.get(username);
        if(!uobj.loggedin) return false;
        uobj.loggedin = false;
        uobj.registered = false;
        users.put(username, uobj);
        return true;
    }

    public synchronized String listUser(){
        String res="";
        for(ClientData usr : users.values()){
            res += usr.username + " " + (usr.loggedin ? "online": "offline")+'\n';
        }
        return res;
    }

    public synchronized String listOnline(){
        String res="";
        for(ClientData usr : users.values()){
            if(usr.loggedin){
                res += usr.username + "; ";
            }
        }
        return res;
    }

    public synchronized boolean register(String username, String address){
        //Get registry
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(chatserverConfig.getInt("registry.port"));
        } catch (RemoteException e) {
            return false;
        }

        String[] usernameParts = username.split("\\.");

        //Get root ns
        INameserverForChatserver ns;
        try {
            ns = (INameserverForChatserver) registry.lookup("root-nameserver");
        } catch (RemoteException e) {
            return false;
        } catch (NotBoundException e) {
            return false;
        }

        try {
            ns.registerUser(username, address);
        } catch (Exception e){
            return false;
        }

        return true;
    }

    public synchronized String lookup(String username){
        //Get registry
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(chatserverConfig.getInt("registry.port"));
        } catch (RemoteException e) {
            return "No registry found";
        }

        String[] usernameParts = username.split("\\.");

        //Get root ns
        INameserverForChatserver ns;
        try {
            ns = (INameserverForChatserver) registry.lookup("root-nameserver");
        } catch (RemoteException e) {
            return "No name sever found.";
        } catch (NotBoundException e) {
            return "No name sever found.";
        }

        while(usernameParts.length > 1){
            try {
                ns = ns.getNameserver(usernameParts[usernameParts.length - 1]);
                usernameParts = Arrays.copyOf(usernameParts, usernameParts.length-1);
            } catch (Exception e){
                return "No name server found.";
            }
        }

        try {
            return ns.lookup(usernameParts[0]);
        } catch (RemoteException e) {
            return "User not found";
        }
    }
}
