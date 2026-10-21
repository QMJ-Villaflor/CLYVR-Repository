package clyvr.view;

import clyvr.controller.AppController;
import clyvr.service.AIService;
import clyvr.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;

public class FloatingChatWidget extends JPanel {

    private AppController app;
    private ToggleRobotButton toggleButton;
    private RoundedPanel chatCard;
    private JTextPane chatPane;
    private StringBuilder chatHtml;
    private JTextField inputField;
    private SendArrowButton sendButton;
    private JPanel faqPanel;
    private boolean isExpanded = false;
    private long lastSendTime = 0;

    public FloatingChatWidget(AppController app) {
        this.app = app;
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        chatCard = createChatCard();
        chatCard.setVisible(false);

        toggleButton = new ToggleRobotButton();
        toggleButton.setToolTipText("CLYVR AI Assistant");
        toggleButton.setPreferredSize(new Dimension(55, 55));

        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonWrapper.setOpaque(false);
        buttonWrapper.add(toggleButton);

        add(chatCard, BorderLayout.CENTER);
        add(buttonWrapper, BorderLayout.SOUTH);

        toggleButton.addActionListener(e -> toggleChatBox());
    }

    public void resetSession() {
        initChatHtml();
        if (faqPanel != null) {
            rebuildFaqChips();
            faqPanel.setVisible(true);
        }
        if (inputField != null) {
            inputField.setText("");
            inputField.setEnabled(true);
        }
        if (sendButton != null) {
            sendButton.setEnabled(true);
        }
    }

    public void toggleChatBox() {
        isExpanded = !isExpanded;
        if (isExpanded) {
            rebuildFaqChips();
            inputField.requestFocusInWindow();
        }
        chatCard.setVisible(isExpanded);
        revalidate();
        repaint();
    }

    private RoundedPanel createChatCard() {
        RoundedPanel card = new RoundedPanel(Theme.HEADER, 24);
        card.setLayout(new BorderLayout(8, 8));
        card.setPreferredSize(new Dimension(340, 430));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerPanel.setOpaque(false);

        JLabel headerTitle = new JLabel("CLYVR AI Assistant");
        headerTitle.setFont(Theme.font(16, true));
        headerTitle.setForeground(Theme.WHITE);

        headerPanel.add(headerTitle);
        card.add(headerPanel, BorderLayout.NORTH);

        RoundedPanel innerWhiteCard = new RoundedPanel(Theme.WHITE, 16);
        innerWhiteCard.setLayout(new BorderLayout());
        innerWhiteCard.setBorder(new EmptyBorder(10, 10, 10, 10));

        chatPane = new JTextPane();
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setOpaque(false);
        chatPane.setBackground(new Color(0, 0, 0, 0));

        chatHtml = new StringBuilder();
        initChatHtml();

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);

        innerWhiteCard.add(scrollPane, BorderLayout.CENTER);

        faqPanel = new JPanel();
        faqPanel.setLayout(new BoxLayout(faqPanel, BoxLayout.Y_AXIS));
        faqPanel.setOpaque(false);
        faqPanel.setBorder(new EmptyBorder(6, 0, 2, 0));

        rebuildFaqChips();
        innerWhiteCard.add(faqPanel, BorderLayout.SOUTH);

        card.add(innerWhiteCard, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setOpaque(false);

        inputField = new RoundedTextField();
        sendButton = new SendArrowButton(38);
        sendButton.setPreferredSize(new Dimension(42, 38));

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        card.add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage(inputField.getText().trim()));
        inputField.addActionListener(e -> sendMessage(inputField.getText().trim()));

        return card;
    }

    private void rebuildFaqChips() {
        if (faqPanel == null) return;
        faqPanel.removeAll();

        JLabel promptLabel = new JLabel("What can I help you with?");
        promptLabel.setFont(Theme.font(13, true));
        promptLabel.setForeground(Theme.INK);
        promptLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        faqPanel.add(promptLabel);
        faqPanel.add(Box.createVerticalStrut(6));

        String userRole = (app != null && app.current != null && app.current.role != null) 
                          ? app.current.role.name() : "PATIENT";

        String[] presets = switch (userRole) {
            case "DOCTOR" -> new String[]{
                "How do I submit a time off request?",
                "How do I mark an appointment completed?",
                "Where can I view my daily schedule?",
                "How do I notify a patient to reschedule?"
            };
            case "RECEPTIONIST" -> new String[]{
                "Where do I review patient requests?",
                "How do I propose an alternate timeslot?",
                "Where can I check doctor availability?",
                "How do I view today's appointments?"
            };
            case "ADMIN" -> new String[]{
                "How do I add or remove doctors?",
                "Where can I see clinic metrics?",
                "How do I view clinic schedule?",
                "Where are doctor profiles managed?"
            };
            default -> new String[]{
                "How do I book an appointment?",
                "Where can I view doctor proposals?",
                "How do I reschedule or cancel?",
                "What services does CLYVR offer?"
            };
        };

        for (String preset : presets) {
            JButton chip = new JButton("[" + preset + "]");
            chip.setFont(Theme.font(12, true));
            chip.setForeground(new Color(0, 110, 180));
            chip.setContentAreaFilled(false);
            chip.setBorderPainted(false);
            chip.setFocusPainted(false);
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.setAlignmentX(Component.CENTER_ALIGNMENT);
            chip.addActionListener(e -> sendMessage(preset));
            faqPanel.add(chip);
            faqPanel.add(Box.createVerticalStrut(2));
        }

        faqPanel.revalidate();
        faqPanel.repaint();
    }

    private void initChatHtml() {
        chatHtml.setLength(0);
        chatHtml.append("<html><head><style>")
                .append("body { font-family: sans-serif; font-size: 12px; color: #1A5560; margin: 2px; width: 230px; word-wrap: break-word; } ")
                .append(".msg { margin-bottom: 8px; word-wrap: break-word; } ")
                .append("</style></head><body>")
                .append("<div class='msg'><b>CLYVR AI:</b> Hi! I'm CLYVR's AI Assistant.<br>")
                .append("I can answer questions about the system and help you navigate.<br></div>");
        updateChatPane();
    }

    private void appendMessage(String sender, String message, boolean isThinking) {
        String safeText = message.replace("&", "&amp;")
                                 .replace("<", "&lt;")
                                 .replace(">", "&gt;")
                                 .replace("\n", "<br>");

        if (isThinking) {
            chatHtml.append("<div id='thinking' class='msg'><b>CLYVR AI:</b> <i style='color:#777777;'>Thinking...</i></div>");
        } else if ("You".equals(sender)) {
            chatHtml.append("<div class='msg'><b>You:</b> ").append(safeText).append("</div>");
        } else {
            chatHtml.append("<div class='msg'><b>CLYVR AI:</b> ").append(safeText).append("</div>");
        }
        updateChatPane();
    }

    private void removeThinking() {
        String target = "<div id='thinking' class='msg'><b>CLYVR AI:</b> <i style='color:#777777;'>Thinking...</i></div>";
        int idx = chatHtml.lastIndexOf(target);
        if (idx != -1) {
            chatHtml.delete(idx, idx + target.length());
            updateChatPane();
        }
    }

    private void updateChatPane() {
        chatPane.setText(chatHtml.toString() + "</body></html>");
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
    }

    private void sendMessage(String query) {
        if (query == null || query.trim().isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastSendTime < 2000) return;
        lastSendTime = now;

        faqPanel.setVisible(false);

        appendMessage("You", query, false);
        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        appendMessage("CLYVR AI", "", true);

        String activePage = app != null && app.getPage() != null ? app.getPage() : "Dashboard";
        String userRole = (app != null && app.current != null && app.current.role != null) 
                          ? app.current.role.name() : "PATIENT";

        AIService.askAI(query, activePage, userRole).whenComplete((reply, error) -> {
            SwingUtilities.invokeLater(() -> {
                removeThinking();

                if (error != null) {
                    appendMessage("CLYVR AI", "Error processing response. Please try again.", false);
                } else {
                    appendMessage("CLYVR AI", reply, false);
                }

                Timer cooldownTimer = new Timer(1500, evt -> {
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.requestFocusInWindow();
                });
                cooldownTimer.setRepeats(false);
                cooldownTimer.start();
            });
        });
    }

    private static class RoundedPanel extends JPanel {
        private final Color fill;
        private final int radius;

        RoundedPanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(new Color(30, 100, 110, 60));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class ToggleRobotButton extends JButton {
        ToggleRobotButton() {
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(Theme.HEADER);
            g2.fillOval(1, 1, w - 3, h - 3);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawOval(1, 1, w - 3, h - 3);

            int cx = w / 2;
            int cy = h / 2 + 1;

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(cx, cy - 9, cx, cy - 14);
            g2.fillOval(cx - 2, cy - 17, 4, 4);

            g2.fillRoundRect(cx - 12, cy - 9, 24, 18, 6, 6);
            g2.fillRect(cx - 15, cy - 4, 3, 8);
            g2.fillRect(cx + 12, cy - 4, 3, 8);

            g2.setColor(Theme.HEADER);
            g2.fillOval(cx - 7, cy - 4, 5, 5);
            g2.fillOval(cx + 2, cy - 4, 5, 5);

            g2.setStroke(new BasicStroke(1.5f));
            g2.drawArc(cx - 5, cy + 1, 10, 5, 180, 180);

            g2.dispose();
        }
    }

    private static class SendArrowButton extends JButton {
        private final int radius;

        SendArrowButton(int radius) {
            this.radius = radius;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(Theme.LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

            g2.setColor(Theme.INK);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            Path2D arrow = new Path2D.Double();
            arrow.moveTo(cx - 4, cy - 6);
            arrow.lineTo(cx + 5, cy);
            arrow.lineTo(cx - 4, cy + 6);
            arrow.closePath();

            g2.fill(arrow);
            g2.dispose();
        }
    }

    private static class RoundedTextField extends JTextField {
        RoundedTextField() {
            setFont(Theme.font(12, false));
            setForeground(Theme.INK);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 8, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.setColor(Theme.LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}