package ch.heigvd.gui;

import javafx.application.Application;

/**
 * Launcher séparé recommandé pour JavaFX (surtout quand tu packages en fat-jar / jpackage).
 */
public final class ImageToolBoxLauncher {
    public static void main(String[] args) {
        Application.launch(ImageToolBoxApp.class, args);
    }
}
