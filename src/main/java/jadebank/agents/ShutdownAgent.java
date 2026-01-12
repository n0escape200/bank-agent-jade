package jadebank.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jadebank.utils.DFServiceHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ShutdownAgent extends Agent {

    private JFrame frame;

    @Override
    protected void setup() {
        System.out.println("[ShutdownAgent] started: " + getLocalName());

        // Wait a bit before registering with DF to ensure DF is ready
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                try {
                    Thread.sleep(3000); // Wait 3 seconds for DF to be ready
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Register with DF
                DFServiceHelper.registerService(this.myAgent, "admin-service", "system-shutdown");

                // Create shutdown GUI
                createShutdownGUI();
            }
        });

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getContent().equals("SHUTDOWN")) {
                        System.out.println("[ShutdownAgent] Received shutdown request from " + msg.getSender());
                        doDelete();
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void createShutdownGUI() {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Bank System Controller");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setSize(350, 200);
            frame.setLocation(100, 100); // Position it

            JPanel panel = new JPanel(new BorderLayout());
            JLabel label = new JLabel("Bank System Control Panel", SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, 16));
            label.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

            JButton shutdownBtn = new JButton("Shutdown All Agents");
            shutdownBtn.setBackground(new Color(220, 60, 60));
            shutdownBtn.setForeground(Color.WHITE);
            shutdownBtn.setFont(new Font("Arial", Font.BOLD, 14));

            shutdownBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int response = JOptionPane.showConfirmDialog(frame,
                            "Are you sure you want to shutdown all agents?",
                            "Confirm Shutdown",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);

                    if (response == JOptionPane.YES_OPTION) {
                        broadcastShutdown();
                        frame.dispose();
                        doDelete();
                    }
                }
            });

            JButton statusBtn = new JButton("Check System Status");
            statusBtn.setBackground(new Color(60, 120, 200));
            statusBtn.setForeground(Color.WHITE);
            statusBtn.setFont(new Font("Arial", Font.BOLD, 14));

            statusBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Run in background thread to keep GUI responsive
                    new Thread(() -> {
                        statusBtn.setEnabled(false);
                        statusBtn.setText("Checking...");
                        checkSystemStatus();
                        statusBtn.setText("Check System Status");
                        statusBtn.setEnabled(true);
                    }).start();
                }
            });

            JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
            buttonPanel.add(statusBtn);
            buttonPanel.add(shutdownBtn);

            panel.add(label, BorderLayout.NORTH);
            panel.add(buttonPanel, BorderLayout.CENTER);

            frame.add(panel);
            frame.setVisible(true);
        });
    }

    private void broadcastShutdown() {
        try {
            System.out.println("[ShutdownAgent] Broadcasting shutdown...");

            // Send shutdown to all known agent types with retries
            String[] serviceTypes = {"atm-service", "banking-service", "validation-service",
                    "gui-service", "console-service", "monitoring-service",
                    "command-gui-service"};

            int totalShutdowns = 0;
            for (String serviceType : serviceTypes) {
                AID[] agents = DFServiceHelper.searchService(this, serviceType, 2); // 2 retries
                for (AID agent : agents) {
                    if (!agent.equals(getAID())) { // Don't send to self
                        ACLMessage shutdownMsg = new ACLMessage(ACLMessage.REQUEST);
                        shutdownMsg.addReceiver(agent);
                        shutdownMsg.setContent("SHUTDOWN");
                        send(shutdownMsg);
                        totalShutdowns++;
                        System.out.println("[ShutdownAgent] Sent shutdown to " + agent.getLocalName());
                    }
                }
            }

            // Also send to Controller (which might not be registered with DF)
            try {
                ACLMessage controllerMsg = new ACLMessage(ACLMessage.REQUEST);
                controllerMsg.addReceiver(new AID("Controller", AID.ISLOCALNAME));
                controllerMsg.setContent("SHUTDOWN");
                send(controllerMsg);
                totalShutdowns++;
                System.out.println("[ShutdownAgent] Sent shutdown to Controller");
            } catch (Exception e) {
                System.out.println("[ShutdownAgent] Could not send to Controller: " + e.getMessage());
            }

            System.out.println("[ShutdownAgent] Total shutdown signals sent: " + totalShutdowns);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkSystemStatus() {
        final StringBuilder status = new StringBuilder("System Status:\n\n");

        // Define services to check
        String[] serviceTypes = {
                "atm-service", "banking-service", "validation-service",
                "gui-service", "console-service", "monitoring-service",
                "command-gui-service", "admin-service"
        };

        int totalAgents = 0;

        for (String serviceType : serviceTypes) {
            // Use retry mechanism
            AID[] agents = DFServiceHelper.searchService(this, serviceType, 2);
            status.append(serviceType).append(": ").append(agents.length).append(" agent(s)\n");
            for (AID agent : agents) {
                status.append("  - ").append(agent.getLocalName()).append("\n");
            }
            totalAgents += agents.length;
        }

        status.append("\nTotal agents: ").append(totalAgents).append("\n");
        status.append("System: ").append(totalAgents > 5 ? "HEALTHY" : "DEGRADED");

        // Show in dialog
        SwingUtilities.invokeLater(() -> {
            JTextArea statusArea = new JTextArea(status.toString());
            statusArea.setEditable(false);
            statusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            JScrollPane scrollPane = new JScrollPane(statusArea);
            scrollPane.setPreferredSize(new Dimension(400, 300));

            JOptionPane.showMessageDialog(frame, scrollPane, "System Status",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        System.out.println("[ShutdownAgent] System status checked: " + totalAgents + " agents");
    }

    @Override
    protected void takeDown() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
        DFServiceHelper.deregister(this);
        System.out.println("[ShutdownAgent] System shutdown completed.");
    }
}