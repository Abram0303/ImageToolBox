package ch.heigvd.commands;

import ch.heigvd.ImageToolBox;
import ch.heigvd.util.Images;
import picocli.CommandLine;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

// PDFBox
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

@CommandLine.Command(
        name = "tileA4",
        description = "Tile logos across an A4 page for printing. Supports multiple logos, one per row."
)
public class TileA4 implements Callable<Integer> {

    // ---- Paramètres généraux ----

    @CommandLine.Option(
            names = {"--shape"},
            description = "Logo shape: circle or rect (default: circle).",
            defaultValue = "circle"
    )
    public String shape;

    @CommandLine.Option(
            names = {"--diameter-cm"},
            description = "Target circle diameter in centimeters (for shape=circle).",
            defaultValue = "3.2"
    )
    public double diameterCm = 3.2;

    @CommandLine.Option(
            names = {"--rect-width-cm"},
            description = "Rectangular logo width in centimeters (for shape=rect).",
            defaultValue = "4.0"
    )
    public double rectWidthCm = 4.0;

    @CommandLine.Option(
            names = {"--rect-height-cm"},
            description = "Rectangular logo height in centimeters (for shape=rect).",
            defaultValue = "2.0"
    )
    public double rectHeightCm = 2.0;

    @CommandLine.Option(
            names = {"--gap-mm"},
            description = "Gap between logos in millimeters.",
            defaultValue = "8"
    )
    public double gapMm = 8.0;

    @CommandLine.Option(
            names = {"--margin-mm"},
            description = "Margin around the page in millimeters.",
            defaultValue = "2"
    )
    public double marginMm = 2.0;

    @CommandLine.Option(
            names = {"--dpi"},
            description = "Output DPI.",
            defaultValue = "300"
    )
    public int dpi = 300;

    @CommandLine.Option(
            names = {"--boost-colors"},
            description = "Facteur de saturation pour rendre les logos plus vifs (1.0 = inchangé, 1.5 conseillé pour pastels).",
            defaultValue = "1.0"
    )
    public double boostColors = 1.0;

    // ---- Options de forme / source ----

    /**
     * Optional: extract only a circular region from the source image before tiling.
     * Format: "cx,cy,r" where each value is either an integer in pixels (e.g. 512)
     * or a percentage of the min(srcW, srcH) with a % sign (e.g. 50%).
     * Examples:
     *   --src-circle "512,512,450"
     *   --src-circle "50%,50%,42%"
     */
    @CommandLine.Option(
            names = {"--src-circle"},
            description = "Crop a circular region from each source before tiling (format: cx,cy,r; values in px or % of min dimension)."
    )
    public String srcCircleSpec;

    @CommandLine.Option(
            names = {"--no-mask"},
            description = "Disable circular masking (draw the full image rectangle even in circle mode)."
    )
    public boolean noMask = false;

    // ---- Options de miroir ----

    @CommandLine.Option(
            names = {"--mirror-horizontal"},
            description = "Mirror the source images horizontally (left-right) before tiling."
    )
    public boolean mirrorHorizontal;

    @CommandLine.Option(
            names = {"--mirror-vertical"},
            description = "Mirror the source images vertically (top-bottom) before tiling."
    )
    public boolean mirrorVertical;

    // ---- Multi-inputs : un logo par ligne ----

    @CommandLine.Option(
            names = {"-I", "--inputs"},
            description = "List of input logo files (one per row, up to 7).",
            split = ","
    )
    public List<File> inputFiles = new ArrayList<>();

    @CommandLine.ParentCommand
    protected ImageToolBox parent;

    // A4 in mm
    private static final double A4_W_MM = 210.0;
    private static final double A4_H_MM = 297.0;

    // ---- Utils conversions ----

    private static int mmToPx(double mm, int dpi) {
        return (int) Math.round(mm / 25.4 * dpi);
    }

    private static int cmToPx(double cm, int dpi) {
        return mmToPx(cm * 10.0, dpi);
    }

    private static int parseComponent(String token, int minDim) {
        token = token.trim();
        if (token.endsWith("%")) {
            double p = Double.parseDouble(token.substring(0, token.length() - 1));
            return (int) Math.round(p / 100.0 * minDim);
        }
        return Integer.parseInt(token);
    }

    private static BufferedImage cropCircleToSquare(BufferedImage src, int cx, int cy, int r) {
        int side = Math.max(1, 2 * r);
        int x = Math.max(0, cx - r);
        int y = Math.max(0, cy - r);
        if (x + side > src.getWidth())  side = src.getWidth() - x;
        if (y + side > src.getHeight()) side = src.getHeight() - y;
        return src.getSubimage(x, y, side, side);
    }

    /**
     * Boost la saturation des couleurs pour rendre l'image plus "flashy".
     * Utilisé pour que les logos pastel ressortent mieux à l'impression/transfert.
     *
     * @param src           image source
     * @param saturationFac facteur de saturation (>1.0 pour booster, 1.5-2.0 pour pastels)
     * @return nouvelle image avec couleurs boostées
     */
    private static BufferedImage boostColors(BufferedImage src, double saturationFac) {
        if (saturationFac <= 1.0) {
            return src; // pas de boost demandé
        }

        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb) & 0xFF;

                float[] hsb = Color.RGBtoHSB(r, g, b, null);

                // Si la couleur est vraiment très pastel (faible saturation), on booste un peu plus.
                double factor = saturationFac;
                if (hsb[1] < 0.3f) {
                    factor *= 1.2; // petit bonus pour les tons très pastel
                }

                hsb[1] = (float) Math.min(1.0, hsb[1] * factor);

                int newRgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                out.setRGB(x, y, newRgb);
            }
        }

        return out;
    }

    private static BufferedImage mirrorImage(BufferedImage src, boolean horizontal, boolean vertical) {
        if (!horizontal && !vertical) {
            return src;
        }

        int w = src.getWidth();
        int h = src.getHeight();

        // On conserve le type d'image d'origine (pour garder la transparence si c'est du PNG)
        // Si le type est inconnu (0), on utilise ARGB par sécurité
        int type = (src.getType() == 0) ? BufferedImage.TYPE_INT_ARGB : src.getType();

        BufferedImage out = new BufferedImage(w, h, type);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // getRGB récupère la couleur complète (y compris transparence) en un seul nombre
                int rgb = src.getRGB(x, y);

                int targetX = horizontal ? (w - 1 - x) : x;
                int targetY = vertical   ? (h - 1 - y) : y;

                out.setRGB(targetX, targetY, rgb);
            }
        }
        return out;
    }

    // Préparation d'une source : crop + miroir + boost couleurs
    private BufferedImage prepareSource(BufferedImage src0) {
        BufferedImage src = src0;

        if (srcCircleSpec != null && !srcCircleSpec.isBlank()) {
            String[] parts = srcCircleSpec.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Bad --src-circle format. Expected: cx,cy,r");
            }
            int minDim = Math.min(src0.getWidth(), src0.getHeight());
            int cx = parseComponent(parts[0], minDim);
            int cy = parseComponent(parts[1], minDim);
            int r  = parseComponent(parts[2], minDim);
            src = cropCircleToSquare(src0, cx, cy, r);
        }

        src = mirrorImage(src, mirrorHorizontal, mirrorVertical);

        // Nouveau : boost de couleurs si demandé
        if (boostColors > 1.0) {
            src = boostColors(src, boostColors);
        }

        return src;
    }


    // Écriture PDF A4 natif à partir de l'image de page
    private void writePdfA4(BufferedImage pageImage, File outputFile) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage pdfPage = new PDPage(PDRectangle.A4);
            doc.addPage(pdfPage);

            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, pageImage);

            try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                float pageW = pdfPage.getMediaBox().getWidth();
                float pageH = pdfPage.getMediaBox().getHeight();

                float imgW = pdImage.getWidth();
                float imgH = pdImage.getHeight();

                float scale = Math.min(pageW / imgW, pageH / imgH);
                float drawW = imgW * scale;
                float drawH = imgH * scale;

                float x = (pageW - drawW) / 2f;
                float y = (pageH - drawH) / 2f;

                cs.drawImage(pdImage, x, y, drawW, drawH);
            }

            doc.save(outputFile);
        }
    }

    @Override
    public Integer call() {
        try {
            Images.io = parent.io;

            // --- 1) Charger les sources (multi-input, sinon -i classique) ---
            List<BufferedImage> sources = new ArrayList<>();

            if (!inputFiles.isEmpty()) {
                for (File f : inputFiles) {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null) {
                        throw new IllegalStateException("Cannot read image: " + f);
                    }
                    sources.add(prepareSource(img));
                }
            } else {
                BufferedImage src0 = Images.readImage();
                if (src0 == null) {
                    throw new IllegalStateException("Input image is null (check -i/--input or --inputs).");
                }
                sources.add(prepareSource(src0));
            }

            if (sources.isEmpty()) {
                throw new IllegalStateException("No input images provided.");
            }

            // max 7 lignes (1 logo par ligne)
            if (sources.size() > 7) {
                System.out.println("Warning: more than 7 input images, only the first 7 will be used.");
                sources = sources.subList(0, 7);
            }

            int nbLogos = sources.size();

            // --- 2) Créer la page A4 en pixels ---
            int pageW = mmToPx(A4_W_MM, dpi);
            int pageH = mmToPx(A4_H_MM, dpi);

            BufferedImage page = new BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = page.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, pageW, pageH);

                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int marginPx = mmToPx(marginMm, dpi);
                int gapPx = mmToPx(gapMm, dpi);

                boolean rect = shape != null && shape.equalsIgnoreCase("rect");

                int tileW;
                int tileH;

                if (rect) {
                    tileW = cmToPx(rectWidthCm, dpi);
                    tileH = cmToPx(rectHeightCm, dpi);
                } else {
                    tileW = tileH = cmToPx(diameterCm, dpi);
                }

                int usableW = pageW - 2 * marginPx;
                int usableH = pageH - 2 * marginPx;

                int stepX = tileW + gapPx;
                int stepY = tileH + gapPx;

                int cols = Math.max(1, (usableW + gapPx) / stepX);
                int maxRowsThatFit = Math.max(1, (usableH + gapPx) / stepY);
                int rows = Math.min(nbLogos, maxRowsThatFit);

                int totalW = cols * tileW + (cols - 1) * gapPx;
                int totalH = rows * tileH + (rows - 1) * gapPx;

                int startX = marginPx + (usableW - totalW) / 2;
                int startY = marginPx + (usableH - totalH) / 2;

                Shape oldClip = g.getClip();

                // --- 3) Dessin : une ligne = un logo différent ---
                for (int row = 0; row < rows; row++) {
                    BufferedImage src = sources.get(row); // logo de cette ligne

                    for (int col = 0; col < cols; col++) {
                        int x = startX + col * (tileW + gapPx);
                        int y = startY + row * (tileH + gapPx);

                        if (rect) {
                            g.setClip(x, y, tileW, tileH);
                        } else {
                            if (!noMask) {
                                Shape circle = new Ellipse2D.Double(x, y, tileW, tileH);
                                g.setClip(circle);
                            } else {
                                g.setClip(x, y, tileW, tileH);
                            }
                        }

                        double scale;
                        if (rect) {
                            scale = Math.min(
                                    (double) tileW / src.getWidth(),
                                    (double) tileH / src.getHeight()
                            );
                        } else {
                            scale = (double) tileW / Math.min(src.getWidth(), src.getHeight());
                        }

                        int drawW = (int) Math.round(src.getWidth() * scale);
                        int drawH = (int) Math.round(src.getHeight() * scale);

                        int dx = x + (tileW - drawW) / 2;
                        int dy = y + (tileH - drawH) / 2;

                        g.drawImage(src, dx, dy, drawW, drawH, null);
                        g.setClip(oldClip);
                    }
                }

                // Repères légers (facultatif)
                g.setColor(new Color(0, 0, 0, 40));
                int markerLen = mmToPx(1, dpi);

                for (int col = 0; col < cols; col++) {
                    int cx = startX + col * (tileW + gapPx) + tileW / 2;
                    g.drawLine(cx, marginPx - markerLen, cx, marginPx);
                    g.drawLine(cx, pageH - marginPx, cx, pageH - marginPx + markerLen);
                }
                for (int row = 0; row < rows; row++) {
                    int cy = startY + row * (tileH + gapPx) + tileH / 2;
                    g.drawLine(marginPx - markerLen, cy, marginPx, cy);
                    g.drawLine(pageW - marginPx, cy, pageW - marginPx + markerLen, cy);
                }

                System.out.printf("Grid: %d cols x %d rows = %d tiles (%d distinct logos)%n",
                        cols, rows, cols * rows, nbLogos);
            } finally {
                g.dispose();
            }

            // --- 4) Sortie : PDF A4 natif ou image ---
            File output = parent.io.outputFile;
            String outName = output.getName().toLowerCase();

            if (outName.endsWith(".pdf")) {
                writePdfA4(page, output);
                System.out.println("A4 PDF generated. Print at 100% scale.");
            } else {
                Images.writeImage(page);
                System.out.println("Image generated. Print at 100% scale.");
            }

            return 0;

        } catch (Exception e) {
            System.err.println("[tileA4] " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
