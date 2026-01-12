package jadebank;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class SnifferStarter {
    public static void main(String[] args) {
        try {
            // First, wait a bit to ensure main platform is running
            System.out.println("Waiting for main platform to be ready...");
            Thread.sleep(3000);

            Runtime rt = Runtime.instance();

            // DO NOT create a new main container - CONNECT to existing one
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");
            p.setParameter(Profile.PLATFORM_ID, "JADE");

            // Create AGENT container that joins existing platform
            ContainerController cc = rt.createAgentContainer(p);

            System.out.println("Connected to existing JADE platform");

            // Create Sniffer with parameters for classic interface
            // The parameters array forces the classic GUI
            Object[] snifferArgs = new Object[3];
            snifferArgs[0] = null;  // agent name (null for default)
            snifferArgs[1] = null;  // icon (null for default)
            snifferArgs[2] = Boolean.FALSE; // showOnlyAgent flag - FALSE for classic interface

            AgentController sniffer = cc.createNewAgent(
                    "classic-sniffer",  // Different name to avoid conflicts
                    "jade.tools.sniffer.Sniffer",
                    snifferArgs
            );

            sniffer.start();

            System.out.println("Classic Sniffer agent started!");
            System.out.println("You should see the full Sniffer GUI with menus:");
            System.out.println("- File, Sniffer, Edit, View menus");
            System.out.println("- Toolbar with buttons");
            System.out.println("- Left panel with agent list");
            System.out.println("\nTo use it:");
            System.out.println("1. Select agents in left panel");
            System.out.println("2. Press F5 or go to Sniffer -> Sniff on");
            System.out.println("3. Perform banking operations");
            System.out.println("4. Watch message arrows appear");

        } catch (Exception e) {
            System.err.println("Error starting sniffer: " + e.getMessage());
            System.out.println("\nTroubleshooting:");
            System.out.println("1. Make sure Main.java is running FIRST");
            System.out.println("2. Wait at least 10 seconds after starting Main.java");
            System.out.println("3. Check the main output - should show agents starting");

            if (e.getMessage().contains("1099") || e.getMessage().contains("port")) {
                System.out.println("\n⚠️  Port 1099 might be in use.");
                System.out.println("   Make sure only ONE Main.java is running.");
            }
        }
    }
}