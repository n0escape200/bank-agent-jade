package jadebank.utils;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class DFServiceHelper {

    public static void registerService(Agent agent, String serviceType, String serviceName) {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(agent.getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType(serviceType);
            sd.setName(serviceName);
            sd.setOwnership("JADE-Bank");

            dfd.addServices(sd);

            DFService.register(agent, dfd);
            System.out.println("[" + agent.getLocalName() + "] Registered service: " + serviceType);
        } catch (FIPAException e) {
            System.err.println("[" + agent.getLocalName() + "] Failed to register with DF: " + e.getMessage());
        }
    }

    public static AID[] searchService(Agent agent, String serviceType) {
        return searchService(agent, serviceType, 3); // Default 3 retries
    }

    public static AID[] searchService(Agent agent, String serviceType, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType(serviceType);
                template.addServices(sd);

                DFAgentDescription[] result = DFService.search(agent, template);
                AID[] providers = new AID[result.length];
                for (int i = 0; i < result.length; i++) {
                    providers[i] = result[i].getName();
                }
                return providers;

            } catch (FIPAException e) {
                retryCount++;
                if (retryCount < maxRetries) {
                    System.out.println("[" + agent.getLocalName() + "] DF search retry " + retryCount + "/" + maxRetries + " for: " + serviceType);
                    try {
                        Thread.sleep(1000); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.err.println("[" + agent.getLocalName() + "] Failed to search DF after " + maxRetries + " retries: " + e.getMessage());
                    return new AID[0];
                }
            }
        }
        return new AID[0];
    }

    public static void deregister(Agent agent) {
        try {
            DFService.deregister(agent);
        } catch (FIPAException e) {
            System.err.println("[" + agent.getLocalName() + "] Failed to deregister from DF: " + e.getMessage());
        }
    }
}