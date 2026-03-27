package com.santea;

import javafx.application.Application;

public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        if (!hasGraphicalDisplay()) {
            System.err.println("JavaFX ne peut pas ouvrir d'interface: DISPLAY/WAYLAND_DISPLAY est absent.");
            System.err.println("Lance l'application depuis une session graphique (desktop local, VNC, ou SSH -X). ");
            return;
        }

        Application.launch(com.santea.Main.class, args);
    }

    private static boolean hasGraphicalDisplay() {
        String display = System.getenv("DISPLAY");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        return (display != null && !display.isBlank()) || (waylandDisplay != null && !waylandDisplay.isBlank());
    }
}