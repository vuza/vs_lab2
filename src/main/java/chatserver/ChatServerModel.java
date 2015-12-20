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
        System.out.println("called with: " + username + " " + address); //DEBUG

        //Get registry
        Registry registry;
        try {
            System.out.println("in try"); //DEBUG
            registry = LocateRegistry.getRegistry(chatserverConfig.getInt("registry.port"));
            System.out.println("in try again"); //DEBUG
        } catch (RemoteException e) {
            System.out.println("Exception kwe"); //DEBUG
            return false;
        }

        System.out.println("got registery"); //DEBUG

        String[] usernameParts = username.split("\\.");

        //Get root ns
        INameserverForChatserver ns;
        try {
            ns = (INameserverForChatserver) registry.lookup("root-nameserver");
        } catch (RemoteException e) {
            System.out.println("Exception daf"); //DEBUG
            return false;
        } catch (NotBoundException e) {
            System.out.println("Exception dse"); //DEBUG
            return false;
        }

        System.out.println("got ns"); //DEBUG

        try {
            ns.registerUser(username, address);
        } catch (Exception e){
            System.out.println("Exception hhd"); //DEBUG
            return false;
        }

        System.out.println("Registered"); //DEBUG

        return true;
    }

    public synchronized String lookup(String username){
        System.out.println("called with: " + username); //DEBUG

        //Get registry
        Registry registry;
        try {
            registry = LocateRegistry.getRegistry(chatserverConfig.getInt("registry.port"));
        } catch (RemoteException e) {
            return "No registry found";
        }

        System.out.println("found registry"); //DEBUG

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

        System.out.println("got root ns"); //DEBUG

        System.out.println("before: " + usernameParts.length); //DEBUG
        while(usernameParts.length > 1){
            try {
                System.out.println("while: " + usernameParts.length); //DEBUG
                ns = ns.getNameserver(usernameParts[usernameParts.length - 1]);
                usernameParts = Arrays.copyOf(usernameParts, usernameParts.length-1);
            } catch (Exception e){
                return "No name server found.";
            }
        }

        System.out.println("found our ns"); //DEBUG

        try {
            return ns.lookup(usernameParts[0]);
        } catch (RemoteException e) {
            return "User not found";
        }
    }
}
