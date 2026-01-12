package jadebank.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jadebank.utils.DFServiceHelper;

public class ATMAgent extends Agent {

    private String atmName;

    @Override
    protected void setup() {
        atmName = getLocalName();
        System.out.println("[" + atmName + "] started - Ready to process commands");

        // Register with DF
        DFServiceHelper.registerService(this, "atm-service", "atm-" + atmName);

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Check for shutdown
                    if (msg.getContent().equals("SHUTDOWN")) {
                        System.out.println("[" + atmName + "] Shutting down");
                        doDelete();
                        return;
                    }

                    System.out.println("[" + atmName + "] Received: " + msg.getContent() + " from " + msg.getSender().getLocalName());

                    // Store sender and conversation ID
                    AID sender = msg.getSender();
                    String convId = msg.getConversationId();

                    try {
                        // Parse the message: "userId pin command"
                        String[] parts = msg.getContent().split(" ", 3);
                        if (parts.length < 3) {
                            sendError(sender, convId, "Invalid format. Expected: USER_ID PIN COMMAND");
                            return;
                        }

                        String userId = parts[0];
                        String pin = parts[1];
                        String commandLine = parts[2];

                        // Extract command and argument
                        String[] cmdParts = commandLine.split(" ", 2);
                        String cmd = cmdParts[0].toUpperCase();
                        String arg = cmdParts.length > 1 ? cmdParts[1] : "";

                        System.out.println("[" + atmName + "] Processing: cmd=" + cmd + ", userId=" + userId + ", arg=" + arg);

                        // Special handling for OPEN_ACCOUNT
                        if (cmd.equals("OPEN_ACCOUNT")) {
                            // For OPEN_ACCOUNT, the PIN is the argument to the bank
                            arg = pin;
                            processBankCommand(cmd, userId, arg, sender, convId);
                        }
                        // For other commands, validate PIN first
                        else {
                            validatePIN(userId, pin, cmd, arg, sender, convId);
                        }

                    } catch (Exception e) {
                        System.out.println("[" + atmName + "] Error parsing message: " + e.getMessage());
                        sendError(sender, convId, "Error: " + e.getMessage());
                    }
                } else {
                    block();
                }
            }

            private void validatePIN(String userId, String pin, String cmd, String arg,
                                     AID originalSender, String originalConvId) {
                try {
                    // Find ValidationAgent
                    AID[] validators = DFServiceHelper.searchService(ATMAgent.this, "validation-service");
                    if (validators.length == 0) {
                        sendError(originalSender, originalConvId, "Validation service not available");
                        return;
                    }

                    // Send validation request
                    ACLMessage valMsg = new ACLMessage(ACLMessage.REQUEST);
                    valMsg.addReceiver(validators[0]);
                    valMsg.setContent(userId + " " + pin);
                    String valConvId = "val-" + System.currentTimeMillis();
                    valMsg.setConversationId(valConvId);
                    send(valMsg);

                    // Wait for response
                    MessageTemplate mt = MessageTemplate.MatchConversationId(valConvId);
                    ACLMessage valReply = blockingReceive(mt, 5000);

                    if (valReply != null && valReply.getPerformative() == ACLMessage.INFORM
                            && valReply.getContent().equals("VALID")) {
                        // PIN valid, process bank command
                        processBankCommand(cmd, userId, arg, originalSender, originalConvId);
                    } else {
                        // PIN invalid
                        ACLMessage reply = new ACLMessage(ACLMessage.FAILURE);
                        reply.addReceiver(originalSender);
                        reply.setContent("[" + atmName + "] Invalid PIN for " + userId);
                        if (originalConvId != null) reply.setConversationId(originalConvId);
                        send(reply);
                    }

                } catch (Exception e) {
                    sendError(originalSender, originalConvId, "Validation error: " + e.getMessage());
                }
            }

            private void processBankCommand(String cmd, String userId, String arg,
                                            AID originalSender, String originalConvId) {
                try {
                    // Find BankAgent
                    AID[] banks = DFServiceHelper.searchService(ATMAgent.this, "banking-service");
                    if (banks.length == 0) {
                        sendError(originalSender, originalConvId, "Banking service not available");
                        return;
                    }

                    // Build bank command
                    String bankCmd = cmd + " " + userId + (arg.isEmpty() ? "" : " " + arg);
                    System.out.println("[" + atmName + "] Sending to bank: " + bankCmd);

                    // Send to bank
                    ACLMessage bankMsg = new ACLMessage(ACLMessage.REQUEST);
                    bankMsg.addReceiver(banks[0]);
                    bankMsg.setContent(bankCmd);
                    String bankConvId = "bank-" + System.currentTimeMillis();
                    bankMsg.setConversationId(bankConvId);
                    send(bankMsg);

                    // Wait for bank reply
                    MessageTemplate mt = MessageTemplate.MatchConversationId(bankConvId);
                    ACLMessage bankReply = blockingReceive(mt, 10000);

                    if (bankReply != null) {
                        // Forward bank reply to original sender
                        ACLMessage reply = new ACLMessage(bankReply.getPerformative());
                        reply.addReceiver(originalSender);
                        reply.setContent("[" + atmName + "] " + bankReply.getContent());
                        if (originalConvId != null) reply.setConversationId(originalConvId);
                        send(reply);
                        System.out.println("[" + atmName + "] Sent reply to " + originalSender.getLocalName());
                    } else {
                        sendError(originalSender, originalConvId, "Bank timeout");
                    }

                } catch (Exception e) {
                    sendError(originalSender, originalConvId, "Bank error: " + e.getMessage());
                }
            }

            private void sendError(AID receiver, String convId, String message) {
                ACLMessage error = new ACLMessage(ACLMessage.FAILURE);
                error.addReceiver(receiver);
                error.setContent("[" + atmName + "] " + message);
                if (convId != null) error.setConversationId(convId);
                send(error);
                System.out.println("[" + atmName + "] Sent error: " + message);
            }
        });
    }

    @Override
    protected void takeDown() {
        DFServiceHelper.deregister(this);
        System.out.println("[" + atmName + "] terminated.");
    }
}