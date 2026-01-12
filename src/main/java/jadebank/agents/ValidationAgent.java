package jadebank.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jadebank.utils.DFServiceHelper;

public class ValidationAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("[ValidationAgent] started: " + getLocalName());

        // Register with DF
        DFServiceHelper.registerService(this, "validation-service", "pin-validation");

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Check if it's a shutdown message
                    if (msg.getContent().equals("SHUTDOWN")) {
                        System.out.println("[ValidationAgent] Received shutdown command");
                        doDelete(); // Terminate agent
                        return;
                    }

                    ACLMessage reply = msg.createReply();
                    String[] parts = msg.getContent().split(" ");
                    if (parts.length < 2) {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("INVALID");
                        send(reply);
                        return;
                    }

                    String userId = parts[0];
                    String pin = parts[1];

                    try {
                        // Find BankAgent using DF
                        AID[] banks = DFServiceHelper.searchService(ValidationAgent.this, "banking-service");
                        if (banks.length == 0) {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("INVALID - Banking service unavailable");
                            send(reply);
                            return;
                        }

                        ACLMessage bankMsg = new ACLMessage(ACLMessage.REQUEST);
                        bankMsg.addReceiver(banks[0]); // Use first found bank
                        bankMsg.setContent("VALIDATE_PIN " + userId + " " + pin);
                        bankMsg.setConversationId(msg.getConversationId()); // pass conversation ID
                        send(bankMsg);

                        MessageTemplate mt = MessageTemplate.MatchConversationId(msg.getConversationId());
                        ACLMessage bankReply = blockingReceive(mt);

                        if (bankReply != null && bankReply.getPerformative() == ACLMessage.INFORM &&
                                bankReply.getContent().equals("VALID")) {
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent("VALID");
                        } else {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("INVALID");
                        }

                        send(reply);

                    } catch (Exception e) {
                        reply.setPerformative(ACLMessage.FAILURE);
                        reply.setContent("Error validating PIN: " + e.getMessage());
                        send(reply);
                    }
                } else {
                    block();
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        DFServiceHelper.deregister(this);
        System.out.println("[ValidationAgent] " + getAID().getName() + " terminating.");
    }
}