package ch.heigvd.commands;

import ch.heigvd.ImageToolBox;
import ch.heigvd.util.Images;
import picocli.CommandLine;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "grayscale",
        description = "Convert an image to grayscale."
)

public class Grayscale implements Callable<Integer> {

    @CommandLine.ParentCommand
    protected ImageToolBox parent;

    @Override
    public Integer call(){
        try{
            Images.io = parent.io;

            // Read the input image
            BufferedImage imageIn = Images.readImage();
            int width = imageIn.getWidth();
            int height = imageIn.getHeight();

            // Create the output image
            BufferedImage imageOut = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // Allows access to the pixel in the image
            // Proposed by ChatGPT, written by RDL
            Raster rasterIn = imageIn.getRaster();
            WritableRaster rasterOut = imageOut.getRaster();

            int[] pin  = new int[3]; // R-G-B
            int[] pout = new int[3];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {

                    rasterIn.getPixel(x, y, pin); // Get the pixel in position (x, y)
                    int r = pin[0], g = pin[1], b = pin[2];

                    // Conversion to grayscale
                    int gray = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b); // The most common formula
                    if (gray < 0) gray = 0;
                    else if (gray > 255) gray = 255;

                    pout[0] = pout[1] = pout[2] = gray;
                    rasterOut.setPixel(x, y, pout);
                }
            }

            // Write the output image
            Images.writeImage(imageOut);
            System.out.println("Image successfully converted to grayscale.");
            return 0;

        } catch (Exception e) {
            System.err.println("[grayscale] " + e.getMessage());
            return 1;
        }
    }
}