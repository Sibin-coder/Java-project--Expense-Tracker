package ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.function.Consumer;

public class LoginDialog extends JDialog {
    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final Consumer<String> errorHandler;
    private boolean cancelled;
    private boolean createAccount;

    public LoginDialog(Consumer<String> errorHandler) {
        super((Frame) null, "Expense Tracker Login", true);
        this.errorHandler = errorHandler;
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        root.setBackground(new Color(239, 246, 255));

        JLabel title = new JLabel("Welcome back");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(new Color(30, 64, 175));
        JLabel subtitle = new JLabel("Sign in to manage your personal expenses");
        subtitle.setForeground(new Color(71, 85, 105));
        JPanel heading = new JPanel(new GridLayout(0, 1, 2, 2));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(subtitle);
        root.add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 1, 6, 6));
        fields.setOpaque(false);
        fields.add(new JLabel("Username"));
        fields.add(username);
        fields.add(new JLabel("Password"));
        fields.add(password);
        root.add(fields, BorderLayout.CENTER);

        JButton login = new JButton("Login");
        JButton register = new JButton("Create new account");
        JButton cancel = new JButton("Cancel");
        login.setBackground(new Color(37, 99, 235));
        login.setForeground(Color.WHITE);
        register.setForeground(new Color(30, 64, 175));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        buttons.add(register);
        buttons.add(cancel);
        buttons.add(login);
        root.add(buttons, BorderLayout.SOUTH);

        login.addActionListener(e -> {
            if (username.getText().trim().isEmpty() || password.getPassword().length == 0) {
                errorHandler.accept("Enter both username and password.");
                return;
            }
            dispose();
        });
        register.addActionListener(e -> {
            createAccount = true;
            dispose();
        });
        cancel.addActionListener(e -> {
            cancelled = true;
            dispose();
        });
        getRootPane().setDefaultButton(login);
        setContentPane(root);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }

    public String getUsername() {
        return username.getText().trim();
    }

    public char[] getPassword() {
        return password.getPassword();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isCreateAccount() {
        return createAccount;
    }
}
