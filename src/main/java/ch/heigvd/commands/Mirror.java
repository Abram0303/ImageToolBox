package ch.heigvd.commands;

import ch.heigvd.ImageProcessor;
import ch.heigvd.util.Images;
import picocli.CommandLine;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "mirror",
        description = "Apply a mirror effect to the image (horizontal and/or vertical)."
)
public class Mirror implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-h", "--horizontal"},
            description = "Apply a horizontal mirror (left-right)."
    )
    public boolean horizontal;

    @CommandLine.Option(
            names = {"-v", "--vertical"},
            description = "Apply a vertical mirror (top-bottom)."
    )
    public boolean vertical;

    @CommandLine.ParentCommand
    protected ImageProcessor parent;

    @Override
    public Integer call() {
        try {
            Images.io = parent.io;

            BufferedImage imageIn = Images.readImage();
            int w = imageIn.getWidth();
            int h = imageIn.getHeight();

            Raster rin = imageIn.getRaster();
            BufferedImage imageOut = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            WritableRaster rout = imageOut.getRaster();

            int[] pixel = new int[3]; // RGB

            // Si aucun des deux n’est spécifié, on fait un miroir horizontal par défaut
            if (!horizontal && !vertical) {
                horizontal = true;
            }

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    rin.getPixel(x, y, pixel);

                    int targetX = horizontal ? (w - 1 - x) : x;
                    int targetY = vertical ? (h - 1 - y) : y;

                    rout.setPixel(targetX, targetY, pixel);
                }
            }

            Images.writeImage(imageOut);

            String orientation = (horizontal && vertical) ? "both axes" :
                    horizontal ? "horizontally" :
                            "vertically";
            System.out.println("Image successfully mirrored " + orientation + ".");

            return 0;

        } catch (Exception e) {
            System.err.println("[mirror] " + e.getMessage());
            return 1;
        }
    }
}

