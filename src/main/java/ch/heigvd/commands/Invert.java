package ch.heigvd.commands;

import ch.heigvd.ImageToolBox;
import ch.heigvd.util.Images;
import picocli.CommandLine;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "invert",
        description = "Invert the color in the image."
)

public class Invert implements Callable<Integer> {

    @CommandLine.ParentCommand
    protected ImageToolBox parent;

    @Override
    public Integer call() {
        try {

            Images.io = parent.io;

            // Read the input image
            BufferedImage imageIn = Images.readImage();
            int width = imageIn.getWidth();
            int height = imageIn.getHeight();

            // Create the output image
            BufferedImage imageOut = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // Allows access to the pixel in the image
            // Proposed by ChatGPT, written by AZL
            Raster rasterIn = imageIn.getRaster();
            WritableRaster rasterOut = imageOut.getRaster();

            int[] pixel = new int[3]; // R-G-B

            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){
                    rasterIn.getPixel(x, y, pixel);

                    // Invert the colors
                    pixel[0] = 255 - pixel[0];
                    pixel[1] = 255 - pixel[1];
                    pixel[2] = 255 - pixel[2];

                    rasterOut.setPixel(x, y, pixel);
                }
            }

            // Write the output image
            Images.writeImage(imageOut);
            System.out.println("Colors successfully inverted.");
            return 0;

        } catch (Exception e) {
            System.err.println("[invert] " + e.getMessage());
            return 1;
        }
    }
}
