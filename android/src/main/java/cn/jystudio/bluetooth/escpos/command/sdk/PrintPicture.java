package cn.jystudio.bluetooth.escpos.command.sdk;

import android.graphics.*;
import android.os.Environment;
import android.text.TextPaint;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import android.graphics.Bitmap.Config;


import java.io.UnsupportedEncodingException;

public class PrintPicture {
    private static int[] p0 = new int[]{0, 128};
    private static int[] p1 = new int[]{0, 64};
    private static int[] p2 = new int[]{0, 32};
    private static int[] p3 = new int[]{0, 16};
    private static int[] p4 = new int[]{0, 8};
    private static int[] p5 = new int[]{0, 4};
    private static int[] p6 = new int[]{0, 2};
    private static int[][] Floyd16x16 = new int[][]{{0, 128, 32, 160, 8, 136, 40, 168, 2, 130, 34, 162, 10, 138, 42, 170}, {192, 64, 224, 96, 200, 72, 232, 104, 194, 66, 226, 98, 202, 74, 234, 106}, {48, 176, 16, 144, 56, 184, 24, 152, 50, 178, 18, 146, 58, 186, 26, 154}, {240, 112, 208, 80, 248, 120, 216, 88, 242, 114, 210, 82, 250, 122, 218, 90}, {12, 140, 44, 172, 4, 132, 36, 164, 14, 142, 46, 174, 6, 134, 38, 166}, {204, 76, 236, 108, 196, 68, 228, 100, 206, 78, 238, 110, 198, 70, 230, 102}, {60, 188, 28, 156, 52, 180, 20, 148, 62, 190, 30, 158, 54, 182, 22, 150}, {252, 124, 220, 92, 244, 116, 212, 84, 254, 126, 222, 94, 246, 118, 214, 86}, {3, 131, 35, 163, 11, 139, 43, 171, 1, 129, 33, 161, 9, 137, 41, 169}, {195, 67, 227, 99, 203, 75, 235, 107, 193, 65, 225, 97, 201, 73, 233, 105}, {51, 179, 19, 147, 59, 187, 27, 155, 49, 177, 17, 145, 57, 185, 25, 153}, {243, 115, 211, 83, 251, 123, 219, 91, 241, 113, 209, 81, 249, 121, 217, 89}, {15, 143, 47, 175, 7, 135, 39, 167, 13, 141, 45, 173, 5, 133, 37, 165}, {207, 79, 239, 111, 199, 71, 231, 103, 205, 77, 237, 109, 197, 69, 229, 101}, {63, 191, 31, 159, 55, 183, 23, 151, 61, 189, 29, 157, 53, 181, 21, 149}, {254, 127, 223, 95, 247, 119, 215, 87, 253, 125, 221, 93, 245, 117, 213, 85}};
    private static final int DITHERING_NO = 0;
    private static final int DITHERING_RANDOM_THRESHOLD = 1;
    private static final int DITHERING_FLOYD_STEINBERG = 2;
    private static final int DITHERING_FLOYD_STEINBERG_OLD = 3;
    private static final byte CAN = 24;
    private static final byte ESC = 27;
    private static final byte GS = 29;
    private static final byte[] cmd_ESCFF = new byte[]{27, 12};
    private static final int MAX_RLE_LENGTH = 62;

    static class C3 {
        int r;
        int g;
        int b;

        public C3(int pixel) {
            this.r = Color.red(pixel);
            this.g = Color.green(pixel);
            this.b = Color.blue(pixel);
        }

        public C3(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public PrintPicture.C3 add(PrintPicture.C3 o) {
            return new PrintPicture.C3(this.r + o.r, this.g + o.g, this.b + o.b);
        }

        public PrintPicture.C3 sub(PrintPicture.C3 o) {
            return new PrintPicture.C3(this.r - o.r, this.g - o.g, this.b - o.b);
        }

        public PrintPicture.C3 mul(double d) {
            return new PrintPicture.C3((int)(d * (double)this.r), (int)(d * (double)this.g), (int)(d * (double)this.b));
        }

        public int diff(PrintPicture.C3 o) {
            return Math.abs(this.r - o.r) + Math.abs(this.g - o.g) + Math.abs(this.b - o.b);
        }

        public int toRGB() {
            return Color.argb(255, this.clamp(this.r), this.clamp(this.g), this.clamp(this.b));
        }

        public int clamp(int c) {
            return Math.max(0, Math.min(255, c));
        }
    }
    
    public static byte[] printImageNew(int x, int y, int width, int height, Bitmap bmp, boolean compressing, int ditherType, boolean landscape) {
        if (width <= 0) {
            width = bmp.getWidth();
        }

        if (height <= 0) {
            height = bmp.getHeight();
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
        byte xl = (byte)(x & 255);
        byte xh = (byte)(x >> 8 & 255);
        byte yl = (byte)(y & 255);
        byte yh = (byte)(y >> 8 & 255);
        byte wl = (byte)(width & 255);
        byte wh = (byte)(width >> 8 & 255);
        byte hl = (byte)(height & 255);
        byte hh = (byte)(height >> 8 & 255);
        byte[] cmd_ESCW = new byte[]{27, 87, xl, xh, yl, yh, wl, wh, hl, hh};
        byteStream.write(cmd_ESCW, 0, cmd_ESCW.length);
        byte[] img;
        if (landscape) {
            img = new byte[]{27, 84, 3};
            byteStream.write(img, 0, img.length);
        }

        img = convertBMPtoX4image(bmp, ditherType);
        int bmpW = bmp.getWidth();
        int bmpH = bmp.getHeight();
        int imgRowBytes = bmpW / 8 + (bmpW % 8 == 0 ? 0 : 1);
        if (landscape) {
            if (bmpH > width) {
                bmpH = width;
            }
        } else if (bmpH > height) {
            bmpH = height;
        }

        int page;
        byte[] cmd_ESCX3;
        byte[] compressedImg;
        byte[] cmd_ESCX2;
        for(page = 0; page < bmpH / 255; ++page) {
            if (compressing) {
                cmd_ESCX3 = new byte[]{27, 88, 51, (byte)imgRowBytes, -1};
                compressedImg = convertImageX4toX3(img, imgRowBytes * 255 * page, imgRowBytes * 255);
                cmd_ESCX2 = new byte[]{27, 88, 50, -1};
                byteStream.write(cmd_ESCX3, 0, cmd_ESCX3.length);
                byteStream.write(compressedImg, 0, compressedImg.length);
                byteStream.write(cmd_ESCX2, 0, cmd_ESCX2.length);
            } else {
                cmd_ESCX3 = new byte[]{27, 88, 52, (byte)imgRowBytes, -1};
                byteStream.write(cmd_ESCX3, 0, cmd_ESCX3.length);
                byteStream.write(img, imgRowBytes * 255 * page, imgRowBytes * 255);
            }

            int offset = 255 * (page + 1);
            byte ol = (byte)(offset & 255);
            byte oh = (byte)(offset >> 8 & 255);
            byte[] cmd_ESCO = new byte[]{27, 79, 0, 0, ol, oh};
            if (landscape) {
                cmd_ESCO[2] = ol;
                cmd_ESCO[3] = oh;
                cmd_ESCO[4] = 0;
                cmd_ESCO[5] = 0;
            }

            byteStream.write(cmd_ESCO, 0, cmd_ESCO.length);
        }

        if (bmpH % 255 != 0) {
            if (compressing) {
                cmd_ESCX3 = new byte[]{27, 88, 51, (byte)imgRowBytes, (byte)(bmpH % 255)};
                compressedImg = convertImageX4toX3(img, imgRowBytes * 255 * page, imgRowBytes * (bmpH % 255));
                cmd_ESCX2 = new byte[]{27, 88, 50, (byte)(bmpH % 255)};
                byteStream.write(cmd_ESCX3, 0, cmd_ESCX3.length);
                byteStream.write(compressedImg, 0, compressedImg.length);
                byteStream.write(cmd_ESCX2, 0, cmd_ESCX2.length);
            } else {
                cmd_ESCX3 = new byte[]{27, 88, 52, (byte)imgRowBytes, (byte)(bmpH % 255)};
                byteStream.write(cmd_ESCX3, 0, cmd_ESCX3.length);
                byteStream.write(img, imgRowBytes * 255 * page, imgRowBytes * (bmpH % 255));
            }
        }

        byteStream.write(cmd_ESCFF, 0, cmd_ESCFF.length);
        return byteStream.toByteArray();
    }

    private static PrintPicture.C3 findClosestPaletteColor(PrintPicture.C3 c, PrintPicture.C3[] palette) {
        PrintPicture.C3 closest = palette[0];
        PrintPicture.C3[] var3 = palette;
        int var4 = palette.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            PrintPicture.C3 n = var3[var5];
            if (n.diff(c) < closest.diff(c)) {
                closest = n;
            }
        }

        return closest;
    }

    private static Bitmap convertGrayScale(Bitmap bmpOriginal) {
        int height = bmpOriginal.getHeight();
        int width = bmpOriginal.getWidth();
        Rect rect = new Rect(0, 0, width, height);
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.0F);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, (Rect)null, rect, paint);
        return bmpGrayscale;
    }


    private static byte[] convertImageX4toX3(byte[] imgX4, int offset, int length) {
        ByteArrayOutputStream imgX3 = new ByteArrayOutputStream(1024);
        byte front = imgX4[offset];
        int sameByteCount = 0;
        int diffByteCount = 1;

        for(int i = 0; i < length; ++i) {
            byte b = imgX4[offset + i];
            if (front == b) {
                ++sameByteCount;
                if (sameByteCount >= 3 && diffByteCount > 1) {
                    imgX3.write(128 + diffByteCount - 1);
                    imgX3.write(imgX4, offset + i - diffByteCount - 1, diffByteCount - 1);
                    diffByteCount = 1;
                }

                if (sameByteCount > 62) {
                    imgX3.write(254);
                    imgX3.write(front);
                    sameByteCount = 1;
                }
            } else {
                ++diffByteCount;
                if (sameByteCount >= 3) {
                    imgX3.write(192 + sameByteCount);
                    imgX3.write(front);
                    --diffByteCount;
                } else if (sameByteCount == 2) {
                    ++diffByteCount;
                }

                if (diffByteCount > 62) {
                    imgX3.write(190);
                    imgX3.write(imgX4, offset + i - diffByteCount + 1, 62);
                    diffByteCount -= 62;
                }

                sameByteCount = 1;
            }

            front = b;
        }

        if (sameByteCount >= 3) {
            imgX3.write(192 + sameByteCount);
            imgX3.write(front);
        } else {
            if (sameByteCount == 2) {
                ++diffByteCount;
            }

            imgX3.write(128 + diffByteCount);
            imgX3.write(imgX4, offset + length - diffByteCount, diffByteCount);
        }

        return imgX3.toByteArray();
    }

    public static Bitmap resizeImage(Bitmap bitmap, int w, int h) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = (float) w / (float) width;
        float scaleHeight = (float) h / (float) height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        return resizedBitmap;
    }

    public static Bitmap pad(Bitmap Src, int padding_x, int padding_y) {
        Bitmap outputimage = Bitmap.createBitmap(Src.getWidth() + padding_x,Src.getHeight() + padding_y, Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(outputimage);
        can.drawARGB(255,255,255,255); //This represents White color
        can.drawBitmap(Src, padding_x, padding_y, null);
        return outputimage;
    }

    private static byte[] convertBMPtoX4image(Bitmap bmp, int ditherType) {
        switch(ditherType) {
        case 0:
            return makeX4imageBlackWhite(bmp);
        case 1:
            return makeX4imageRandomThreshold(bmp);
        case 2:
            return makeX4imageFloydSteinberg(bmp);
        case 3:
            return makeX4imageFloydSteinbergOld(bmp);
        default:
            return null;
        }
    }

    private static byte[] makeX4imageBlackWhite(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int[] pixels = new int[height * width];
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
        int imgRowBytes = width / 8 + (width % 8 == 0 ? 0 : 1);
        byte[] img = new byte[imgRowBytes * height];
        Arrays.fill(img, (byte)0);

        for(int y = 0; y < height; ++y) {
            for(int x = 0; x < width; ++x) {
                int byteCount = x / 8;
                int bitCount = x % 8;
                int currIndex = y * width + x;
                int currPixel = pixels[currIndex];
                int destIndex = y * imgRowBytes + byteCount;
                int color = Color.red(currPixel) + Color.green(currPixel) + Color.blue(currPixel);
                if (color < 702 && currPixel != 0) {
                    img[destIndex] = (byte)(img[destIndex] | 1 << 7 - bitCount);
                }
            }
        }

        return img;
    }

    private static byte[] makeX4imageRandomThreshold(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int[] pixels = new int[height * width];
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
        int imgRowBytes = width / 8 + (width % 8 == 0 ? 0 : 1);
        byte[] img = new byte[imgRowBytes * height];
        Arrays.fill(img, (byte)0);
        Random rand = new Random();
        double[] threshold = new double[]{0.25D, 0.26D, 0.27D, 0.28D, 0.29D, 0.3D, 0.31D, 0.32D, 0.33D, 0.34D, 0.35D, 0.36D, 0.37D, 0.38D, 0.39D, 0.4D, 0.41D, 0.42D, 0.43D, 0.44D, 0.45D, 0.46D, 0.47D, 0.48D, 0.49D, 0.5D, 0.51D, 0.52D, 0.53D, 0.54D, 0.55D, 0.56D, 0.57D, 0.58D, 0.59D, 0.6D, 0.61D, 0.62D, 0.63D, 0.64D, 0.65D, 0.66D, 0.67D, 0.68D, 0.69D};

        for(int y = 0; y < height; ++y) {
            for(int x = 0; x < width; ++x) {
                int byteCount = x / 8;
                int bitCount = x % 8;
                int currIndex = y * width + x;
                int currPixel = pixels[currIndex];
                int destIndex = y * imgRowBytes + byteCount;
                double lum = (double)(((float)Color.red(currPixel) * 0.21F + (float)Color.green(currPixel) * 0.71F + (float)Color.blue(currPixel) * 0.07F) / 255.0F);
                if (lum <= threshold[rand.nextInt(threshold.length)] && currPixel != 0) {
                    img[destIndex] = (byte)(img[destIndex] | 1 << 7 - bitCount);
                }
            }
        }

        return img;
    }

    private static byte[] makeX4imageFloydSteinberg(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int[] pixels = new int[height * width];
        bmp.getPixels(pixels, 0, width, 0, 0, width, height);
        int imgRowBytes = width / 8 + (width % 8 == 0 ? 0 : 1);
        byte[] img = new byte[imgRowBytes * height];
        Arrays.fill(img, (byte)0);
        PrintPicture.C3[] palette = new PrintPicture.C3[]{new PrintPicture.C3(0, 0, 0), new PrintPicture.C3(255, 255, 255)};
        PrintPicture.C3[][] d = new PrintPicture.C3[height][width];

        int y;
        int x;
        for(y = 0; y < height; ++y) {
            for(x = 0; x < width; ++x) {
                d[y][x] = new PrintPicture.C3(bmp.getPixel(x, y));
            }
        }

        for(y = 0; y < height; ++y) {
            for(x = 0; x < width; ++x) {
                int byteCount = x / 8;
                int bitCount = x % 8;
                int currIndex = y * width + x;
                int currPixel = pixels[currIndex];
                int destIndex = y * imgRowBytes + byteCount;
                PrintPicture.C3 oldColor = d[y][x];
                PrintPicture.C3 newColor = findClosestPaletteColor(oldColor, palette);
                if (newColor.toRGB() == -16777216 && currPixel != 0) {
                    img[destIndex] = (byte)(img[destIndex] | 1 << 7 - bitCount);
                }

                PrintPicture.C3 err = oldColor.sub(newColor);
                if (x + 1 < width) {
                    d[y][x + 1] = d[y][x + 1].add(err.mul(0.4375D));
                }

                if (x - 1 >= 0 && y + 1 < height) {
                    d[y + 1][x - 1] = d[y + 1][x - 1].add(err.mul(0.1875D));
                }

                if (y + 1 < height) {
                    d[y + 1][x] = d[y + 1][x].add(err.mul(0.3125D));
                }

                if (x + 1 < width && y + 1 < height) {
                    d[y + 1][x + 1] = d[y + 1][x + 1].add(err.mul(0.0625D));
                }
            }
        }

        return img;
    }

    private static byte[] makeX4imageFloydSteinbergOld(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        Bitmap grayBmp = convertGrayScale(bmp);
        int[] pixels = new int[height * width];
        grayBmp.getPixels(pixels, 0, width, 0, 0, width, height);
        int imgRowBytes = width / 8 + (width % 8 == 0 ? 0 : 1);
        byte[] img = new byte[imgRowBytes * height];
        Arrays.fill(img, (byte)0);

        for(int y = 0; y < height; ++y) {
            for(int x = 0; x < width; ++x) {
                int byteCount = x / 8;
                int bitCount = x % 8;
                int currIndex = y * width + x;
                int currPixel = pixels[currIndex];
                int destIndex = y * imgRowBytes + byteCount;
                int roundColor = Color.blue(currPixel) < 128 ? 0 : 255;
                int errorAmount = Color.blue(currPixel) - roundColor;
                if (roundColor == 0 && currPixel != 0) {
                    img[destIndex] = (byte)(img[destIndex] | 1 << 7 - bitCount);
                }

                if (x + 1 < width && pixels[currIndex + 1] != 0) {
                    pixels[currIndex + 1] += errorAmount * 7 >> 4;
                }

                if (y + 1 != height) {
                    if (x > 0 && pixels[currIndex + width - 1] != 0) {
                        pixels[currIndex + width - 1] += errorAmount * 3 >> 4;
                    }

                    if (pixels[currIndex + width] != 0) {
                        pixels[currIndex + width] += errorAmount * 5 >> 4;
                    }

                    if (x + 1 < width && pixels[currIndex + width + 1] != 0) {
                        pixels[currIndex + width + 1] += errorAmount >> 4;
                    }
                }
            }
        }

        return img;
    }


    /**
     * 打印位图函数
     * 此函数是将一行作为一个图片打印，这样处理不容易出错
     *
     * @param mBitmap
     * @param nWidth
     * @param nMode
     * @return
     */
    public static byte[] POS_PrintBMP(Bitmap mBitmap, int nWidth, int nMode, int leftPadding) {
        // 先转黑白，再调用函数缩放位图
        int width = ((nWidth + 7) / 8) * 8;
        int height = mBitmap.getHeight() * width / mBitmap.getWidth();
        height = ((height + 7) / 8) * 8;
        int left = leftPadding == 0 ? 0 : ((leftPadding+7) / 8) * 8;

        Bitmap rszBitmap = mBitmap;
        if (mBitmap.getWidth() != width) {
            rszBitmap = Bitmap.createScaledBitmap(mBitmap, width, height, true);
        }

        Bitmap grayBitmap = toGrayscale(rszBitmap);
        if(left>0){
            grayBitmap = pad(grayBitmap,left,0);
        }

        byte[] dithered = thresholdToBWPic(grayBitmap);

        byte[] data = eachLinePixToCmd(dithered, width+left, nMode);

        return data;
    }

    /**
     * 使用下传位图打印图片
     * 先收完再打印
     *
     * @param bmp
     * @return
     */
    public static byte[] Print_1D2A(Bitmap bmp) {

			/*
			 * 使用下传位图打印图片
			 * 先收完再打印
			 */
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        byte data[] = new byte[1024 * 10];
        data[0] = 0x1D;
        data[1] = 0x2A;
        data[2] = (byte) ((width - 1) / 8 + 1);
        data[3] = (byte) ((height - 1) / 8 + 1);
        byte k = 0;
        int position = 4;
        int i;
        int j;
        byte temp = 0;
        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                if (bmp.getPixel(i, j) != -1) {
                    temp |= (0x80 >> k);
                } // end if
                k++;
                if (k == 8) {
                    data[position++] = temp;
                    temp = 0;
                    k = 0;
                } // end if k
            }// end for j
            if (k % 8 != 0) {
                data[position++] = temp;
                temp = 0;
                k = 0;
            }

        }

        if (width % 8 != 0) {
            i = height / 8;
            if (height % 8 != 0) i++;
            j = 8 - (width % 8);
            for (k = 0; k < i * j; k++) {
                data[position++] = 0;
            }
        }
        return data;
    }

    public static Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    public static byte[] thresholdToBWPic(Bitmap mBitmap) {
        int[] pixels = new int[mBitmap.getWidth() * mBitmap.getHeight()];
        byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight()];
        mBitmap.getPixels(pixels, 0, mBitmap.getWidth(), 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        format_K_threshold(pixels, mBitmap.getWidth(), mBitmap.getHeight(), data);
        return data;
    }

    private static void format_K_threshold(int[] orgpixels, int xsize, int ysize, byte[] despixels) {
        int graytotal = 0;
        boolean grayave = true;
        int k = 0;

        int i;
        int j;
        int gray;
        for (i = 0; i < ysize; ++i) {
            for (j = 0; j < xsize; ++j) {
                gray = orgpixels[k] & 255;
                graytotal += gray;
                ++k;
            }
        }

        int var10 = graytotal / ysize / xsize;
        k = 0;

        for (i = 0; i < ysize; ++i) {
            for (j = 0; j < xsize; ++j) {
                gray = orgpixels[k] & 255;
                if (gray > var10) {
                    despixels[k] = 0;
                } else {
                    despixels[k] = 1;
                }

                ++k;
            }
        }

    }

    public static byte[] eachLinePixToCmd(byte[] src, int nWidth, int nMode) {
        int nHeight = src.length / nWidth;
        int nBytesPerLine = nWidth / 8;
        byte[] data = new byte[nHeight * (8 + nBytesPerLine)];
        boolean offset = false;
        int k = 0;

        for (int i = 0; i < nHeight; ++i) {
            int var10 = i * (8 + nBytesPerLine);
            //GS v 0 m xL xH yL yH d1....dk 打印光栅位图
            data[var10 + 0] = 29;//GS
            data[var10 + 1] = 118;//v
            data[var10 + 2] = 48;//0
            data[var10 + 3] = (byte) (nMode & 1);
            data[var10 + 4] = (byte) (nBytesPerLine % 256);//xL
            data[var10 + 5] = (byte) (nBytesPerLine / 256);//xH
            data[var10 + 6] = 1;//yL
            data[var10 + 7] = 0;//yH

            for (int j = 0; j < nBytesPerLine; ++j) {
                data[var10 + 8 + j] = (byte) (p0[src[k]] + p1[src[k + 1]] + p2[src[k + 2]] + p3[src[k + 3]] + p4[src[k + 4]] + p5[src[k + 5]] + p6[src[k + 6]] + src[k + 7]);
                k += 8;
            }
        }

        return data;
    }

    public static byte[] pixToTscCmd(byte[] src) {
        byte[] data = new byte[src.length / 8];
        int k = 0;

        for (int j = 0; k < data.length; ++k) {
            byte temp = (byte) (p0[src[j]] + p1[src[j + 1]] + p2[src[j + 2]] + p3[src[j + 3]] + p4[src[j + 4]] + p5[src[j + 5]] + p6[src[j + 6]] + src[j + 7]);
            data[k] = (byte) (~temp);
            j += 8;
        }

        return data;
    }
    public static byte[] pixToEscRastBitImageCmd(byte[] src) {
        byte[] data = new byte[src.length / 8];
        int i = 0;

        for (int k = 0; i < data.length; ++i) {
            data[i] = (byte) (p0[src[k]] + p1[src[k + 1]] + p2[src[k + 2]] + p3[src[k + 3]] + p4[src[k + 4]] + p5[src[k + 5]] + p6[src[k + 6]] + src[k + 7]);
            k += 8;
        }

        return data;
    }
    public static byte[] pixToEscNvBitImageCmd(byte[] src, int width, int height) {
        byte[] data = new byte[src.length / 8 + 4];
        data[0] = (byte) (width / 8 % 256);
        data[1] = (byte) (width / 8 / 256);
        data[2] = (byte) (height / 8 % 256);
        data[3] = (byte) (height / 8 / 256);
        boolean k = false;

        for (int i = 0; i < width; ++i) {
            int var7 = 0;

            for (int j = 0; j < height / 8; ++j) {
                data[4 + j + i * height / 8] = (byte) (p0[src[i + var7]] + p1[src[i + var7 + 1 * width]] + p2[src[i + var7 + 2 * width]] + p3[src[i + var7 + 3 * width]] + p4[src[i + var7 + 4 * width]] + p5[src[i + var7 + 5 * width]] + p6[src[i + var7 + 6 * width]] + src[i + var7 + 7 * width]);
                var7 += 8 * width;
            }
        }

        return data;
    }
    public static byte[] bitmapToBWPix(Bitmap mBitmap) {
        int[] pixels = new int[mBitmap.getWidth() * mBitmap.getHeight()];
        byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight()];
        Bitmap grayBitmap = toGrayscale(mBitmap);
        grayBitmap.getPixels(pixels, 0, mBitmap.getWidth(), 0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        format_K_dither16x16(pixels, grayBitmap.getWidth(), grayBitmap.getHeight(), data);
        return data;
    }

    private static void format_K_dither16x16(int[] orgpixels, int xsize, int ysize, byte[] despixels) {
        int k = 0;

        for (int y = 0; y < ysize; ++y) {
            for (int x = 0; x < xsize; ++x) {
                if ((orgpixels[k] & 255) > Floyd16x16[x & 15][y & 15]) {
                    despixels[k] = 0;
                } else {
                    despixels[k] = 1;
                }

                ++k;
            }
        }

    }

    public static byte[] drawBitmap(int x, int y, Bitmap bmp) {
        return drawImage(x, y, bmp, 0);
    }

    public static byte[] drawColorBitmap(int x, int y, Bitmap bmp) {
        return drawImage(x, y, bmp, 2);
    }


    private static byte[] drawImage(int x, int y, Bitmap bmp, int ditherType) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int imgRowBytes = width / 8 + (width % 8 == 0 ? 0 : 1);
        byte xl = (byte)(x & 255);
        byte xh = (byte)(x >> 8 & 255);
        byte yl = (byte)(y & 255);
        byte yh = (byte)(y >> 8 & 255);
        byte wl = (byte)(width & 255);
        byte wh = (byte)(width >> 8 & 255);
        byte hl = (byte)(height & 255);
        byte hh = (byte)(height >> 8 & 255);
        byte[] cmd_ESCW = new byte[]{27, 87, xl, xh, yl, yh, wl, wh, hl, hh};
        byte[] cmd_ESCX4 = new byte[]{27, 88, 52, (byte)imgRowBytes, (byte)height};
        byte[] img = convertBMPtoX4image(bmp, ditherType);
        ByteBuffer buffer = ByteBuffer.allocate(cmd_ESCW.length + cmd_ESCX4.length + img.length);
        buffer.put(cmd_ESCW);
        buffer.put(cmd_ESCX4);
        buffer.put(img);
        return buffer.array();
    }

    public static byte[] drawBox(int x, int y, int width, int height, int thickness) {
        if (width <= 0 && height <= 0) {
            Log.e("WoosimImage", "Invalid parameters on width and/or height.");
            return null;
        } else {
            byte xl = (byte)(x & 255);
            byte xh = (byte)(x >> 8 & 255);
            byte yl = (byte)(y & 255);
            byte yh = (byte)(y >> 8 & 255);
            byte wl = (byte)(width & 255);
            byte wh = (byte)(width >> 8 & 255);
            byte hl = (byte)(height & 255);
            byte hh = (byte)(height >> 8 & 255);
            return new byte[]{27, 79, xl, xh, yl, yh, 29, 105, wl, wh, hl, hh, (byte)thickness};
        }
    }

    public static byte[] drawLine(int x1, int y1, int x2, int y2, int thickness) {
        if (x1 >= 0 && y1 >= 0 && x2 >= 0 && y2 >= 0 && thickness > 0) {
            if (thickness > 255) {
                thickness = 255;
            }

            byte x1l = (byte)(x1 & 255);
            byte x1h = (byte)(x1 >> 8 & 255);
            byte y1l = (byte)(y1 & 255);
            byte y1h = (byte)(y1 >> 8 & 255);
            byte x2l = (byte)(x2 & 255);
            byte x2h = (byte)(x2 >> 8 & 255);
            byte y2l = (byte)(y2 & 255);
            byte y2h = (byte)(y2 >> 8 & 255);
            byte thick = (byte)(thickness & 255);
            return new byte[]{27, 103, 49, x1l, x1h, y1l, y1h, x2l, x2h, y2l, y2h, thick};
        } else {
            Log.e("WoosimImage", "Invalid parameter.");
            return null;
        }
    }

    public static byte[] drawEllipse(int x, int y, int radiusW, int radiusH, int thickness) {
        if (x >= 0 && y >= 0 && radiusW > 0 && radiusH > 0 && thickness > 0) {
            if (thickness > 255) {
                thickness = 255;
            }

            byte xl = (byte)(x & 255);
            byte xh = (byte)(x >> 8 & 255);
            byte yl = (byte)(y & 255);
            byte yh = (byte)(y >> 8 & 255);
            byte wl = (byte)(radiusW & 255);
            byte wh = (byte)(radiusW >> 8 & 255);
            byte hl = (byte)(radiusH & 255);
            byte hh = (byte)(radiusH >> 8 & 255);
            byte thick = (byte)(thickness & 255);
            return new byte[]{27, 103, 50, xl, xh, yl, yh, wl, wh, hl, hh, thick};
        } else {
            Log.e("WoosimImage", "Invalid parameter.");
            return null;
        }
    }

    public static byte[] printStdModeBitmap(Bitmap bmp) {
        return printStdModeImage(bmp, 0);
    }

    public static byte[] printStdModeColorBitmap(Bitmap bmp) {
        return printStdModeImage(bmp, 2);
    }

    private static byte[] printStdModeImage(Bitmap bmp, int ditherType) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int imgRowBytes = width / 8 + (width % 8 == 0 ? 0 : 1);
        byte[] img = convertBMPtoX4image(bmp, ditherType);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
        byte[] cmd_ESCX4;
        if (height > 255) {
            cmd_ESCX4 = new byte[]{27, 51, 0};
            byteStream.write(cmd_ESCX4, 0, cmd_ESCX4.length);
        }

        int page;
        for(page = 0; page < height / 255; ++page) {
            cmd_ESCX4 = new byte[]{27, 88, 52, (byte)imgRowBytes, -1};
            byteStream.write(cmd_ESCX4, 0, cmd_ESCX4.length);
            byteStream.write(img, imgRowBytes * 255 * page, imgRowBytes * 255);
            byteStream.write(10);
        }

        if (height > 255) {
            cmd_ESCX4 = new byte[]{27, 50};
            byteStream.write(cmd_ESCX4, 0, cmd_ESCX4.length);
        }

        if (height % 255 != 0) {
            cmd_ESCX4 = new byte[]{27, 88, 52, (byte)imgRowBytes, (byte)(height % 255)};
            byteStream.write(cmd_ESCX4, 0, cmd_ESCX4.length);
            byteStream.write(img, imgRowBytes * 255 * page, imgRowBytes * (height % 255));
            byteStream.write(10);
        }

        return byteStream.toByteArray();
    }

    public static byte[] printARGBbitmap(int x, int y, int width, int height, Bitmap bmp) {
        Bitmap opaqueBmp = removeAlphaValue(bmp);
        byte[] result = printRGBbitmap(x, y, width, height, opaqueBmp);
        opaqueBmp.recycle();
        return result;
    }

    public static byte[] printBitmap(int x, int y, int width, int height, Bitmap bmp) {
        return printImageNew(x, y, width, height, bmp, false, 0, false);
    }

    public static byte[] printRGBbitmap(int x, int y, int width, int height, Bitmap bmp) {
        return printBitmap(x, y, width, height, bmp);
    }

    public static byte[] bmp2PrintableImage(int x, int y, int width, int height, Bitmap bmp) {
        return printRGBbitmap(x, y, width, height, bmp);
    }

    public static byte[] fastPrintBitmap(int x, int y, int width, int height, Bitmap bmp) {
        if (width <= 0) {
            width = bmp.getWidth();
        }

        if (height <= 0) {
            height = bmp.getHeight();
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
        byte xl = (byte)(x & 255);
        byte xh = (byte)(x >> 8 & 255);
        byte yl = (byte)(y & 255);
        byte yh = (byte)(y >> 8 & 255);
        byte wl = (byte)(width & 255);
        byte wh = (byte)(width >> 8 & 255);
        byte[] img = convertBMPtoX4image(bmp, 0);
        int bmpW = bmp.getWidth();
        int bmpH = bmp.getHeight();
        int imgRowBytes = bmpW / 8 + (bmpW % 8 == 0 ? 0 : 1);
        boolean startBand = true;
        if (bmpH > height) {
            bmpH = height;
        }

        byteStream.write(24);

        int page;
        byte[] cmd_ESCW;
        byte[] cmd_ESCX4;
        for(page = 0; page < bmpH / 255; ++page) {
            cmd_ESCW = new byte[]{27, 87, xl, xh, 0, 0, wl, wh, -1, 0};
            if (startBand) {
                cmd_ESCW[4] = yl;
                cmd_ESCW[5] = yh;
                startBand = false;
            }

            byteStream.write(cmd_ESCW, 0, cmd_ESCW.length);
            cmd_ESCX4 = new byte[]{27, 88, 52, (byte)imgRowBytes, -1};
            byteStream.write(cmd_ESCX4, 0, cmd_ESCX4.length);
            byteStream.write(img, imgRowBytes * 255 * page, imgRowBytes * 255);
            byteStream.write(cmd_ESCFF, 0, cmd_ESCFF.length);
            byteStream.write(24);
        }

        if (bmpH % 255 != 0) {
            cmd_ESCW = new byte[]{27, 87, xl, xh, 0, 0, wl, wh, (byte)(bmpH % 255), 0};
            if (startBand) {
                cmd_ESCW[4] = yl;
                cmd_ESCW[5] = yh;
            }

            byteStream.write(cmd_ESCW, 0, cmd_ESCW.length);
            cmd_ESCX4 = new byte[]{27, 88, 52, (byte)imgRowBytes, (byte)(bmpH % 255)};
            byteStream.write(cmd_ESCX4, 0, cmd_ESCX4.length);
            byteStream.write(img, imgRowBytes * 255 * page, imgRowBytes * (bmpH % 255));
            byteStream.write(cmd_ESCFF, 0, cmd_ESCFF.length);
            byteStream.write(24);
        }

        return byteStream.toByteArray();
    }

    private static Bitmap removeAlphaValue(Bitmap bmp) {
        Bitmap clone = bmp.copy(bmp.getConfig(), true);
        int w = clone.getWidth();
        int h = clone.getHeight();

        for(int i = 0; i < w; ++i) {
            for(int j = 0; j < h; ++j) {
                if (clone.getPixel(i, j) == 0) {
                    clone.setPixel(i, j, -1);
                }
            }
        }

        return clone;
    }

    public static byte[] putARGBbitmap(int x, int y, Bitmap bmp) {
        Bitmap opaqueBmp = removeAlphaValue(bmp);
        byte[] result = putRGBbitmap(x, y, opaqueBmp);
        opaqueBmp.recycle();
        return result;
    }

    public static byte[] putRGBbitmap(int x, int y, Bitmap bmp) {
        return drawBitmap(x, y, bmp);
    }

    public static byte[] fastPrintARGBbitmap(int x, int y, int width, int height, Bitmap bmp) {
        Bitmap opaqueBmp = removeAlphaValue(bmp);
        byte[] result = fastPrintRGBbitmap(x, y, width, height, opaqueBmp);
        opaqueBmp.recycle();
        return result;
    }

    public static byte[] fastPrintRGBbitmap(int x, int y, int width, int height, Bitmap bmp) {
        return fastPrintBitmap(x, y, width, height, bmp);
    }

}
