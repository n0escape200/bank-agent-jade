package jadebank;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class Main {
    public static void main(String[] args) {
        try {
            Runtime rt = Runtime.instance();

            // === CREATE MAIN CONTAINER ===
            Profile pMain = new ProfileImpl();
            pMain.setParameter(Profile.GUI, "true");
            pMain.setParameter(Profile.CONTAINER_NAME, "Main-Container");
            ContainerController mainContainer = rt.createMainContainer(pMain);

            System.out.println("=== MAIN CONTAINER CREATED ===");

            // === CREATE BANK AGENTS CONTAINER ===
            Profile pBank = new ProfileImpl();
            pBank.setParameter(Profile.MAIN_HOST, "localhost");
            pBank.setParameter(Profile.MAIN_PORT, "1099");
            pBank.setParameter(Profile.CONTAINER_NAME, "Bank-Agents-Container");
            pBank.setParameter(Profile.PLATFORM_ID, "JADE");

            ContainerController bankContainer = rt.createAgentContainer(pBank);
            System.out.println("=== BANK AGENTS CONTAINER CREATED ===");

            // === START SNIFFER IN MAIN CONTAINER (BEFORE BANK AGENTS) ===
            try {
                AgentController sniffer = mainContainer.createNewAgent("sniffer",
                        "jade.tools.sniffer.Sniffer", null);
                sniffer.start();
                System.out.println("✅ Sniffer agent started in Main Container");
                System.out.println("   Use it to monitor all message flows");
            } catch (Exception e) {
                System.out.println("Note: Could not start sniffer - " + e.getMessage());
            }

            // === START ALL BANK AGENTS IN BANK CONTAINER ===
            System.out.println("\n=== STARTING BANK AGENTS ===");

            // Start BankAgent
            AgentController bank = bankContainer.createNewAgent("bank",
                    "jadebank.agents.BankAgent", null);
            bank.start();
            Thread.sleep(500);

            // Start ValidationAgent
            AgentController validation = bankContainer.createNewAgent("validation",
                    "jadebank.agents.ValidationAgent", null);
            validation.start();
            Thread.sleep(500);

            // Start ATMs
            AgentController atm1 = bankContainer.createNewAgent("atm1",
                    "jadebank.agents.ATMAgent", null);
            atm1.start();

            AgentController atm2 = bankContainer.createNewAgent("atm2",
                    "jadebank.agents.ATMAgent", null);
            atm2.start();
            Thread.sleep(500);

            // === START GUI & CONTROL AGENTS IN MAIN CONTAINER ===
            System.out.println("\n=== STARTING GUI & CONTROL AGENTS ===");

            // Start ControllerAgent
            AgentController controller = mainContainer.createNewAgent("Controller",
                    "jadebank.agents.ControllerAgent", null);
            controller.start();
            Thread.sleep(500);

            // Start GUI Agent (monitoring)
            AgentController gui = mainContainer.createNewAgent("bank-gui",
                    "jadebank.agents.BankGUIAgent", null);
            gui.start();
            Thread.sleep(500);

            // Start Monitoring Agent
            AgentController monitor = mainContainer.createNewAgent("monitor",
                    "jadebank.agents.MonitoringAgent", null);
            monitor.start();
            Thread.sleep(500);

            // Start Shutdown Agent
            AgentController shutdown = mainContainer.createNewAgent("shutdown-controller",
                    "jadebank.agents.ShutdownAgent", null);
            shutdown.start();
            Thread.sleep(500);

            // Start Command GUI Agent
            AgentController commandGui = mainContainer.createNewAgent("command-gui",
                    "jadebank.agents.CommandGUIAgent", null);
            commandGui.start();

            System.out.println("\n" + "=".repeat(50));
            System.out.println("=== SYSTEM READY ===");
            System.out.println("=".repeat(50));
            System.out.println("\nContainers:");
            System.out.println("1. Main Container:");
            System.out.println("   - sniffer (for monitoring)");
            System.out.println("   - Controller (command router)");
            System.out.println("   - bank-gui (monitoring GUI)");
            System.out.println("   - monitor (system health)");
            System.out.println("   - shutdown-controller (control panel)");
            System.out.println("   - command-gui (command console GUI)");
            System.out.println("\n2. Bank Agents Container:");
            System.out.println("   - bank (account management)");
            System.out.println("   - validation (PIN validation)");
            System.out.println("   - atm1, atm2 (ATM agents)");

            System.out.println("\n" + "=".repeat(50));
            System.out.println("HOW TO USE:");
            System.out.println("1. Look for 'Bank Command Console' GUI - for commands");
            System.out.println("2. Look for 'Bank System Monitor' GUI - for monitoring");
            System.out.println("3. Look for 'Bank System Control Panel' GUI - for shutdown");
            System.out.println("4. Look for 'JADE Sniffer' window - for protocol capture");
            System.out.println("\nExample commands in Command Console:");
            System.out.println("  atm1 alice 1234 OPEN_ACCOUNT");
            System.out.println("  atm1 alice 1234 DEPOSIT 1000");
            System.out.println("  atm1 alice 1234 BALANCE");
            System.out.println("=".repeat(50));

        } catch (Exception e) {
            System.err.println("Error starting system: " + e.getMessage());
            e.printStackTrace();
        }
    }
}