package communication;

import javax.crypto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

public class AESChannel implements Channel{

    private Cipher _ciph;
    private Channel _supChan;
    private SecretKey _key;
    private AlgorithmParameterSpec _param;

    // new IvParameterSpec(iv);
    public AESChannel(Channel supChan, SecretKey key, AlgorithmParameterSpec param){
        this._supChan = supChan;
        this._key = key;
        this._param = param;
        try{
            this._ciph = Cipher.getInstance("AES/CTR/NoPadding");
        }catch(Exception e) {}
    }
    
    public byte[] readLineBytes() throws IOException{
        byte[] res= new byte[1];
        try{
            res = _supChan.readLineBytes();
            _ciph.init(Cipher.DECRYPT_MODE,_key,_param);
            
            res = _ciph.doFinal(res);
        }catch(IOException e){
            throw e;
        }catch(Exception e){
            
        }
        return res;
    }

    public void sendLineBytes(byte[] line) throws IOException{
        try{
            _ciph.init(Cipher.ENCRYPT_MODE,_key,_param);
            _supChan.sendLineBytes(_ciph.doFinal(line));
        }catch(Exception e){

        }
    }

}
