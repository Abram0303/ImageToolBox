# ImageToolBox 

---

## Table of Contents

* [Authors](#authors)
* [Project Overview](#project-overview)
* [Implemented Commands](#implemented-commands)
* [Build Instructions](#build-instructions)
* [Usage Instructions](#usage-instructions)

    * [From the Terminal](#from-the-terminal)
    * [From IntelliJ IDEA](#from-intellij-idea)
* [A4 Logo Tiling Feature](#a4-logo-tiling-feature)
* [Use of AI Tools](#use-of-ai-tools)

---

## Authors

* **Romain Durussel**
* **Abram Zweifel**

HEIG-VD, Class C, 2025–2026

---

## Project Overview

**ImageToolBox** is a Java-based command-line toolkit for basic image processing operations.

The tool allows users to apply transformations to RGB images directly from the terminal without requiring specialized image-editing applications.
It demonstrates:

* Image manipulation via `BufferedImage`, `Raster`, `WritableRaster`
* Efficient stream-based file I/O
* Modular CLI using **Picocli**
* Packaging with **Maven**
* Automatic generation of **native A4 PDF sheets**

---

## Implemented Commands

### `grayscale`

Converts a color image to grayscale.

### `invert`

Inverts all color channels.

### `rotate`

Rotates an image by 90°, 180°, or 270°.

### `mirror`

Applies horizontal and/or vertical mirroring.

### `tileA4` (Advanced Feature)

Creates complete A4 sheets with repeated logos:

* Multiple inputs (`-I file1,file2,...`)
* One logo per row (up to 7 rows)
* Circular & rectangular modes
* User-defined size in cm
* Mirroring options
* Native PDF output if the output ends with `.pdf`

Example use-case: preparing mirrored edible-print logos for meringue transfers.

---

## Build Instructions

Clone and build the project:

```bash
git clone https://github.com/Abram0303/ImageToolBox.git
cd ImageToolBox
./mvnw clean package
```

After building, Maven produces:

```
target/ImageToolBox-1.0-SNAPSHOT.jar
target/ImageToolBox-1.0-SNAPSHOT-shaded.jar   ← includes all dependencies (RECOMMENDED)
```

---

## Usage Instructions

### From the Terminal

#### Grayscale conversion

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar \
  -i image/input/input1.jpg -o output/grayscale.jpg grayscale
```

#### Invert colors

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar \
  -i image/input/input1.jpg -o output/invert.jpg invert
```

#### Rotate 90°

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar \
  -i image/input/input1.jpg -o output/rotate.jpg rotate -a 90
```

#### Help

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar -h
```

#### Version

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar -V
```

---

## From IntelliJ IDEA

1. Open the project
2. Select **Run ImageProcessor** or create a run configuration
3. Use **Package JAR** to generate the executable artifact
4. Run the CLI via:

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar -h
```

---

## A4 Logo Tiling Feature

### Example: Generate a PDF A4 page containing 35 rectangular mirrored logos

```bash
java -jar target/ImageToolBox-1.0-SNAPSHOT-shaded.jar \
  -i image/input/logo1.jpg \
  -o planche_35logos.pdf \
  tileA4 \
  --shape rect \
  --rect-width-cm 3.4 \
  --rect-height-cm 2.7 \
  --gap-mm 8 \
  --margin-mm 2 \
  --mirror-horizontal \
  -I image/input/logo1.jpg,image/input/logo1.jpg,image/input/logo1.jpg,image/input/logo1.jpg,image/input/logo1.jpg,image/input/logo1.jpg,image/input/logo1.jpg
```

This produces:

* A **native A4** PDF (210 × 297 mm)
* Automatic grid (≈5 columns × 7 rows)
* 35 proportional, non-distorted logos
* Perfect alignment for high-quality printing

---

## Use of AI Tools

ChatGPT was used as a support tool to:

* Explore and validate Java imaging techniques
* Suggest improvements for `tileA4`
* Help implement native A4 PDF generation (PDFBox)
* Assist in documentation and structure

All final code was manually integrated and adapted by the authors.
