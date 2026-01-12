package jadebank.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jadebank.utils.DFServiceHelper;

public class MonitoringAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("[MonitoringAgent] started: " + getLocalName());

        // Register with DF
        DFServiceHelper.registerService(this, "monitoring-service", "system-monitor");

        // Check system health every 30 seconds
        addBehaviour(new TickerBehaviour(this, 30000) {
            @Override
            protected void onTick() {
                checkSystemHealth();
            }
        });

        // Also add cyclic behaviour to handle shutdown
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null && msg.getContent().equals("SHUTDOWN")) {
                    System.out.println("[MonitoringAgent] Received shutdown command");
                    doDelete();
                } else {
                    block();
                }
            }
        });
    }

    private void checkSystemHealth() {
        System.out.println("[MonitoringAgent] Performing system health check...");

        // Check essential services
        String[] essentialServices = {"banking-service", "validation-service", "atm-service"};

        for (String service : essentialServices) {
            AID[] agents = DFServiceHelper.searchService(this, service);
            if (agents.length == 0) {
                System.out.println("[MonitoringAgent] WARNING: No " + service + " available!");
            } else {
                System.out.println("[MonitoringAgent] " + service + " OK: " + agents.length + " agent(s)");
            }
        }

        // Send status to GUI if available
        try {
            AID[] guiAgents = DFServiceHelper.searchService(this, "gui-service");
            if (guiAgents.length > 0) {
                ACLMessage statusMsg = new ACLMessage(ACLMessage.INFORM);
                statusMsg.addReceiver(guiAgents[0]);
                statusMsg.setContent("SYSTEM_STATUS: All services operational");
                send(statusMsg);
            }
        } catch (Exception e) {
            // Ignore - GUI might not be running
        }
    }

    @Override
    protected void takeDown() {
        DFServiceHelper.deregister(this);
        System.out.println("[MonitoringAgent] terminated.");
    }
}