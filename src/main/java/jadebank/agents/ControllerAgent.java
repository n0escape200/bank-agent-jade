package jadebank.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jadebank.utils.DFServiceHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ControllerAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("[Controller] started - Ready for commands");

        // Register with DF
        DFServiceHelper.registerService(this, "console-service", "command-interface");

        addBehaviour(new CyclicBehaviour(this) {
            private BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            @Override
            public void action() {
                // Check for GUI messages first
                ACLMessage msg = receive();
                if (msg != null) {
                    System.out.println("[Controller DEBUG] Received message:");
                    System.out.println("  From: " + msg.getSender().getLocalName());
                    System.out.println("  Content: " + msg.getContent());
                    System.out.println("  ConvID: " + msg.getConversationId());

                    // Handle shutdown
                    if (msg.getContent().equals("SHUTDOWN")) {
                        System.out.println("[Controller] Shutting down");
                        doDelete();
                        return;
                    }

                    // If from GUI, process command
                    String sender = msg.getSender().getLocalName();
                    if (sender.equals("command-gui")) {
                        System.out.println("[Controller] Processing GUI command: " + msg.getContent());
                        processCommand(msg.getContent(), "command-gui");
                    }
                    // If from ATM, it's a reply - just print it
                    else if (sender.equals("atm1") || sender.equals("atm2")) {
                        System.out.println("[Controller] ATM reply: " + msg.getContent());
                        // Forward to GUI if needed
                        forwardToGUI("command-gui", msg.getContent());
                    }
                }

                // Check console input
                try {
                    if (reader.ready()) {
                        String line = reader.readLine().trim();
                        if (!line.isEmpty()) {
                            System.out.println("[Controller] Console command: " + line);
                            processCommand(line, "console");
                        }
                    } else {
                        block(500);
                    }
                } catch (Exception e) {
                    System.out.println("[Controller] Input error: " + e.getMessage());
                    block(1000);
                }
            }

            private void processCommand(String command, String sender) {
                // Special commands
                if (command.equalsIgnoreCase("help")) {
                    sendResponse(sender, getHelpText());
                    return;
                }

                if (command.equalsIgnoreCase("list")) {
                    listATMs(sender);
                    return;
                }

                if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
                    sendResponse(sender, "[Controller] Exiting...");
                    doDelete();
                    return;
                }

                // Parse banking command
                String[] parts = command.split(" ", 4);
                if (parts.length < 3) {
                    sendResponse(sender, "ERROR: Invalid format. Use: ATM USERID PIN COMMAND");
                    return;
                }

                String atmName = parts[0];
                String userId = parts[1];
                String pin = parts[2];
                String cmd = (parts.length == 4 ? parts[3] : "");

                System.out.println("[Controller] Sending to " + atmName + ": " + userId + " " + pin + " " + cmd);

                // Send to ATM
                try {
                    ACLMessage atmMsg = new ACLMessage(ACLMessage.REQUEST);
                    atmMsg.addReceiver(new AID(atmName, AID.ISLOCALNAME));
                    atmMsg.setContent(userId + " " + pin + " " + cmd);
                    // Include conversation ID for tracking
                    String convId = "ctrl-" + System.currentTimeMillis();
                    atmMsg.setConversationId(convId);

                    send(atmMsg);
                    sendResponse(sender, "[Controller] Command sent to " + atmName);
                    System.out.println("[Controller] Sent with ConvID: " + convId);

                } catch (Exception e) {
                    sendResponse(sender, "[Controller] Error: " + e.getMessage());
                }
            }

            private void sendResponse(String receiver, String message) {
                if (receiver.equals("console")) {
                    System.out.println(message);
                } else {
                    try {
                        ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                        response.addReceiver(new AID(receiver, AID.ISLOCALNAME));
                        response.setContent(message);
                        send(response);
                    } catch (Exception e) {
                        System.out.println("[Controller] Error sending to " + receiver + ": " + e.getMessage());
                    }
                }
            }

            private void forwardToGUI(String guiName, String message) {
                try {
                    ACLMessage guiMsg = new ACLMessage(ACLMessage.INFORM);
                    guiMsg.addReceiver(new AID(guiName, AID.ISLOCALNAME));
                    guiMsg.setContent(message);
                    send(guiMsg);
                } catch (Exception e) {
                    System.out.println("[Controller] Error forwarding to GUI: " + e.getMessage());
                }
            }

            private void listATMs(String sender) {
                try {
                    AID[] atms = DFServiceHelper.searchService(ControllerAgent.this, "atm-service");
                    if (atms.length == 0) {
                        sendResponse(sender, "No ATMs available");
                    } else {
                        StringBuilder sb = new StringBuilder("Available ATMs:\n");
                        for (AID atm : atms) {
                            sb.append("  - ").append(atm.getLocalName()).append("\n");
                        }
                        sendResponse(sender, sb.toString());
                    }
                } catch (Exception e) {
                    sendResponse(sender, "Error listing ATMs: " + e.getMessage());
                }
            }

            private String getHelpText() {
                return
                        "=== Banking System ===\n" +
                                "Format: ATM_NAME USER_ID PIN COMMAND [AMOUNT]\n" +
                                "Examples:\n" +
                                "  atm1 alice 1234 OPEN_ACCOUNT\n" +
                                "  atm1 alice 1234 DEPOSIT 1000\n" +
                                "  atm1 alice 1234 BALANCE\n" +
                                "  atm1 alice 1234 WITHDRAW 500\n" +
                                "  atm1 alice 1234 TRANSACTION_HISTORY\n" +
                                "Commands: help, list, exit";
            }
        });
    }

    @Override
    protected void takeDown() {
        DFServiceHelper.deregister(this);
        System.out.println("[Controller] terminated.");
    }
}