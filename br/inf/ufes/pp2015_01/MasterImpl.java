/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.inf.ufes.pp105_01;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eduardo
 */
public class MasterImpl implements Master {

    public class StructSlave {

        int slaveKey;
        String slavename;
        private int startLength;
        private int finalLength;

        Slave slave;

        public StructSlave(int slaveKey, String slavename, Slave slave) {
            this.slaveKey = slaveKey;
            this.slavename = slavename;
            this.slave = slave;
        }

        public Slave getSlave() {
            return slave;
        }

        public int getSlaveKey() {
            return slaveKey;
        }

        public int getStartLength() {
            return startLength;
        }

        public void setStartLength(int startLength) {
            this.startLength = startLength;
        }

        public int getFinalLength() {
            return finalLength;
        }

        public void setFinalLength(int finalLength) {
            this.finalLength = finalLength;
        }

    }

    List<StructSlave> slaves = new ArrayList<StructSlave>();
    int currentKey = 0;
    List<Guess> guesses = new ArrayList<Guess>();
    List<Integer> checkpoints = new ArrayList<Integer>();
    static long initTimer;
    static int slavesSize;
    static boolean writeFile = true;

	//Adicionar escravo
    @Override
    public int addSlave(Slave s, String slavename) throws RemoteException {
        //registrar e re-registrar escravos
        currentKey++;
        StructSlave slave = new StructSlave(currentKey, slavename, s);
        boolean alreadyExists = false;

        synchronized (slaves) {
            for (StructSlave slavei : slaves) {
                if (slavei.slavename.equals(slavename)) {
                    alreadyExists = true;
                    slaves.remove(slavei);
                    break;
                }
            }
            if (alreadyExists == false) {
                slaves.add(slave);
                System.out.println("Escravo " + slave.slavename + " adicionado com sucesso");
            } else {
                slaves.add(slave);
                System.out.println("Escravo " + slave.slavename + " \"re-registrado\" com sucesso");
            }
        }
        return currentKey;
    }

    //remover escravos
    @Override
    public void removeSlave(int slaveKey) throws RemoteException {
        synchronized (slaves) {
            for (StructSlave slave : slaves) {
                if (slave.getSlaveKey() == slaveKey) {
                    slaves.remove(slaveKey);
                    break;
                }
            }
        }
    }

	//Caso encontrou uma palavra chave candidata
    @Override
    public void foundGuess(long currentindex, Guess currentguess) throws RemoteException {

        Long timer = System.nanoTime() - initTimer;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);
        List<StructSlave> slavesCopyFoundGuess = new ArrayList<StructSlave>();
        synchronized (slavesCopy) {
            for (StructSlave slave1 : slavesCopy) {
                slavesCopyFoundGuess.add(slave1);
            }
        }

        int batch = 80368 / slavesSize;
        long index = currentindex / batch;
        if (index >= slavesSize) {
            index = slavesSize - 1;
        }
        timers[(int) index].cancel();
        timers[(int) index].purge();

        slaveTimeOut((int) index);

        slavesCopyFoundGuess.get((int) index).setStartLength((int) currentindex + 1);
        synchronized (guesses) {
            String messageString = new String(currentguess.getMessage(), 0, currentguess.getMessage().length);
            System.out.println("FoundGuess: [" + df.format(timer / 1000000000.0) + " segundos] " + "Escravo " + slavesCopyFoundGuess.get((int) index).slavename + " encontrou a mensagem candidata \"" + currentguess.getMessage() + "\" usando a palavra-chave de indice: " + currentindex); //mostrar a palavra e nao o index
            guesses.add(currentguess);
        }
    }

    public static Timer[] timers;
	
	//Funcao para remover o escravo caso o mesmo fique 20 segundos sem responder
    public void slaveTimeOut(final int index) {

        timers[index] = new Timer();
        timers[index].schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (slavesCopy) {
                    System.out.println("Escravo " + slavesCopy.get(index).slavename + " removido por nao responder em 20s.");
                    if (slaves.contains(slavesCopy.get(index))) {
                        slaves.remove(slavesCopy.get(index));
                    }
                    timers[index].cancel();
                }
            }
        }, 20 * 1000, 20 * 1000);
    }

	//Checkpoint dos escravos a cada 10 segundos ou quando acaba
    @Override
    public void checkpoint(long currentindex) throws RemoteException {

        Long timer = System.nanoTime() - initTimer;
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(2);

        List<StructSlave> slavesCopyCheckpoint = new ArrayList<StructSlave>();
        synchronized (slavesCopy) {
            for (StructSlave slave1 : slavesCopy) {
                slavesCopyCheckpoint.add(slave1);
            }
        }

        int workingLength = finalLength - initialLength;
        int batch = workingLength / slavesSize;

        long index = currentindex / batch;
        if (index >= slavesSize) {
            index = slavesSize - 1;
        }

        synchronized (checkpoints) {
            while (checkpoints.size() < index) {
                checkpoints.add(new Integer(0));
            }
            checkpoints.add((int) index, (int) currentindex);
        }

        timers[(int) index].cancel();
        timers[(int) index].purge();

        slaveTimeOut((int) index);
        slavesCopyCheckpoint.get((int) index).setStartLength((int) currentindex + 1);

        System.out.println("Checkpoint: " + "[tempo: " + df.format(timer / 1000000000.0) + " segundos] " + slavesCopyCheckpoint.get((int) index).slavename + " no index " + currentindex);

    }

    public static int initialLength = 0;
    public static int finalLength = 80368;
    public static List<StructSlave> slavesCopy;
	
	public Guess[] attackOverHead(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		List<RunningSlaveOH> slaveThreads = new ArrayList<RunningSlaveOH>();
        List<Thread> threads = new ArrayList<Thread>();
        RunningSlaveOH runningSlaveOH;
        Thread thread;
        if (slavesCopy.size() != 0) {
            for (StructSlave slave : slavesCopy) {

                if (slavesCopy.indexOf(slave) != slavesSize - 1) {
                    try {
                        //entrega para o escravo os indices do dicionario
                        slave.setStartLength(batch * slavesCopy.indexOf(slave) + initialLength);
                        slave.setFinalLength(batch * slavesCopy.indexOf(slave) + batch + initialLength);
                        runningSlaveOH = new RunningSlaveOH(slave.getSlave(), ciphertext, knowntext, batch * slavesCopy.indexOf(slave) + initialLength, batch * slavesCopy.indexOf(slave) + batch + initialLength, this);
                        slaveThreads.add(runningSlaveOH);
                        thread = new Thread(runningSlaveOH);
                        threads.add(thread);
                        thread.start();
                    } catch (Exception e) {
                        System.out.println("Erro ao entregar a tarefa ao escravo " + slave.slavename);
                    }
                } else {
                    try {
                        //entrega para o escravo os indices do dicionario
                        slave.setStartLength(batch * slavesCopy.indexOf(slave) + initialLength);
                        slave.setFinalLength(batch * slavesCopy.indexOf(slave) + batch + difference + initialLength);
                        runningSlaveOH = new RunningSlaveOH(slave.getSlave(), ciphertext, knowntext, batch * slavesCopy.indexOf(slave) + initialLength, batch * slavesCopy.indexOf(slave) + batch + difference + initialLength, this);
                        slaveThreads.add(runningSlaveOH);
                        thread = new Thread(runningSlaveOH);
                        threads.add(thread);
                        thread.start();
                    } catch (Exception e) {
                        System.out.println("Erro ao entregar a tarefa ao escravo " + slave.slavename);
                    }
                }
            }

            //esperar resposta das threads
            for (Thread threadOne : threads) {
                try {
                    threadOne.join();
                } catch (InterruptedException ex) {
                    System.out.println(ex);
                }
            }
        } else {
            System.out.println("Mestre General Comandante nao ataca sozinho");
        }
		
		Guess[] guessesArray;
        synchronized (guesses) {
            guessesArray = new Guess[guesses.size()];
            guesses.toArray(guessesArray);
        }
		
		return guessesArray;
	}

	//Ataque
    @Override
    public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {

        long initialAttackTimer = System.nanoTime();
        slavesCopy = new ArrayList<StructSlave>();

        synchronized (slaves) {
            for (StructSlave slave1 : slaves) {
                slavesCopy.add(slave1);
            }
        }
        timers = new Timer[slavesCopy.size()];
        for (int i = 0; i < slavesCopy.size(); i++) {
            timers[i] = new Timer();
            slaveTimeOut(i);
        }

        int workingLength = finalLength - initialLength;
        slavesSize = slavesCopy.size();
        int batch = workingLength / slavesSize;
        int difference = workingLength - batch * slavesSize;

        //Criar uma lista de threads
        List<RunningSlave> slaveThreads = new ArrayList<RunningSlave>();
        List<Thread> threads = new ArrayList<Thread>();
        RunningSlave runningSlave;
        Thread thread;
        if (slavesCopy.size() != 0) {
            for (StructSlave slave : slavesCopy) {

                if (slavesCopy.indexOf(slave) != slavesSize - 1) {
                    try {
                        //entrega para o escravo os indices do dicionario
                        slave.setStartLength(batch * slavesCopy.indexOf(slave) + initialLength);
                        slave.setFinalLength(batch * slavesCopy.indexOf(slave) + batch + initialLength);
                        runningSlave = new RunningSlave(slave.getSlave(), ciphertext, knowntext, batch * slavesCopy.indexOf(slave) + initialLength, batch * slavesCopy.indexOf(slave) + batch + initialLength, this);
                        slaveThreads.add(runningSlave);
                        thread = new Thread(runningSlave);
                        threads.add(thread);
                        thread.start();
                    } catch (Exception e) {
                        System.out.println("Erro ao entregar a tarefa ao escravo " + slave.slavename);
                    }
                } else {
                    try {
                        //entrega para o escravo os indices do dicionario
                        slave.setStartLength(batch * slavesCopy.indexOf(slave) + initialLength);
                        slave.setFinalLength(batch * slavesCopy.indexOf(slave) + batch + difference + initialLength);
                        runningSlave = new RunningSlave(slave.getSlave(), ciphertext, knowntext, batch * slavesCopy.indexOf(slave) + initialLength, batch * slavesCopy.indexOf(slave) + batch + difference + initialLength, this);
                        slaveThreads.add(runningSlave);
                        thread = new Thread(runningSlave);
                        threads.add(thread);
                        thread.start();
                    } catch (Exception e) {
                        System.out.println("Erro ao entregar a tarefa ao escravo " + slave.slavename);
                    }
                }
            }

            //esperar resposta das threads
            for (Thread threadOne : threads) {
                try {
                    threadOne.join();
                } catch (InterruptedException ex) {
                    System.out.println(ex);
                }
            }
        } else {
            System.out.println("Mestre General Comandante nao ataca sozinho");
        }

		//Cancelar os tempos
        for (Timer timer : timers) {
            timer.cancel();
            timer.purge();
        }
        
		//Verifica se os escravos fizeram tudo certo, caso nao, distribuir o restante do trabalho para outros escravos
        for (RunningSlave rs : slaveThreads) {
            if (!rs.successful) {
                StructSlave slaveFix = slavesCopy.get(slaveThreads.indexOf(rs));
                initialLength = slaveFix.getStartLength();
                finalLength = slaveFix.getFinalLength();
                synchronized (slaves) {
                    if (slaves.contains(slaveFix)) {
                        slaves.remove(slaveFix);
                    }
                }
                attack(ciphertext, knowntext);
                initialLength = 0;
                finalLength = 80368;
            }
        }

		//
        Guess[] guessesArray;
        synchronized (guesses) {
            guessesArray = new Guess[guesses.size()];
            guesses.toArray(guessesArray);
        }

        long finalAttackTimer = System.nanoTime();
        long executionTimer = finalAttackTimer - initialAttackTimer;
        //criar o arquivo CSV

        FileWriter fw = null;
        try {
            fw = new FileWriter("resultsOH.csv", true);
        } catch (IOException ex) {
            Logger.getLogger(MasterImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        PrintWriter out = new PrintWriter(fw);

        out.print(executionTimer);
        out.print("\n");
        out.flush();
        out.close();

        return guessesArray;
    }

    static int dictionaryLenght;

    public static void main(String args[]) throws IOException {
        BufferedReader br = null;
        br = new BufferedReader(new FileReader("dictionary.txt"));
        int dictionaryLenght = 0;
        for (String key; (key = br.readLine()) != null;) {
            dictionaryLenght++;
        }
        try {
            System.setProperty("java.rmi.server.hostname", args[0]);
            MasterImpl obj = new MasterImpl();
            Master objref = (Master) UnicastRemoteObject.exportObject(obj, 2001);
            Registry reg = LocateRegistry.getRegistry();
            reg.rebind("mestre", objref);
            System.out.println("Master running");

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public class RunningSlave implements Runnable {

        private Slave slave;
        byte[] ciphertext;
        byte[] knowntext;
        long initialwordindex;
        long finalwordindex;
        SlaveManager callbackinterface;
        boolean successful = false;

        public RunningSlave(Slave slave, byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex, SlaveManager callbackinterface) {
            this.slave = slave;
            this.ciphertext = ciphertext;
            this.knowntext = knowntext;
            this.initialwordindex = initialwordindex;
            this.finalwordindex = finalwordindex;
            this.callbackinterface = callbackinterface;
        }

        @Override
        public void run() {
            try {
                initTimer = System.nanoTime();
                slave.startSubAttack(ciphertext, knowntext, initialwordindex, finalwordindex, callbackinterface);
                successful = true;
            } catch (Exception ex) {
                System.out.println("Erro na execucao de um escravo");
            }
        }
    }
	
	public class RunningSlaveOH implements Runnable {

        private Slave slave;
        byte[] ciphertext;
        byte[] knowntext;
        long initialwordindex;
        long finalwordindex;
        SlaveManager callbackinterface;

        public RunningSlaveOH(Slave slave, byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex, SlaveManager callbackinterface) {
            this.slave = slave;
            this.ciphertext = ciphertext;
            this.knowntext = knowntext;
            this.initialwordindex = initialwordindex;
            this.finalwordindex = finalwordindex;
            this.callbackinterface = callbackinterface;
        }

        @Override
        public void run() {
            try {
                initTimer = System.nanoTime();
                slave.overHead(ciphertext, knowntext, initialwordindex, finalwordindex, callbackinterface);
            } catch (Exception ex) {
                System.out.println("Erro na execucao de um escravo");
            }
        }
    }

}
