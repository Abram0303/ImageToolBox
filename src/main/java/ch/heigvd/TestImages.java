package ch.heigvd;

import ch.heigvd.util.Images;
import picocli.CommandLine;

import java.awt.image.BufferedImage;
import java.io.File;

public class TestImages {
    public static void main(String[] args) {
        IOOptions io = new IOOptions();
        io.inputFile = new java.io.File("image/imageTest.jpg");
        io.outputFile = null;

        if (io.outputFile == null) {
            io.outputFile = new File("image/output.jpg");
        }

        // Simule le mixin : on assigne manuellement à Images.io
        Images.io = io;

        // Lecture de l’image
        BufferedImage img = Images.readImage();
        System.out.println("Image lue : " + img.getWidth() + "x" + img.getHeight());

        // Écriture (on réécrit la même image ici)
        Images.writeImage(img);
        System.out.println("Image écrite dans " + io.outputFile.getPath());
    }
}

