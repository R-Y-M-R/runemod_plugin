package net.runelite.client.plugins.runemod;

import net.runelite.client.ui.ClientUI;

import javax.swing.*;
import java.awt.*;

public class RuneMod_statusUI implements Runnable {

    public JLabel message = new JLabel();
    public JOptionPane pane = new JOptionPane(message, JOptionPane.DEFAULT_OPTION, JOptionPane.DEFAULT_OPTION, null, null, null);
    public JDialog dialog;

    @Override
    public void run()
    {
    }

    RuneMod_statusUI() {
        pane.setWantsInput(false);
        dialog = pane.createDialog(null, "no title yet");
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.setAlwaysOnTop(true);
        dialog.setLocation(ClientUI.getFrame().getLocation().x+20, ClientUI.getFrame().getLocation().y+20);
        dialog.setVisible(true);
        message.setText("no message yet");
    }

    public void SetMessage(String statusText) {
        message.setText(statusText);
    }

    public static void main(String args[]) {
        new RuneMod_statusUI();
    }
}
