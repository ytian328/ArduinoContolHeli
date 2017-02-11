package ArduinoControlHeli;

import com.fazecast.jSerialComm.SerialPort;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Iterator;
import java.util.Scanner;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * The controller associated with the only view of our application. The
 * application logic is implemented here. It handles the button for
 * starting/stopping the camera, the acquired video stream, the relative
 * controls and the image segmentation process.
 * 
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @version 1.5 (2015-11-26)
 * @since 1.0 (2015-01-13)
 * 
 */
public class ArduinoControlHeli {
    @FXML
    private Button cameraButton; // FXML camera button
    
    @FXML
    private ImageView diffImage;
    
    @FXML
    private ImageView labeledImage;
    
    @FXML 
    private ImageView origImage;
    
    @FXML
    private Slider maxArea;
    
    @FXML
    private Slider blurSize; 
    
    @FXML
    private Label currentValues; // FXML label to show the current values set with the sliders
    
    private ScheduledExecutorService timer; // a timer for acquiring the video stream
    private VideoCapture capture = new VideoCapture(); // the OpenCV object that performs the video capture
    private boolean cameraActive; // a flag to change the button behavior
    private ObjectProperty<String> hsvValuesProp; // property for object binding
    private SerialPort port; // serial port that connect to arduino
    


    private int tolerance = 60;
    static Mat imag = null;
    /**
     * The action triggered by pushing the button on the GUI
     */
    @FXML
    private void startCamera() {
        // bind a text property with the string containing the current range of
        // HSV values for object detection
        hsvValuesProp = new SimpleObjectProperty<>();
        this.currentValues.textProperty().bind(hsvValuesProp);
				
        // set a fixed width for all the image to show and preserve image ratio
        this.imageViewProperties(this.labeledImage, 400);
        this.imageViewProperties(this.origImage, 200);
        this.imageViewProperties(this.diffImage, 200);
        
        startArduino();
		
        if (!this.cameraActive) {
            // start the video capture
            this.capture.open(0);
            
            // is the video stream available?
            if (this.capture.isOpened())	{
	this.cameraActive = true;
				
                    // grab a frame every 33 ms (30 frames/sec)
                    Runnable frameGrabber = new Runnable() {    
                        @Override
                        public void run() {
                            if(port.openPort()){
                                System.out.println("port is open");
                             movingObjDetection();
                            }else{
                                System.out.println("cannot open port");
                            }
                            
                        }
                    };
                    
                    this.timer = Executors.newSingleThreadScheduledExecutor();
                    this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
                    
                    // update the button content
                    this.cameraButton.setText("Stop Camera");
            }
            else {
                // log the error
                System.err.println("Failed to open the camera connection...");
            }
        }
        else {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.cameraButton.setText("Start Camera");
            // stop the timer
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // log the exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
            
            // release the camera
            this.capture.release();
        }
    }
    
    private void startArduino() {
        port = SerialPort.getCommPort("COM3");
        port.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
    }
    
    private void movingObjDetection() {
        Image imageToShow = null;
        Mat frame = new Mat();
        Mat outerBox = new Mat();
        Mat diffFrame = null;
        Mat tempFrame = null;
        ArrayList<Rect> array = new ArrayList<Rect>();
        int i = 0;
        
        if(this.capture.isOpened()) {
            try {     
                while(true) {
                    this.capture.read(frame);
                    imag = frame.clone();
                    
                    Point center  =  null;
                    Point camCenter = new Point(imag.size().width / 2, imag.size().height / 2);
                    Imgproc.circle(imag, camCenter, 60, new Scalar(0,225,0), 1);
                    
                    outerBox = new Mat(frame.size(), CvType.CV_8UC1);
                    Imgproc.cvtColor(frame, outerBox, Imgproc.COLOR_RGB2GRAY);
                    Imgproc.blur(outerBox, outerBox, new Size(this.blurSize.getValue(), this.blurSize.getValue()));

                    if(i == 0) {
                        diffFrame = new Mat(frame.size(), CvType.CV_8UC1);
                        tempFrame = new Mat(frame.size(), CvType.CV_8UC1);
                        diffFrame = outerBox.clone();
                    }
                    else {
                        Core.subtract(outerBox, tempFrame, diffFrame);
                        Imgproc.adaptiveThreshold(diffFrame, diffFrame, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 2);
                        array = this.detectContours(diffFrame);
                        if(array.size() > 0) {
                            Iterator<Rect> itr = array.iterator();
//                            while(itr.hasNext()) {
//                                Rect obj = itr.next();
//                                Imgproc.rectangle(imag, obj.br(), obj.tl(), new Scalar(0, 255, 0), 3);
//                            }
                            center = centerObject(array);
                            Imgproc.circle(imag, center, 15, new Scalar(255,225,102), 2);
                            
//                            Rect rect = sqrObject(array);
//                            Imgproc.rectangle(imag, rect.br(), rect.tl(), new Scalar(0, 0, 255), 3);
                        }
                    }
                    i = 1;
                    tempFrame = outerBox.clone();
                    this.onFXThread(this.labeledImage.imageProperty(), this.mat2Image(imag));
                    this.onFXThread(this.diffImage.imageProperty(), this.mat2Image(diffFrame));
                    this.onFXThread(this.origImage.imageProperty(), this.mat2Image(frame));
                    
                    String valuesToPrint = "maxArea: " + this.maxArea.getValue() + "\tblurSize: " + this.blurSize.getValue();
                    this.onFXThread(this.hsvValuesProp, valuesToPrint);
                    
                    PrintWriter output = new PrintWriter(port.getOutputStream());
                    output.print(controlCode(camCenter, center));
                    
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                    Scanner data = new Scanner(port.getInputStream());
                    while(data.hasNextLine()) {
                    String line = "";
                    try{line = data.nextLine();} catch(Exception e){}
                    System.out.println(line);

        }
                    
                    output.flush();
                }     
            } catch (Exception e) {
                System.err.print("ERROR");
                e.printStackTrace();
            }
        }        
    }
    
    private String controlCode(Point camCenter, Point center) {
        String code = "";
        char YAW =  63; 
        char PITCH  = 64;
        char THROTTLE  = 55;
        char TRIM = 63;
        char offset = 5;
        
        if(center == null) {
            return code + YAW + PITCH + THROTTLE + TRIM;
        }
        int deltaWidth = (int) (camCenter.x - center.x);
        int deltaHeight = (int) (camCenter.y - center.y);
        
        
        //if(deltaWidth < - tolerance )  YAW = (char)(YAW - offset);
        //if(deltaWidth > tolerance) YAW = (char)(YAW + offset);
        if(deltaHeight > tolerance) THROTTLE = (char)(THROTTLE - offset);
        if(deltaHeight < - tolerance) THROTTLE = (char)(THROTTLE + offset);
        return code + YAW + PITCH + THROTTLE + TRIM;
        
    }
    
    private ArrayList<Rect> detectContours(Mat diffFrame) {
        ArrayList<Rect> list =  new ArrayList<Rect>();
        Mat v = new Mat();
        Mat vv = diffFrame.clone();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(vv, contours, v, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double maxArea = this.maxArea.getValue();
        int maxAreaIdx = -1;
        Rect  r = null;
        
        for(int i = 0; i < contours.size(); i ++) {
            Mat contour = contours.get(i);
            double area = Imgproc.contourArea(contour);
            if(area > maxArea) {
                maxAreaIdx = i;
                r = Imgproc.boundingRect(contours.get(maxAreaIdx));
                list.add(r);
            }
        }
        v.release();
        return list;
    } 
    
    private Rect sqrObject(ArrayList<Rect> list) {

        int x1 = (int) imag.size().width;
        int y1 = (int) imag.size().height;
        int x2 = 0;
        int y2 = 0;
        
        Iterator<Rect> itr = list.iterator();
        while(itr.hasNext()) {
            Rect  cur = itr.next();
            x1 = Math.min(x1, cur.x);
            x2 = Math.max(x2, cur.width + cur.x);
            y1 = Math.min(y1, cur.y);
            y2 = Math.max(y2, cur.height + cur.y);
        }
        
        return new Rect(x1, y1, x2 - x1, y2 - y1);
    }
    
    private Point centerObject(ArrayList<Rect> list) {
        int x1 = (int) imag.size().width;
        int y1 = (int) imag.size().height;
        int x2 = 0;
        int y2 = 0;
        
        Iterator<Rect> itr = list.iterator();
        while(itr.hasNext()) {
            Rect  cur = itr.next();
            x1 = Math.min(x1, cur.x);
            x2 = Math.max(x2, cur.width + cur.x);
            y1 = Math.min(y1, cur.y);
            y2 = Math.max(y2, cur.height + cur.y);
        }
        return new Point((int)(x2 + x1) / 2, (int)(y1 + y2) / 2);      
    }
    
	
	
    /**
     * Set typical {@link ImageView} properties: a fixed width and the
     * information to preserve the original image ration
     * 
     * @param image
     *            the {@link ImageView} to use
     * @param dimension
     *            the width of the image to set
     */
    private void imageViewProperties(ImageView image, int dimension) {
        // set a fixed width for the given ImageView
        image.setFitWidth(dimension);
        // preserve the image ratio
        image.setPreserveRatio(true);
    }
	
    /**
     * Convert a {@link Mat} object (OpenCV) in the corresponding {@link Image}
     * for JavaFX
     * 
     * @param frame
     * the {@link Mat} representing the current frame
     * @return the {@link Image} to show
     */
    private Image mat2Image(Mat frame) {
        // create a temporary buffer		
        MatOfByte buffer = new MatOfByte();
        // encode the frame in the buffer, according to the PNG format
        Imgcodecs.imencode(".png", frame, buffer);
        // build and return an Image created from the image encoded in the buffer
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
	
    /**
     * Generic method for putting element running on a non-JavaFX thread on the
     * JavaFX thread, to properly update the UI
     * 
     * @param property
     * a {@link ObjectProperty}
     * @param value
     * the value to set for the given {@link ObjectProperty}
     */
    private <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(new Runnable() {			
            @Override
            public void run() {
                property.set(value);
            }
        });
    }
    
}