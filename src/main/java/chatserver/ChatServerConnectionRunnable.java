package chatserver;

import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.io.*;
import communication.*;
import org.bouncycastle.util.encoders.Base64;
import java.security.*;
import javax.crypto.*;
import java.nio.charset.StandardCharsets;
import util.*;
import javax.crypto.spec.IvParameterSpec;

public class ChatServerConnectionRunnable implements Runnable{
    private Socket _con;
    private ChatServerModel _model;
    private String _user;
    private boolean close=false;
    private Channel clientChan;
    private Config _conf;

    private boolean running = false;

    public ChatServerConnectionRunnable(Socket con, ChatServerModel model, Config cf) throws IOException{
        this._con = con;    
        this._conf = cf;
        this._model = model;
        this.clientChan = new Base64Channel(new TCPChannel(_con));
    }
    
    @Override
    public void run(){
        if (!handshake()){
           /* _model.removeClient(this);
            try{
                _con.close();
            }catch(Exception e){}
            return; */
        }
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

    private boolean handshake(){
        try{
            PrivateKey servPriv = Keys.readPrivatePEM(new File(_conf.getString("key")));
            PublicKey clientKey;
            Cipher rsa = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
            rsa.init(Cipher.DECRYPT_MODE,servPriv);
            SecureRandom rand = new SecureRandom();

            String message = new String(rsa.doFinal(clientChan.readLineBytes()),StandardCharsets.UTF_8);
            if(!message.startsWith("!authenticate")) return false;
            
            String[] messageparts =  message.split(" ");
            if(messageparts.length != 3) return false;
            
            clientKey = Keys.readPublicPEM(new File(_conf.getString("keys.dir")+
                                    "/"+messageparts[1]+".pub.pem"));
            
            // 1st Message recieved and checked

            byte[] srvChal = new byte[32];
            rand.nextBytes(srvChal);
            srvChal = Base64.encode(srvChal);
            String clientChallange = messageparts[2];
            String serverChallange = new String(srvChal,StandardCharsets.UTF_8);
            
            KeyGenerator keygen = KeyGenerator.getInstance("AES");
            keygen.init(256); // Keysize
            SecretKey aesKey = keygen.generateKey();
            String b64Key = new String(Base64.encode(aesKey.getEncoded()),StandardCharsets.UTF_8);

            byte[] ivarr = new byte[16];
            rand.nextBytes(ivarr);
            ivarr = Base64.encode(ivarr);
            String iv = new String(ivarr,StandardCharsets.UTF_8);

            message = "!ok "+ clientChallange + " " + serverChallange + " " + b64Key + " " + iv;

            rsa.init(Cipher.ENCRYPT_MODE,clientKey);
            clientChan.sendLineBytes(rsa.doFinal(message.getBytes(StandardCharsets.UTF_8)));

            // 2nd Message sent
            this.clientChan = new AESChannel(this.clientChan, aesKey, 
                            new IvParameterSpec(Base64.decode(ivarr)));

            if( clientChan.readLine().equals(serverChallange)){
                _model.addClient(this);
                _user = messageparts[1];
                return _model.loginEncrypted(messageparts[1]);
            }else {
                return false;
            }
        }catch(Exception e){
            return false;
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
