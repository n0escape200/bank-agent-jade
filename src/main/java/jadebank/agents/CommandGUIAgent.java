package jadebank.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jadebank.utils.DFServiceHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class CommandGUIAgent extends Agent {

    private JFrame frame;
    private JTextArea outputArea;
    private JTextField commandField;
    private DefaultListModel<String> atmListModel;

    @Override
    protected void setup() {
        System.out.println("[CommandGUI] started");

        // Register with DF
        DFServiceHelper.registerService(this, "command-gui-service", "command-interface-gui");

        createGUI();

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Check if it's a shutdown message
                    if (msg.getContent().equals("SHUTDOWN")) {
                        System.out.println("[CommandGUI] Received shutdown command");
                        SwingUtilities.invokeLater(() -> {
                            if (frame != null) {
                                frame.dispose();
                            }
                        });
                        doDelete(); // Terminate agent
                        return;
                    }

                    // Display replies
                    final String content = msg.getContent();
                    final String sender = msg.getSender().getLocalName();
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append("[" + sender + "] " + content + "\n");
                        outputArea.setCaretPosition(outputArea.getDocument().getLength()); // Auto-scroll
                    });
                } else {
                    block();
                }
            }
        });

        // Update ATM list periodically
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                updateATMList();
                block(10000); // Update every 10 seconds
            }
        });
    }

    private void createGUI() {
        frame = new JFrame("Bank Command Console");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(700, 500);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Title
        JLabel titleLabel = new JLabel("Bank Command Console", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Split pane for output and ATM list
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // Left: Output area
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setBackground(new Color(240, 240, 240));
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputPanel.add(new JLabel("Command Output:"), BorderLayout.NORTH);
        outputPanel.add(outputScrollPane, BorderLayout.CENTER);

        // Right: ATM list and examples
        JPanel sidePanel = new JPanel(new BorderLayout());

        // ATM List
        JPanel atmPanel = new JPanel(new BorderLayout());
        atmListModel = new DefaultListModel<>();
        JList<String> atmList = new JList<>(atmListModel);
        atmList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane atmScrollPane = new JScrollPane(atmList);
        atmPanel.add(new JLabel("Available ATMs:"), BorderLayout.NORTH);
        atmPanel.add(atmScrollPane, BorderLayout.CENTER);

        // Examples panel
        JPanel examplesPanel = new JPanel(new BorderLayout());
        JTextArea examplesArea = new JTextArea();
        examplesArea.setEditable(false);
        examplesArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        examplesArea.setBackground(new Color(220, 240, 220));
        examplesArea.setText(
                "Command Format:\n" +
                        "ATM USER_ID PIN COMMAND [AMOUNT]\n\n" +
                        "Examples:\n" +
                        "atm1 alice 1234 OPEN_ACCOUNT\n" +
                        "atm1 alice 1234 DEPOSIT 1000\n" +
                        "atm1 alice 1234 BALANCE\n" +
                        "atm1 alice 1234 WITHDRAW 500\n" +
                        "atm1 alice 1234 TRANSACTION_HISTORY\n" +
                        "atm2 bob 5678 OPEN_ACCOUNT\n\n" +
                        "Special Commands:\n" +
                        "help - Show help\n" +
                        "list - List ATMs\n" +
                        "clear - Clear output"
        );
        JScrollPane examplesScrollPane = new JScrollPane(examplesArea);
        examplesPanel.add(new JLabel("Examples:"), BorderLayout.NORTH);
        examplesPanel.add(examplesScrollPane, BorderLayout.CENTER);

        // Add refresh button for ATMs
        JPanel atmButtonPanel = new JPanel(new FlowLayout());
        JButton refreshAtmButton = new JButton("Refresh ATM List");
        refreshAtmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateATMList();
            }
        });
        atmButtonPanel.add(refreshAtmButton);
        atmPanel.add(atmButtonPanel, BorderLayout.SOUTH);

        sidePanel.add(atmPanel, BorderLayout.NORTH);
        sidePanel.add(examplesPanel, BorderLayout.CENTER);

        splitPane.setLeftComponent(outputPanel);
        splitPane.setRightComponent(sidePanel);
        splitPane.setDividerLocation(500);

        // Input panel at bottom
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        commandField = new JTextField();
        commandField.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JButton sendButton = new JButton("Send");
        sendButton.setBackground(new Color(70, 130, 180));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Arial", Font.BOLD, 12));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 5, 5));
        JButton clearButton = new JButton("Clear");
        JButton helpButton = new JButton("Help");
        JButton listButton = new JButton("List ATMs");
        JButton exitButton = new JButton("Exit");

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                outputArea.setText("");
                outputArea.append("=== Bank Command Console ===\n");
                outputArea.append("Type commands or click 'Help' for examples\n\n");
            }
        });

        helpButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });

        listButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateATMList();
                outputArea.append("[System] Refreshed ATM list\n");
            }
        });

        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                shutdown();
            }
        });

        buttonPanel.add(clearButton);
        buttonPanel.add(helpButton);
        buttonPanel.add(listButton);
        buttonPanel.add(exitButton);

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendCommand();
            }
        });

        commandField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendCommand();
            }
        });

        inputPanel.add(new JLabel("Command: "), BorderLayout.WEST);
        inputPanel.add(commandField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        outputArea.append("=== Bank Command Console ===\n");
        outputArea.append("System started successfully!\n");
        outputArea.append("Type commands or click 'Help' for examples\n\n");

        // Initial ATM list update
        updateATMList();
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) return;

        outputArea.append("> " + command + "\n");
        commandField.setText("");

        // Handle special commands locally
        if (command.equalsIgnoreCase("help")) {
            showHelpDialog();
            return;
        }

        if (command.equalsIgnoreCase("clear")) {
            outputArea.setText("");
            outputArea.append("=== Bank Command Console ===\n");
            outputArea.append("Type commands or click 'Help' for examples\n\n");
            return;
        }

        if (command.equalsIgnoreCase("list")) {
            updateATMList();
            outputArea.append("[System] ATM list refreshed\n");
            return;
        }

        if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
            shutdown();
            return;
        }

        // Send banking commands to ControllerAgent
        try {
            // Find ControllerAgent
            AID[] controllers = DFServiceHelper.searchService(this, "console-service");

            if (controllers.length == 0) {
                // If not found via DF, try direct
                outputArea.append("[Error] Controller agent not found!\n");
                return;
            }

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(controllers[0]);
            msg.setContent(command);
            send(msg);

            outputArea.append("[System] Command sent to Controller\n");

        } catch (Exception e) {
            outputArea.append("[Error] " + e.getMessage() + "\n");
        }
    }

    private void updateATMList() {
        try {
            AID[] atms = DFServiceHelper.searchService(this, "atm-service");
            SwingUtilities.invokeLater(() -> {
                atmListModel.clear();
                if (atms.length == 0) {
                    atmListModel.addElement("No ATMs available");
                } else {
                    for (AID atm : atms) {
                        atmListModel.addElement(atm.getLocalName());
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[CommandGUI] Error updating ATM list: " + e.getMessage());
        }
    }

    private void showHelpDialog() {
        String helpText =
                "=== Banking System Help ===\n\n" +
                        "Command Format:\n" +
                        "  ATM_NAME USER_ID PIN COMMAND [AMOUNT]\n\n" +
                        "Available ATMs:\n" +
                        "  atm1, atm2 (check 'List ATMs' for current list)\n\n" +
                        "Banking Commands:\n" +
                        "  OPEN_ACCOUNT - Create new account\n" +
                        "  DEPOSIT amount - Deposit money\n" +
                        "  WITHDRAW amount - Withdraw money\n" +
                        "  BALANCE - Check balance\n" +
                        "  TRANSACTION_HISTORY - View transaction history\n\n" +
                        "System Commands:\n" +
                        "  help - Show this help\n" +
                        "  list - List available ATMs\n" +
                        "  clear - Clear output window\n" +
                        "  exit/quit - Exit application\n\n" +
                        "Examples:\n" +
                        "  atm1 alice 1234 OPEN_ACCOUNT\n" +
                        "  atm1 alice 1234 DEPOSIT 1000\n" +
                        "  atm1 alice 1234 BALANCE\n" +
                        "  atm1 alice 1234 WITHDRAW 500\n" +
                        "  atm2 bob 5678 OPEN_ACCOUNT\n\n" +
                        "Note: Use the Shutdown Controller GUI to stop all agents properly.";

        JTextArea helpArea = new JTextArea(helpText);
        helpArea.setEditable(false);
        helpArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(helpArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));

        JOptionPane.showMessageDialog(frame, scrollPane, "Help - Command Reference",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void shutdown() {
        int response = JOptionPane.showConfirmDialog(frame,
                "Do you want to shutdown the Command Console?\n" +
                        "Note: Use Shutdown Controller GUI to stop all agents.",
                "Confirm Shutdown",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
            SwingUtilities.invokeLater(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
            doDelete();
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
        System.out.println("[CommandGUI] terminated.");
    }
}