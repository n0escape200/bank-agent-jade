package jadebank.agents;

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jadebank.utils.DFServiceHelper;

import java.util.*;
import java.io.*;

public class BankAgent extends Agent {

    private HashMap<String, UserAccount> accounts = new HashMap<>();
    private static final String ACCOUNTS_FILE = "accounts.dat";

    static class UserAccount implements Serializable {
        private static final long serialVersionUID = 1L;
        String pin;
        double balance;
        List<String> transactionHistory = new ArrayList<>();

        UserAccount(String pin) {
            this.pin = pin;
            this.balance = 0.0;
        }

        void addTransaction(String transaction) {
            transactionHistory.add(new Date() + ": " + transaction);
            // Keep only last 100 transactions
            if (transactionHistory.size() > 100) {
                transactionHistory.remove(0);
            }
        }
    }

    @Override
    protected void setup() {
        System.out.println("[BankAgent] started: " + getLocalName());

        // Load accounts from file
        loadAccountsFromFile();

        // Register with DF
        DFServiceHelper.registerService(this, "banking-service", "bank-account-management");

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Check if it's a shutdown message
                    if (msg.getContent().equals("SHUTDOWN")) {
                        System.out.println("[BankAgent] Received shutdown command");
                        doDelete(); // Terminate agent
                        return;
                    }

                    ACLMessage reply = msg.createReply();
                    String[] parts = msg.getContent().split(" ");

                    if (parts.length < 1) {
                        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                        reply.setContent("Empty command");
                        send(reply);
                        return;
                    }

                    String command = parts[0].toUpperCase();
                    String userId = parts.length > 1 ? parts[1] : "";
                    String arg = parts.length > 2 ? parts[2] : "";

                    switch (command) {
                        case "OPEN_ACCOUNT":
                            if (!accounts.containsKey(userId)) {
                                accounts.put(userId, new UserAccount(arg));
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("Account created for " + userId + ". Balance: 0.0");

                                // Notify GUI - sends UPDATE message
                                notifyGUI(userId, 0.0, "ACCOUNT_CREATED");

                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Account already exists.");
                            }
                            break;

                        case "DEPOSIT":
                            if (accounts.containsKey(userId)) {
                                try {
                                    double amount = Double.parseDouble(arg);
                                    UserAccount acc = accounts.get(userId);
                                    acc.balance += amount;
                                    acc.addTransaction("DEPOSIT: +" + amount + ", Balance: " + acc.balance);

                                    reply.setPerformative(ACLMessage.INFORM);
                                    reply.setContent("Deposited " + amount + ". Balance: " + acc.balance);

                                    // Notify GUI - sends UPDATE message
                                    notifyGUI(userId, acc.balance, "DEPOSIT:" + amount);

                                } catch (NumberFormatException e) {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Invalid amount");
                                }
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Account not found.");
                            }
                            break;

                        case "WITHDRAW":
                            if (accounts.containsKey(userId)) {
                                try {
                                    double amount = Double.parseDouble(arg);
                                    UserAccount acc = accounts.get(userId);
                                    if (acc.balance >= amount) {
                                        acc.balance -= amount;
                                        acc.addTransaction("WITHDRAW: -" + amount + ", Balance: " + acc.balance);

                                        reply.setPerformative(ACLMessage.INFORM);
                                        reply.setContent("Withdrew " + amount + ". Balance: " + acc.balance);

                                        // Notify GUI - sends UPDATE message
                                        notifyGUI(userId, acc.balance, "WITHDRAW:" + amount);

                                    } else {
                                        reply.setPerformative(ACLMessage.FAILURE);
                                        reply.setContent("Insufficient funds. Balance: " + acc.balance);
                                    }
                                } catch (NumberFormatException e) {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Invalid amount");
                                }
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Account not found.");
                            }
                            break;

                        case "BALANCE":
                            if (accounts.containsKey(userId)) {
                                UserAccount acc = accounts.get(userId);
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("Balance: " + acc.balance);
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Account not found.");
                            }
                            break;

                        case "VALIDATE_PIN":
                            if (parts.length < 3) {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("INVALID");
                            } else {
                                String pinToCheck = parts[2];
                                UserAccount acc = accounts.get(userId);
                                if (acc != null && acc.pin.equals(pinToCheck)) {
                                    reply.setPerformative(ACLMessage.INFORM);
                                    reply.setContent("VALID");
                                } else {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("INVALID");
                                }
                            }
                            break;

                        case "ACCOUNT_EXISTS":
                            if (accounts.containsKey(userId)) {
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("YES");
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("NO");
                            }
                            break;

                        case "TRANSACTION_HISTORY":
                            if (accounts.containsKey(userId)) {
                                UserAccount acc = accounts.get(userId);
                                StringBuilder history = new StringBuilder("Transaction History for " + userId + ":\n");
                                for (String t : acc.transactionHistory) {
                                    history.append(t).append("\n");
                                }
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent(history.toString());
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Account not found.");
                            }
                            break;

                        case "GET_ALL_ACCOUNTS":
                            if (accounts.isEmpty()) {
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent("No accounts in the system.");
                            } else {
                                StringBuilder allAccounts = new StringBuilder("All Accounts:\n");
                                for (Map.Entry<String, UserAccount> entry : accounts.entrySet()) {
                                    allAccounts.append("User: ").append(entry.getKey())
                                            .append(", Balance: ").append(entry.getValue().balance)
                                            .append("\n");
                                }
                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent(allAccounts.toString());
                            }
                            break;

                        default:
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            reply.setContent("Unknown command: " + command);
                            break;
                    }

                    send(reply);
                } else {
                    block();
                }
            }
        });
    }

    private void notifyGUI(String userId, double balance, String operation) {
        try {
            jade.core.AID[] guiAgents = DFServiceHelper.searchService(this, "gui-service");
            if (guiAgents.length > 0) {
                // Send proper UPDATE message, NOT "ACK"
                ACLMessage guiMsg = new ACLMessage(ACLMessage.INFORM);
                guiMsg.addReceiver(guiAgents[0]);
                guiMsg.setContent("UPDATE " + userId + " " + balance + " " + operation);
                send(guiMsg);
                System.out.println("[BankAgent] Notified GUI: " + userId + " - " + operation);
            }
        } catch (Exception e) {
            // Silent fail - GUI might not be running
        }
    }

    private void loadAccountsFromFile() {
        try {
            File file = new File(ACCOUNTS_FILE);
            if (file.exists()) {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                accounts = (HashMap<String, UserAccount>) ois.readObject();
                ois.close();
                System.out.println("[BankAgent] Loaded " + accounts.size() + " accounts from file.");
            } else {
                System.out.println("[BankAgent] No accounts file found, starting with empty accounts.");
                accounts = new HashMap<>();
            }
        } catch (Exception e) {
            System.out.println("[BankAgent] Could not load accounts: " + e.getMessage());
            accounts = new HashMap<>();
        }
    }

    private void saveAccountsToFile() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ACCOUNTS_FILE));
            oos.writeObject(accounts);
            oos.close();
            System.out.println("[BankAgent] Saved " + accounts.size() + " accounts to file.");
        } catch (Exception e) {
            System.out.println("[BankAgent] Could not save accounts: " + e.getMessage());
        }
    }

    @Override
    protected void takeDown() {
        saveAccountsToFile();
        DFServiceHelper.deregister(this);
        System.out.println("[BankAgent] " + getAID().getName() + " terminating.");
    }
}