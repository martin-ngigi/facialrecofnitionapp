package com.example.facialrecofnitionapp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Face_Recognition {
    // define interpreter
    private Interpreter interpreter;
    //define model size
    private int INPUT_SIZE;
    // define height and width of the frame
    private int height = 0;
    private int width = 0;
    //define gpuDelegate... This is used to run model using GPU
    private GpuDelegate gpuDelegate = null;
    //define cascadeClassifier
    private CascadeClassifier cascadeClassifier;
    //create constructor
    Face_Recognition(AssetManager assetManager, Context context, String modelPath, int input_size) throws IOException{
        //call this class in Camera Activity

        //get input size
        INPUT_SIZE = input_size;
        //set GPU for the Interpreter
        Interpreter.Options options = new Interpreter.Options();
        gpuDelegate = new GpuDelegate();
        //if you are using EFficient you can use GPU as it doesnt support GPU
        //Still without GPU frame rate is OK

        /**1. But if you are using MobileNet model**/
        //options.addDelegate(gpuDelegate);

        /**2. But if you are using Efficient model*/
        //Before loading, add number of threads
        options.setNumThreads(4); // chose number of threads according to your phone
        //If you want to increase frame-rate use maximum frame rate that your phone can support
        //If your phone slows down due to this app, reduce the number of threads
        //load model
        interpreter = new Interpreter(loadModel(assetManager, modelPath), options);
        //when model is loaded successfully
        Toast.makeText(context, "Model loaded successfully", Toast.LENGTH_SHORT).show();
        Log.d("Face_Recognition", "Face_Recognition: Model loaded successfully");

        //load haar cascade model
        try{
            //define input stream to read haar cascade file
            InputStream inputStream = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            //create a new folder to save classifier
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            //create a new cascade file in that folder
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt");
            //define output stream to save haarcascade_frontalface_alt in mCascadeFile
            FileOutputStream outputStream = new FileOutputStream(mCascadeFile);
            //create empty byte buffer to store byte
            byte[] buffer = new byte[4096];
            int byteRead;
            //read byte in loop
            //when it reads -1, that means no data to read
            while ((byteRead = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, byteRead);
            }
            //when reading file is complete do the following:
            inputStream.close();
            outputStream.close();

            //load cascade classifier
            //                                       Path of saved file
            cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());

            //if classifier is successfully loaded
            Log.d("Face_Recognition", "Face_Recognition: classifier loaded successfully ");
            Toast.makeText(context, "classifier loaded successfully", Toast.LENGTH_SHORT).show();
        }
        catch (IOException e){
            e.printStackTrace();
            Log.d("Face_Recognition", "Face_Recognition Error: Error in loading classifier ");
            Toast.makeText(context, "Error in loading classifier \n"+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    //create a new function with input Mat and output Mat
    public Mat recognitionImage(Mat mat_image){
        //call this method in onCameraFrame of CameraACtivity

        //Before doing process rotate mat_image by 90 degree
        //As its not properly aligned
        Core.flip(mat_image.t(), mat_image, 1);

        //do all processing here
        //convert mat_image to gray_scale
        Mat grayScaleImage = new Mat();
        //               input      output          type
        Imgproc.cvtColor(mat_image, grayScaleImage, Imgproc.COLOR_RGBA2GRAY);
        //define height and width
        height = grayScaleImage.height();
        width = grayScaleImage.width();

        //define min height and width of face in frame
        //below this height and width will be neglected
        int absoluteFaceSize = (int) (height*0.1);
        MatOfRect faces = new MatOfRect();
        //This will store all faces
        //check if cascadeClassifier is loaded or not
        if (cascadeClassifier != null){
            //detect face in frame
            //                                  input         //output     //Scale of frames
            cascadeClassifier.detectMultiScale(grayScaleImage, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize, absoluteFaceSize), new Size());
                    //min size of face
        }

        //convert face to array
        Rect[] faceArray = faces.toArray();
        //loop through each face
        for (int i =0; i<faceArray.length; i++){
            //draw rec around faces
    //                  //input/output,  starting point,    endpoint,            color   (R, G, B,Alpha), Thickness
            Imgproc.rectangle(mat_image, faceArray[i].tl(), faceArray[i].br(), new Scalar(0,255,0,255), 2);

            //                  Starting coordinates of x,   Starting coordinates of y,
            Rect roi = new Rect((int)faceArray[i].tl().x, (int)faceArray[i].tl().y,
                    ((int)faceArray[i].br().x) - ((int)faceArray[i].tl().x),
                    ((int)faceArray[i].br().y) - ((int)faceArray[i].tl().y));
            //roi(region Of Interest) is used to crop faces from image
            Mat cropped_rgb = new Mat(mat_image, roi);

            //convert cropped_rgb to bitmap
            Bitmap bitmap = null;
            bitmap=Bitmap.createBitmap(cropped_rgb.cols(), cropped_rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgb, bitmap);
            //scale bitmap to model input size 96
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
            
            //convert scaledBitmap to byteBuffer
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(scaledBitmap);
            //create output
            float[][] face_value = new float[1][1];
            interpreter.run(byteBuffer, face_value);
            //ig=f you want to see face_value
            Log.d("Face_Recognition", "Face_Recognition Output: "+ Array.get(Array.get(face_value, 0), 0));
            
            //read face value
            float read_face = (float) Array.get(Array.get(face_value, 0), 0);
            //invoke method whose input is read_face and output is name
            String face_name = getFaceName(read_face);
            //put text on frame
            //        //input/output            text
            Imgproc.putText(mat_image, ""+face_name,
                    new Point((int)faceArray[i].tl().x+10, (int)faceArray[i].tl().y+20),
                    1, 1.5, new Scalar(255,255,255.150), 2);
                    //sizw                  color ( R, G, B, Alpha)         thickness
        }


        //before returning rotate it back by -90 degree
        Core.flip(mat_image.t(), mat_image, 0);

        return mat_image;
    }

    private String getFaceName(float read_face) {
        String val = "";
        if (read_face >=0 & read_face<0.5){
            val ="Courtney Cox";
        }
        else if (read_face >=0.5 & read_face<1.5){
            val ="Anord Schwarzeneggar";
        }
        else if (read_face >=1.5 & read_face<2.5){
            val ="Bhuvan Bam";
        }
        else if (read_face >=2.5 & read_face<3.5){
            val ="Hardik Pandya";
        }
        else if (read_face >=3.5 & read_face<4.5){
            val ="David Schwimmer";
        }
        else if (read_face >=4.5 & read_face<5.5){
            val ="Matt LeBlanc";
        }
        else if (read_face >=5.5 & read_face<6.5){
            val ="Simon Helberg";
        }
        else if (read_face >=6.5 & read_face<7.5){
            val ="Scarlett Johnson";
        }
        else if (read_face >=7.5 & read_face<8.5){
            val ="Pankaj Tripathi";
        }
        else if (read_face >=8.5 & read_face<9.5){
            val ="Mathew Perry";
        }
        else if (read_face >=9.5 & read_face<10.5){
            val ="Sylvester Stallone";
        }
        else if (read_face >=10.5 & read_face<11.5){
            val ="Messi";
        }
        else if (read_face >=11.5 & read_face<12.5){
            val ="Jim Parsons";
        }
        else if (read_face >=12.5 & read_face<13.5){
            val ="Not in Dataset";
        }
        else if (read_face >=13.5 & read_face<14.5){
            val ="Lisa Kudrow";
        }
        else if (read_face >=14.5 & read_face<15.5){
            val ="Mohamed Ali";
        }
        else if (read_face >=15.5 & read_face<16.5){
            val ="Brad Pit";
        }
        else if (read_face >=16.5 & read_face<17.5){
            val ="Ronaldo";
        }
        else if (read_face >=17.5 & read_face<18.5){
            val ="Virat Kohli";
        }
        else if (read_face >=18.5 & read_face<19.5){
            val ="Angelina Jolie";
        }
        else if (read_face >=19.5 & read_face<20.5){
            val ="KunalNayya";
        }
        else if (read_face >=20.5 & read_face<21.5){
            val ="Monaje Bajpayee";
        }
        else if (read_face >=21.5 & read_face<22.5){
            val ="Sachin Tundulka";
        }
        else if (read_face >=22.5 & read_face<23.5){
            val ="Jennifer Aniston";
        }
        else if (read_face >=23.5 & read_face<24.5){
            val ="Dhoni";
        }
        else if (read_face >=24.5 & read_face<25.5){
            val ="Pewdiepie";
        }
        else if (read_face >=25.5 & read_face<26.5){
            val ="Aishwarya Rai";
        }
        else if (read_face >=26.5 & read_face<27.5){
            val ="Johnny Galeck";
        }
        else if (read_face >=27.5 & read_face<28.5){
            val ="Rohit Sharma";
        }
        else if (read_face >=28.5 & read_face<29.5){
            val ="Suresh Raina";
        }


        return val;

    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {

        //Define ByteBuffer
        ByteBuffer byteBuffer;
        //define input size
        int input_size=INPUT_SIZE;

        //Multiply by 4 if input of model is float
        //Multiply by 3 if input is RGB
        byteBuffer = ByteBuffer.allocateDirect(4*1*input_size*input_size*3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValue = new int[input_size*input_size];
        scaledBitmap.getPixels(intValue, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        int pixels = 0;
        for (int i=0; i<input_size; i++){
            for (int j=0; j<input_size; j++){
                //each pixel value
                final int val = intValue[pixels++];
                //put this pixel value in bytebuffer
                //VERY IMPORTANT AS IT IS PLACING RGB TO MSB TO LSB
                byteBuffer.putFloat((((val>>16)&0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)&0xFF))/255.0f);
                byteBuffer.putFloat(((val & 0xFF))/255.0f);
                //scaling pixels from 0-255 to 0-1
            }
        }
        return byteBuffer;

    }

    //this function will load model
    private MappedByteBuffer loadModel(AssetManager assetManager, String modelPath) throws IOException {
        //Give description of modelPath
        AssetFileDescriptor assetFileDescriptor = assetManager.openFd(modelPath);

        //create a inputStream to read model path
        FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset=assetFileDescriptor.getStartOffset();
        long declaredLength = assetFileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);


    }


}
