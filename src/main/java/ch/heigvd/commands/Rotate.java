package ch.heigvd.commands;

import ch.heigvd.ImageToolBox;
import ch.heigvd.util.Images;
import picocli.CommandLine;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rotate",
        description = "Rotate an image by 90, 180, or 270 degrees."
)

public class Rotate implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-a", "--angle"},
            description = "Rotation angle (valid: 90, 180, 270).",
            defaultValue = "90"
    )

    public int angle;

    @CommandLine.ParentCommand
    protected ImageToolBox parent;

    public Integer call(){
        try {
            Images.io = parent.io;

            // Read the input image
            BufferedImage imageIn = Images.readImage();
            int w = imageIn.getWidth();
            int h = imageIn.getHeight();

            // Proposed by ChatGPT, written by RDL & AZL
            Raster rin = imageIn.getRaster();

            if (angle != 90 && angle != 180 && angle != 270) {
                throw new IllegalArgumentException("Angle invalide: " + angle + " (valeurs valides: 90, 180, 270)");
            }

            BufferedImage imageOut;
            WritableRaster rout;
            int[] pixel = new int[3]; // R-G-B

            switch (angle) {
                case 90: // (x,y) --> (h-1-y, x)
                    imageOut = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                    rout = imageOut.getRaster();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            rin.getPixel(x, y, pixel);
                            rout.setPixel(h - 1 - y, x, pixel);
                        }
                    }
                    break;

                case 180: // (x,y) --> (w-1-x, h-1-y)
                    imageOut = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    rout = imageOut.getRaster();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            rin.getPixel(x, y, pixel);
                            rout.setPixel(w - 1 - x, h - 1 - y, pixel);
                        }
                    }
                    break;

                case 270: // (x,y) --> (y, w-1-x)
                    imageOut = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                    rout = imageOut.getRaster();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            rin.getPixel(x, y, pixel);
                            rout.setPixel(y, w - 1 - x, pixel);
                        }
                    }
                    break;

                default:
                    // Should never happen
                    imageOut = imageIn;
                    angle = 0;
            }

            // Write the output image
            Images.writeImage(imageOut);
            System.out.println("Image successfully rotated by " + angle + " degrees.");
            return 0;

        } catch (Exception e) {
            System.err.println("[rotate] " + e.getMessage());
            return 1;
        }
    }
}
