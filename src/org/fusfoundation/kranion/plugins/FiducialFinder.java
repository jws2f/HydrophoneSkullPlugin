/*
 * The MIT License
 *
 * Copyright 2017 Focused Ultrasound Foundation.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.fusfoundation.kranion.plugins;

import org.fusfoundation.kranion.ProgressListener;
import org.fusfoundation.kranion.model.image.ImageVolume4D;
import org.lwjgl.util.vector.Vector3f;
import Jama.*;

/**
 *
 * @author john
 */
public class FiducialFinder {
    private double xpos[] = new double[4], xsum[] = new double[4];
    private double ypos[] = new double[4], ysum[] = new double[4];
    private double zpos[] = new double[4], zsum[] = new double[4];
    
    public void FiducialFinder() {
        reset();
    }
    
    public Vector3f getFiducialLocation(int i) {
        
        if (i < 0 || i > 3) return null;
        
        Vector3f result = new Vector3f((float)xpos[i], (float)ypos[i], (float)zpos[i]);
        
        return result;
    }
    
    private void reset() {
        for (int i=0; i<4; i++) {
            xpos[i] = ypos[i] = zpos[i] = 0f;
            xsum[i] = ysum[i] = zsum[i] = 0f;
        }
    }
    
    public boolean find(ImageVolume4D image, ProgressListener listener) {
        reset();
        
        int fiducial = 0;
        
        int nDims = image.getDimensionality();
        
        int pixelCounts[] = new int[4];
        
        int xsize = image.getDimension(0).getSize();
        int ysize = image.getDimension(1).getSize();
        int zsize = image.getDimension(2).getSize();
        
        float xres = image.getDimension(0).getSampleWidth(0);
        float yres = image.getDimension(1).getSampleWidth(0);
        float zres = image.getDimension(2).getSampleWidth(0);
        
        short data[] = (short[])image.getData();
        
        for (int x=0; x<xsize; x++) {
            if (listener != null) {
                listener.percentDone("Finding fiducials", Math.round((float)x/(float)xsize*100f));
            }
            for (int y=0; y<ysize; y++) {
                for (int z=0; z<zsize; z++) {
                    
                    float value = data[image.getVoxelOffset(x, y, z)] & 0xff - 1024;
                    
                    fiducial = -1;
                    
                    if (value > 3300 ) {
                        
                        float tx = (x - xsize/2f);
                        float tz = -(z - zsize/2f);
                        
                        if (tx < 0 && tz < 0) {
                            if (Math.abs(-tx - -tz) < 30) {
                                fiducial = 0;
                            }
                        }
                        else if (tx > 0 && tz < 0) {
                            if (Math.abs(tx - -tz) < 30) {
                                fiducial = 1;
                            }
                        }
                        else if (tx < 0 && tz > 0) {
                            if (Math.abs(-tx - tz) < 30) {
                                fiducial = 2;
                            }
                        }
                        else if (tx > 0 && tz > 0){
                            if (Math.abs(tx - tz) < 30) {
                                fiducial = 3;
                            }
                        }
                        
                        if (fiducial > -1) {
                            pixelCounts[fiducial]++;
                            
                            value = 1f;
                        
                            xpos[fiducial] += value*xres*x; // weighted position
                            ypos[fiducial] += value*yres*y; // weighted position
                            zpos[fiducial] += value*zres*z; // weighted position

                            xsum[fiducial] += value;
                            ysum[fiducial] += value;
                            zsum[fiducial] += value;
                        }
                    }
                }
            }
        }
        
        Vector3f centroid = new Vector3f();
        
        for (int i=0; i<4; i++) {
            xpos[i] /= xsum[i];
            ypos[i] /= ysum[i];
            zpos[i] /= zsum[i];
            
            centroid.x += xpos[i];
            centroid.y += ypos[i];
            centroid.z += zpos[i];
            
            System.out.println("Fiducial " + i + ": " + xpos[i] + ", " + ypos[i] + ", " + zpos[i] + "   [" + pixelCounts[i] + "]");
            
            if (pixelCounts[i] < 50) {
                System.out.println("Fiducial " + i + " not found.");
                return false;
            }
        }
        
        centroid.x /= 4f;
        centroid.y /= 4f;
        centroid.z /= 4f;
        
        System.out.println("Fiducial centroid: " + centroid);
               
        double[][] knowns = { {-80.0, -80.0, 0.0}, {80.0, -80.0, 0.0}, {-80.0, 80.0, 0.0}, {80.0, 80.0, 0.0} };
        
        // build correlation matrix
        Jama.Matrix A = new Jama.Matrix(3, 3, 0.0);
        
        for (int i=0; i<4; i++) {
            double[][] sourcePoints = new double[3][1];
            sourcePoints[0][0] = (xpos[i] - centroid.x);
            sourcePoints[1][0] = (ypos[i] - centroid.y);
            sourcePoints[2][0] = (zpos[i] - centroid.z);
            Jama.Matrix m = new Jama.Matrix(sourcePoints);
            
            double[][] targetPoints = new double[3][1];
            targetPoints[0][0] = knowns[i][0];
            targetPoints[1][0] = knowns[i][1];
            targetPoints[2][0] = knowns[i][2];
            Jama.Matrix d = new Jama.Matrix(targetPoints);
            
            A.plusEquals(m.times(d.transpose()));
            
            System.out.println("i="+i);
            A.print(3, 3);
        }
        
        A.print(3, 3);
        
        Jama.SingularValueDecomposition svd = new Jama.SingularValueDecomposition(A);

//      Calculate rotation        
        Jama.Matrix R = svd.getV().times(svd.getU().transpose());
        
        R.print(3, 3);
        
        double det = R.det();
        System.out.println("R.det() = " + det);
        
//        if (det<0) {
//            R.set(2, 0, -R.get(2, 0));
//            R.set(2, 1, -R.get(2, 1));
//            R.set(2, 2, -R.get(2, 2));
//        }
                
//      Calculate translation

        // mbar is the centroid of fiducial positions found in image space
        // - image volume origin is the center of the volume
        Jama.Matrix mbar = new Jama.Matrix(3, 1);
        mbar.set(0, 0, xsize*xres/2f - centroid.x);
        mbar.set(1, 0, ysize*yres/2f - centroid.y);
        mbar.set(2, 0, zsize*zres/2f - centroid.z);
        
        // dbar is the centroid of the known fiducial positions in transducer coordinates
        Jama.Matrix dbar = new Jama.Matrix(3, 1);
        dbar.set(0, 0, 0.0);
        dbar.set(1, 0, 0.0);
        
        // Transducer mounting ledge is at z=161mm
        // skull mounting frame is 12.7mm thick with upper surface at z=161mm
        // distance from the bottom skull mounting frame surface to bottom of fiducial well is 3.9mm
        // tantalum bb (2mm diam) center sits 1.17mm above the bottom of the well
        // (fidiucial well is 2.5mm diam with conical bottom having angle of 118 degrees)
        dbar.set(2, 0, -3.37); // 150 - (161 - 12.7 + 3.9 + 1.17) = -3.37

                        
        Jama.Matrix T = (dbar.minus(R.times(mbar)));
        System.out.println("Translation = ");
        T.print(3, 1);
        

        // check for answer, should get close to knowns
        for (int i=0; i<4; i++) {
            System.out.println("Fiducial SVD checks:");
            double[][] sourcePoints = new double[3][1];
            sourcePoints[0][0] = xpos[i] - centroid.x;
            sourcePoints[1][0] = ypos[i] - centroid.y;
            sourcePoints[2][0] = zpos[i] - centroid.z;
            Jama.Matrix m = new Jama.Matrix(sourcePoints);
            
            R.times(m).print(3, 1);
        }
       
        setImageTransformation(image, R, T);
        
        if (listener != null) {
            listener.percentDone("Ready", -1);
            
        }
        
        return true;
       
    }
    
    public void setImageTransformation(ImageVolume4D image, Jama.Matrix R, Jama.Matrix T) {
        
        int xsize = image.getDimension(0).getSize();
        int ysize = image.getDimension(1).getSize();
        int zsize = image.getDimension(2).getSize();
        
        float xres = image.getDimension(0).getSampleWidth(0);
        float yres = image.getDimension(1).getSampleWidth(0);
        float zres = image.getDimension(2).getSampleWidth(0);

        double [] rows = R.getRowPackedCopy();
        
        Vector3f xvec = new Vector3f((float)rows[0], (float)rows[1], (float)rows[2]);
        Vector3f yvec = new Vector3f((float)rows[3], (float)rows[4], (float)rows[5]);
        Vector3f zvec = new Vector3f((float)rows[6], (float)rows[7], (float)rows[8]);
//        Vector3f xvec = new Vector3f((float)rows[0], (float)rows[3], -(float)rows[6]);
//        Vector3f yvec = new Vector3f((float)rows[1], (float)rows[4], -(float)rows[7]);
//        Vector3f zvec = new Vector3f((float)rows[2], (float)rows[5], -(float)rows[8]);
        
        System.out.println(xvec);
        System.out.println(yvec);
        
        float[] imageOrientation = new float[6];
        
        imageOrientation[0] = xvec.x;
        imageOrientation[1] = xvec.y;
        imageOrientation[2] = xvec.z;
        
        imageOrientation[3] = yvec.x;
        imageOrientation[4] = yvec.y;
        imageOrientation[5] = yvec.z;
        
        image.setAttribute("ImageOrientation", imageOrientation);
        
        rows = T.getColumnPackedCopy();
        rows = T.getRowPackedCopy();
        
        
        float[] imagePosition = new float[3];
        imagePosition[0] = (float)(rows[0]);// + xsize*xres/2f;
        imagePosition[1] = (float)(rows[1]);// + ysize*yres/2f;
        imagePosition[2] = -(float)(rows[2]);// - zsize*zres/2f;
        
//        image.setAttribute("ImagePosition", imagePosition);
        image.setAttribute("ImagePosition", new float[3]);
        
//        image.setAttribute("ImageTranslation", new Vector3f(0, 0, 0));
        image.setAttribute("ImageTranslation", new Vector3f(imagePosition[0], imagePosition[1], imagePosition[2]));
        
 
    }
    
}
