package br.inf.ufes.pp2015_01;
 
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
 
/*
  
 
  
 //javac *.java
 */ //rmiregistry ()java Master 192.168.2.14()ssh a2011100308@grad2xx // area de trabalho/master-slave() java Slave 192.168.2.xx 192.168.2.minhamaquina()java Client 192.168.2.14
/**
 *
 * java br.inf.ufes.pp2015_01.MasterImpl 192.168.2.14 java
 * br.inf.ufes.pp2015_01.Client 192.168.2.14 aaa.txt.cipher dia java
 * br.inf.ufes.pp2015_01.SlaveImpl Dovakhiin1 192.168.2.13 192.168.2.14 (pc que
 * roda mestre, pc do escravo)
 *
 * @author BOT
 */
 
public class Client {
 
    //ler arquivo
    private static byte[] readFile(String filename) throws IOException {
 
        File file = new File(filename);
        InputStream is = new FileInputStream(file);
        long length = file.length();
 
        byte[] data = new byte[(int) length];
 
        int offset = 0;
        int count = 0;
 
        while ((offset < data.length) && (count = is.read(data, offset, data.length - offset)) >= 0) {
            offset += count;
        }
        is.close();
        return data;
    }
     
	 //Funcao Decrypt
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
 
 //Funcao que compara os bytes
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
 
	//Funcao para testar o tempo sequencial
    private static void sequential(byte[] ciphertext, byte[] knowntext) throws FileNotFoundException, IOException {
 
        byte[] decrypt;
         
        //ler o arquivo e procurar as palavras
        BufferedReader br = null;
        br = new BufferedReader(new FileReader("dictionary.txt"));
        for (String key; (key = br.readLine()) != null;) {
 
            decrypt = decrypt(key, ciphertext);
            if (decrypt != null) {
                int findIt = find(decrypt, knowntext);
            }
        }
    }
 
    public static void main(String[] args) throws RemoteException, NotBoundException, FileNotFoundException, UnsupportedEncodingException, IOException {
 
		boolean sequential = false;
        //Para salvar o arquivo csv
        List<String> title = new ArrayList<>();
        title.add("Size of Vector");
        title.add("Time for directly");
        FileWriter fw = new FileWriter("results.csv");
        PrintWriter out = new PrintWriter(fw);
 
        for (int i = 0; i < title.size(); i++) {
            out.print(title.get(i) + "; ");
        }
        out.print("\n");
 
        //args[0] = ip
        //args[1] = nome do arquivo
        //args[2] = palavra conhecida
        //se nao existir o arquivo, tenta receber o tamanho do vetor, se nao cria randomico

 
        Registry registry = LocateRegistry.getRegistry(args[0]);
        Master stub = (Master) registry.lookup("mestre");
        String host = (args.length < 1) ? null : args[0];
        System.setProperty("java.rmi.server.hostname", args[0]);
        byte[] bytes;
        try {
            bytes = readFile(args[1]);
        } catch (IOException ex) {
            int tamanhoVetor;
            if (args.length < 4) {
                tamanhoVetor = 1000 + (int) (Math.random() * ((100000 - 1000) + 1));
                System.out.println("Arquivo nao encontrado. Gerando arquivo com tamanho aleatorio(" + tamanhoVetor + ").");
            } else {
                tamanhoVetor = Integer.parseInt(args[3]);
                System.out.println("Arquivo nao encontrado. Gerando arquivo com tamanho especificado de " + tamanhoVetor + " bytes.");
            }
            bytes = new byte[tamanhoVetor];
            new Random().nextBytes(bytes);

            //converter bytes em um arquivo
            FileOutputStream fileOuputStream;
            try {
                fileOuputStream = new FileOutputStream(args[1]);
                fileOuputStream.write(bytes);
                fileOuputStream.close();
            } catch (Exception ex1) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
 
        //enviar pro mestre e comecar o ataque
        Long start = System.nanoTime();
		if(!sequential && !overhead){
			Guess[] guesses = stub.attack(bytes, args[2].getBytes());
		}
		else if(sequential && !overhead){
			sequential(bytes, args[2].getBytes());
		}
		else if(overhead){
			Guess[] guesses = stub.attackOverHead(bytes, args[2].getBytes());
		}
        Long finish = System.nanoTime();
        Long time = finish - start;
        out.print(time);
        PrintWriter writer = null;
        String decryptedString;
        if (guesses != null) {
            for (Guess guess : guesses) {
                writer = new PrintWriter(guess.getKey() + ".msg", "UTF-8");
                decryptedString = new String(guess.getMessage(), 0, guess.getMessage().length);
                writer.println(decryptedString);
                System.out.println(guess.getKey());
            }
 
        }
 
        if (writer != null) {
            writer.close();
        }
        out.flush();
        out.close();
        fw.close();
 
    }
}
