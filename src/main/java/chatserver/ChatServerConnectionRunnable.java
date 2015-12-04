package chatserver;

import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.io.*;
import communication.*;

public class ChatServerConnectionRunnable implements Runnable{
    private Socket _con;
    private ChatServerModel _model;
    private String _user;
    private boolean close=false;
    private Channel clientChan;

    private boolean running = false;

    public ChatServerConnectionRunnable(Socket con, ChatServerModel model) throws IOException{
        this._con = con;    
        this._model = model;
        this.clientChan = new TCPChannel(_con);
    }
    
    @Override
    public void run(){
        try{
            String inp;
            while((inp=clientChan.readLine())!=null){
                String res = respond(inp);
                clientChan.sendLine(res);
                if (inp.equals("!logout")&&(_user!=null)&&close){
                    _con.close();
                    _user=null;
                }
            }
            _con.close();
        }
        catch (Exception e){
            _model.removeClient(this);
            if(_user!=null) _model.logout(_user);
        }
        if(_con.isClosed()){
            _model.removeClient(this);
            if(_user!=null) _model.logout(_user);
        }
    }

    private String respond(String in){
        if(in.startsWith("!login")){
            String[] cred = in.split(" ");
            if(cred.length == 3 && _user==null&& _model.logIn(cred[1],cred[2])){
                _model.addClient(this);
                _user=cred[1];
                return "Successfully logged in";
            }else{
                return "Login failed";
            }
        }else if(in.equals("!logout")){
            if(_user!=null && _model.logout(_user)){
                close = true;
                _model.removeClient(this);
                return "Logged out";
            }else{
                return "Error logging out";
            }
        }else if(in.startsWith("!send")){
            if(_user!=null){
                _model.sendMessage(_user,in.substring(6));
                return "Successfully sent";
            }else{
                return "Not logged in.";
            }
        }else if(in.startsWith("!register")){
            String[] vals = in.split(" ");
            if(_user!= null){
                if(vals.length!=2) return "Parameter Missmatch";
                return _model.register(_user,vals[1]) ? "Successfully regisered for "+_user:
                        "Register failed";
            }else{
                return "Not logged in";
            }
        }else if(in.startsWith("!lookup")){
            String[] vals = in.split(" ");
            if(_user!= null){
                if(vals.length!=2) return "Parameter Missmatch";
                return _model.lookup(vals[1]);
            }else{
                return "Not logged in";
            }
        }
        return "Command not found";
    }

    public void sendMessage(String user, String msg){
        if (_user != null && !_user.equals(user)){
            try{
                clientChan.sendLine(msg);
            }catch (IOException e) {}
        }
    }
}
