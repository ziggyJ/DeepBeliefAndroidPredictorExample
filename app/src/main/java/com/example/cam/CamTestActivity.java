package com.example.cam;

/**
 * @author Jose Davis Nidhin
 */

import java.io.ByteArrayOutputStream;
import java.io.File; 
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.jetpac.deepbelief.DeepBelief.JPCNNLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public class CamTestActivity extends Activity {
	private static final String TAG = "CamTestActivity";
	Preview preview;
	Button buttonClick;
	TextView labelsView;
	Camera camera;
	String fileName;
	Activity act;
	Context ctx;
	Pointer networkHandle = null;
    private Pointer scratchCanPredictor;
    private Pointer guitarPredictor;
    private Pointer hornsPredictor;
    private Pointer maleVocalPredictor;
    private Pointer banjoPredictor;
    private Pointer bassPredictor;


    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ctx = this;
		act = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.main);
		
		preview = new Preview(this, (SurfaceView)findViewById(R.id.surfaceView));
		preview.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		((FrameLayout) findViewById(R.id.preview)).addView(preview);
		preview.setKeepScreenOn(true);
		
		labelsView = (TextView) findViewById(R.id.labelsView);
		labelsView.setText("");
		
		initDeepBelief();
	}

	@Override
	protected void onResume() {
		super.onResume();
		//      preview.camera = Camera.open();
		camera = Camera.open();
		camera.startPreview();
		preview.setCamera(camera);
		camera.setPreviewCallback(previewCallback);
	}

	@Override
	protected void onPause() {
		if(camera != null) {
            camera.setPreviewCallback(null);
			camera.stopPreview();
			preview.setCamera(null);
			camera.release();
			camera = null;
		}
		super.onPause();
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseDeepBelief();
    }

    private void resetCam() {
		camera.startPreview();
		preview.setCamera(camera);
	}
	
	PreviewCallback previewCallback = new PreviewCallback() {
		public void onPreviewFrame(byte[] data, Camera camera) {
			Size previewSize = camera.getParameters().getPreviewSize(); 
			YuvImage yuvimage=new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 80, baos);
			byte[] jdata = baos.toByteArray();
			Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
			classifyBitmap(bmp);
		}
	};

	void initDeepBelief() {
		AssetManager am = ctx.getAssets();
		String baseFileName = "jetpac.ntwk";
		String dataDir = ctx.getFilesDir().getAbsolutePath();
		String networkFile = dataDir + "/" + baseFileName;
		copyAsset(am, baseFileName, networkFile);
	    networkHandle = JPCNNLibrary.INSTANCE.jpcnn_create_network(networkFile);

        String scratch = "JBB_scratch_can.txt";
        copyAsset(am, scratch, dataDir + "/" + scratch);
        scratchCanPredictor = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(dataDir + "/" + scratch);
        JPCNNLibrary.INSTANCE.jpcnn_save_predictor(dataDir + "/test.txt", scratchCanPredictor );
//        guitarPredictor = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(dataDir + "/" + "JBB_guitar_can.txt");
//        hornsPredictor = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(dataDir + "/" + "JBB_horns_can.txt");
//        maleVocalPredictor = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(dataDir + "/" + "JBB_malevocal_can.txt");
//        banjoPredictor = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(dataDir + "/" + "JBB_banjo_can.txt");
//        bassPredictor = JPCNNLibrary.INSTANCE.jpcnn_load_predictor(dataDir+"/"+"JBB_bass_can.txt");

	    Bitmap lenaBitmap = getBitmapFromAsset("lena.png");
	    classifyBitmap(lenaBitmap);
	}

    void releaseDeepBelief(){
        JPCNNLibrary.INSTANCE.jpcnn_destroy_predictor(scratchCanPredictor);
//        JPCNNLibrary.INSTANCE.jpcnn_destroy_predictor(guitarPredictor);
//        JPCNNLibrary.INSTANCE.jpcnn_destroy_predictor(hornsPredictor);
//        JPCNNLibrary.INSTANCE.jpcnn_destroy_predictor(maleVocalPredictor);
//        JPCNNLibrary.INSTANCE.jpcnn_destroy_predictor(banjoPredictor);
//        JPCNNLibrary.INSTANCE.jpcnn_destroy_predictor(bassPredictor);
        JPCNNLibrary.INSTANCE.jpcnn_destroy_network(networkHandle);
    }
	
	private class PredictionLabel implements Comparable<PredictionLabel> {
		public String name;
		public float predictionValue;
		public PredictionLabel(String inName, float inPredictionValue) {
			this.name = inName;
			this.predictionValue = inPredictionValue;
		}
		public int compareTo(PredictionLabel anotherInstance) {
			final float diff = (this.predictionValue - anotherInstance.predictionValue);
			if (diff < 0.0f) {
				return 1;
			} else if (diff > 0.0f) {
				return -1;
			} else {
				return 0;
			}
	    }
	};
	
	void classifyBitmap(Bitmap bitmap) {
	    final int width = bitmap.getWidth();
	    final int height = bitmap.getHeight();
	    final int pixelCount = (width * height);
	    final int bytesPerPixel = 4;
	    final int byteCount = (pixelCount * bytesPerPixel);
	    ByteBuffer buffer = ByteBuffer.allocate(byteCount);
	    bitmap.copyPixelsToBuffer(buffer);
	    byte[] pixels = buffer.array();
	    Pointer imageHandle = JPCNNLibrary.INSTANCE.jpcnn_create_image_buffer_from_uint8_data(pixels, width, height, 4, (4 * width), 0, 0);

	    PointerByReference predictionsValuesRef = new PointerByReference();
	    IntByReference predictionsLengthRef = new IntByReference();
	    PointerByReference predictionsNamesRef = new PointerByReference();
	    IntByReference predictionsNamesLengthRef = new IntByReference();
		long startT = System.currentTimeMillis();	    	    
	    JPCNNLibrary.INSTANCE.jpcnn_classify_image(
	      networkHandle,
	      imageHandle,
	      1<<1,
	      -2,
	      predictionsValuesRef,
	      predictionsLengthRef,
	      predictionsNamesRef,
	      predictionsNamesLengthRef);
		long stopT = System.currentTimeMillis();	   
		float duration = (float)(stopT-startT) / 1000.0f;
		System.err.println("jpcnn_classify_image() took " + duration + " seconds.");

		JPCNNLibrary.INSTANCE.jpcnn_destroy_image_buffer(imageHandle);
		
	    Pointer predictionsValuesPointer = predictionsValuesRef.getValue(); 
	    final int predictionsLength = predictionsLengthRef.getValue();
	    Pointer predictionsNamesPointer = predictionsNamesRef.getValue();
	    final int predictionsNamesLength = predictionsNamesLengthRef.getValue();

        System.err.println(String.format("predictionsLength = %d", predictionsLength));
        System.err.println(String.format("predictionsNamesLength = %d", predictionsNamesLength));

	    float[] predictionsValues = new float[6];
	    Pointer[] predictionsNames = new Pointer[6];

//        predictionsNames[0] = scratchCanPredictor;
//        predictionsNames[1] = guitarPredictor;
//        predictionsNames[2] = hornsPredictor;
//        predictionsNames[3] = maleVocalPredictor;
//        predictionsNames[4] = banjoPredictor;
//        predictionsNames[5] = bassPredictor;

        System.err.println("predict can1");
        predictionsValues[0] = JPCNNLibrary.INSTANCE.jpcnn_predict(scratchCanPredictor, predictionsValuesPointer, predictionsLength);
        System.err.println("predict can2");
//        predictionsValues[1] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictionsNames[1], predictionsValuesPointer, predictionsNamesLength);
//        predictionsValues[2] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictionsNames[2], predictionsValuesPointer, predictionsNamesLength);
//        predictionsValues[3] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictionsNames[3], predictionsValuesPointer, predictionsNamesLength);
//        predictionsValues[4] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictionsNames[4], predictionsValuesPointer, predictionsNamesLength);
//        predictionsValues[5] = JPCNNLibrary.INSTANCE.jpcnn_predict(predictionsNames[5], predictionsValuesPointer, predictionsNamesLength);

        System.err.println("predict cans end.");
	    String names[] = {"scratch", "guitar", "horns", "maleVocal", "banjo", "bass"};
	    ArrayList<PredictionLabel> foundLabels = new ArrayList<PredictionLabel>();
	    for (int index = 0; index < predictionsLength; index += 1) {
	    	final float predictionValue = predictionsValues[index];
	    	if (predictionValue > 0.05f) {
	    		String name = names[index];
	            System.err.println(String.format("%s = %f", name, predictionValue));	    		
	            PredictionLabel label = new PredictionLabel(name, predictionValue);
	            foundLabels.add(label);
	    	}
	    }
	    Collections.sort(foundLabels);
	    String labelsText = "";
	    for (PredictionLabel label : foundLabels) {
	    	labelsText += String.format("%s - %.2f\n", label.name, label.predictionValue);
	    }
	    labelsView.setText(labelsText);
	}
	
    private static boolean copyAsset(AssetManager assetManager,
            String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
          in = assetManager.open(fromAssetPath);
          new File(toPath).createNewFile();
          out = new FileOutputStream(toPath);
          copyFile(in, out);
          in.close();
          in = null;
          out.flush();
          out.close();
          out = null;
          return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
          out.write(buffer, 0, read);
        }
    }

    private Bitmap getBitmapFromAsset(String strName) {
        AssetManager assetManager = getAssets();
        InputStream istr = null;
        try {
            istr = assetManager.open(strName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(istr);
        return bitmap;
    }
}
