package chatserver;

import util.Config;

import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Vector;

public class ChatServerModel{
    
    private List<ChatServerConnectionRunnable> clients = new Vector<>();
    
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
    public ChatServerModel(Config con){
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
        if (!users.containsKey(username)) return false;
        ClientData uobj = users.get(username);
        if(uobj.addr != null) return false;
        uobj.addr = address;
        uobj.registered = true;
        users.put(username,uobj);
        return true;
    }

    public synchronized String lookup(String username){
        if (!users.containsKey(username)) return "User not found";
        ClientData uobj = users.get(username);
        if(uobj.addr != null && uobj.registered)
            return uobj.addr;
        return "User not registered";
    }
}
