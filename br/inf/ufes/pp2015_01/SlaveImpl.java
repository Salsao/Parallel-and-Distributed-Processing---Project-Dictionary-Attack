/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.inf.ufes.pp105_01;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Eduardo
 */
public class SlaveImpl extends UnicastRemoteObject implements Slave {

    public SlaveImpl() throws RemoteException {
        super();
    }

    public static Master stub;
    public static Slave slave;
    public static List<String> dictionary;
    

    public static void main(final String args[]) throws FileNotFoundException, IOException {

        dictionary = new ArrayList<String>();
        BufferedReader br = null;
        br = new BufferedReader(new FileReader("dictionary.txt"));
        for (String key; (key = br.readLine()) != null;) {
            dictionary.add(key);
        }

        //registros
        System.setProperty("java.rmi.server.hostname", args[1]);
        final String host = (args.length < 3) ? null : args[2];
        try {
            slave = (Slave) new SlaveImpl();
            Registry registry = LocateRegistry.getRegistry(host);
            stub = (Master) registry.lookup("mestre");

            stub.addSlave(slave, args[0]);
            System.out.println("Slave running");
        } catch (Exception e) {
            System.out.println(e);
        }
        //chamar a funcao de registrar a cada 30 segundos
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    Registry registry = LocateRegistry.getRegistry(host);
                    stub = (Master) registry.lookup("mestre");
                    stub.addSlave(slave, args[0]);
                } catch (Exception ex) {
                    System.out.println("Erro ao contactar ao Mestre, tentando novamente em 30 segundos.");
                }
            }
        }, 30 * 1000, 30 * 1000);

    }

    public static byte[] decrypt(String key, byte[] ciphertext) {
        // args[0] e a chave a ser usada
        // args[1] e o nome do arquivo de entrada
        byte[] decrypted = null;

        try {

            byte[] byteKey = key.getBytes();
            SecretKeySpec keySpec = new SecretKeySpec(byteKey, "Blowfish");

            Cipher cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            //System.out.println("message size (bytes) = " + ciphertext.length);
            decrypted = cipher.doFinal(ciphertext);

            return decrypted;

        } catch (javax.crypto.BadPaddingException e) {
            // essa excecao e jogada quando a senha esta incorreta
            // porem nao quer dizer que a senha esta correta se nao jogar essa excecao
            //System.out.println("Senha invalida.");
            return null;

        } catch (Exception e) {
            //dont try this at home
            return null;
        }

    }

    public static int find(byte[] source, byte[] match) {
        // sanity checks
        if (source == null || match == null) {
            return -1;
        }
        if (source.length == 0 || match.length == 0) {
            return -1;
        }
        int ret = -1;
        int spos = 0;
        int mpos = 0;
        byte m = match[mpos];
        for (; spos < source.length; spos++) {
            if (m == source[spos]) {
                // starting match
                if (mpos == 0) {
                    ret = spos;
                } // finishing match
                else if (mpos == match.length - 1) {
                    return ret;
                }
                mpos++;
                m = match[mpos];
            } else {
                ret = -1;
                mpos = 0;
                m = match[mpos];
            }
        }
        return ret;
    }

    static long count = 0;
	
	//Calculo de overhead
	public void overHead(byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex, SlaveManager callbackinterface) throws RemoteException{
		callbackinterface.checkpoint((int)initialwordindex);
	}

    @Override
    public void startSubAttack(byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex, SlaveManager callbackinterface) throws RemoteException {

        final SlaveManager copy = callbackinterface;

        //chamar o checkpoint a cada 10 segundos
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    copy.checkpoint(count);

                } catch (RemoteException ex) {
                   System.out.println("Mestre morto em combate.");
                }
            }
        }, 10 * 1000, 10 * 1000);
        try {

            

            byte[] decrypt;
            String key;
            //ler o arquivo e procurar as palavras
            
            for (int i = (int)initialwordindex; i < (int)finalwordindex; i++) {
                key = dictionary.get((int) i);
                decrypt = decrypt(key, ciphertext);
                if (decrypt != null) {
                    int findIt = find(decrypt, knowntext);
                    //se achou
                    if (findIt != -1) {
                        Guess guess = new Guess();
                        guess.setKey(key);
                        guess.setMessage(decrypt);
                        callbackinterface.foundGuess(i, guess);
                    }

                }
                if (i == finalwordindex - 1) {
                    callbackinterface.checkpoint(i);
                    timer.cancel();
                    timer.purge();
                    break;
                }
                count = i;

            }

        } catch (Exception ex) {
            Logger.getLogger(SlaveImpl.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

    }

}
