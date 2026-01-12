package jadebank.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jadebank.utils.DFServiceHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

public class BankGUIAgent extends Agent {

    private JFrame frame;
    private JTextArea logArea;
    private JTextArea accountsArea;
    private HashMap<String, Double> accounts = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("[BankGUIAgent] started: " + getLocalName());

        // Create GUI
        createGUI();

        // Register with DF
        DFServiceHelper.registerService(this, "gui-service", "bank-monitor");

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String content = msg.getContent();

                    // Check if it's a shutdown message
                    if (content.equals("SHUTDOWN")) {
                        System.out.println("[BankGUIAgent] Received shutdown command");
                        SwingUtilities.invokeLater(() -> {
                            if (frame != null) {
                                frame.dispose();
                            }
                        });
                        doDelete(); // Terminate agent
                        return;
                    }

                    // CRITICAL FIX: Filter out "ACK" messages
                    if (content.equals("ACK") || content.trim().isEmpty()) {
                        return; // Ignore completely - no logging, no processing
                    }

                    String senderName = msg.getSender().getLocalName();

                    logMessage("Received from " + senderName + ": " + content);

                    // Process UPDATE messages from BankAgent
                    if (content.startsWith("UPDATE ")) {
                        String[] parts = content.split(" ", 4);
                        if (parts.length >= 4) {
                            String userId = parts[1];
                            try {
                                double balance = Double.parseDouble(parts[2]);
                                String operation = parts[3];

                                updateAccount(userId, balance);
                                logMessage(userId + ": " + operation + " -> Balance: $" + String.format("%.2f", balance));

                            } catch (NumberFormatException e) {
                                logMessage("Error parsing balance for " + userId);
                            }
                        }
                    }
                    // Process system status messages
                    else if (content.contains("SYSTEM_STATUS")) {
                        logMessage("System Status: " + content);
                    }
                    // Ignore other messages
                } else {
                    block();
                }
            }
        });
    }

    private void createGUI() {
        frame = new JFrame("Bank Monitoring System - " + getLocalName());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(700, 500);

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Title
        JLabel titleLabel = new JLabel("Bank System Monitor", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Tabbed pane for different views
        JTabbedPane tabbedPane = new JTabbedPane();

        // Log Panel
        JPanel logPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logPanel.add(new JLabel("System Log:"), BorderLayout.NORTH);
        logPanel.add(logScrollPane, BorderLayout.CENTER);

        // Accounts Panel
        JPanel accountsPanel = new JPanel(new BorderLayout());
        accountsArea = new JTextArea();
        accountsArea.setEditable(false);
        accountsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane accountsScrollPane = new JScrollPane(accountsArea);
        accountsPanel.add(new JLabel("Active Accounts:"), BorderLayout.NORTH);
        accountsPanel.add(accountsScrollPane, BorderLayout.CENTER);

        tabbedPane.addTab("System Log", logPanel);
        tabbedPane.addTab("Accounts", accountsPanel);

        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JButton refreshBtn = new JButton("Refresh Accounts");
        JButton clearLogBtn = new JButton("Clear Log");
        JButton checkSystemBtn = new JButton("Check System Status");
        JButton exportBtn = new JButton("Export Data");

        refreshBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshAccountsDisplay();
            }
        });

        clearLogBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logArea.setText("");
                logMessage("Log cleared");
            }
        });

        checkSystemBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkSystemStatus();
            }
        });

        exportBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportData();
            }
        });

        controlPanel.add(refreshBtn);
        controlPanel.add(clearLogBtn);
        controlPanel.add(checkSystemBtn);
        controlPanel.add(exportBtn);

        // Status bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        JLabel statusLabel = new JLabel("Ready - Monitoring Bank System");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        JLabel countLabel = new JLabel("Accounts: 0");
        countLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusPanel.add(countLabel, BorderLayout.EAST);

        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        logMessage("BankGUI Agent started successfully!");
        logMessage("Agent Name: " + getLocalName());
        logMessage("AID: " + getAID().getName());

        // Start a timer to periodically update the display
        Timer timer = new Timer(5000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateStatusBar(countLabel);
            }
        });
        timer.start();
    }

    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); // Auto-scroll
            System.out.println("[BankGUI] " + message);
        });
    }

    public void updateAccount(String userId, double balance) {
        accounts.put(userId, balance);
        refreshAccountsDisplay();
    }

    private void refreshAccountsDisplay() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Total Accounts: ").append(accounts.size()).append("\n\n");
            sb.append(String.format("%-20s %-15s\n", "User ID", "Balance"));
            sb.append(String.format("%-20s %-15s\n", "-------", "-------"));

            for (Map.Entry<String, Double> entry : accounts.entrySet()) {
                sb.append(String.format("%-20s $%-14.2f\n",
                        entry.getKey(), entry.getValue()));
            }

            accountsArea.setText(sb.toString());
        });
    }

    private void updateStatusBar(JLabel countLabel) {
        SwingUtilities.invokeLater(() -> {
            countLabel.setText("Accounts: " + accounts.size());
        });
    }

    private void checkSystemStatus() {
        logMessage("=== Checking System Status ===");

        // Check for essential services using DF
        String[] services = {"banking-service", "validation-service", "atm-service", "monitoring-service"};

        for (String service : services) {
            try {
                jade.core.AID[] agents = DFServiceHelper.searchService(this, service);
                if (agents.length == 0) {
                    logMessage("WARNING: No " + service + " available!");
                } else {
                    logMessage(service + ": " + agents.length + " agent(s) available");
                    for (jade.core.AID agent : agents) {
                        logMessage("  - " + agent.getLocalName());
                    }
                }
            } catch (Exception e) {
                logMessage("Error checking " + service + ": " + e.getMessage());
            }
        }

        logMessage("=== System Status Check Complete ===");
    }

    private void exportData() {
        if (accounts.isEmpty()) {
            logMessage("No data to export");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Account Data");
        fileChooser.setSelectedFile(new java.io.File("bank_accounts_export.txt"));

        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                writer.println("Bank System Export - " + new java.util.Date());
                writer.println("========================================");
                writer.println("Total Accounts: " + accounts.size());
                writer.println();
                writer.println(String.format("%-20s %-15s", "User ID", "Balance"));
                writer.println(String.format("%-20s %-15s", "-------", "-------"));

                for (Map.Entry<String, Double> entry : accounts.entrySet()) {
                    writer.println(String.format("%-20s $%-14.2f",
                            entry.getKey(), entry.getValue()));
                }

                logMessage("Data exported successfully to: " + file.getAbsolutePath());
                JOptionPane.showMessageDialog(frame,
                        "Data exported successfully!\n" + file.getAbsolutePath(),
                        "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception e) {
                logMessage("Export failed: " + e.getMessage());
                JOptionPane.showMessageDialog(frame,
                        "Export failed: " + e.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    protected void takeDown() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
        DFServiceHelper.deregister(this);
        System.out.println("[BankGUIAgent] terminated.");
    }
}