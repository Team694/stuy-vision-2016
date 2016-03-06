package modules;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import gui.DoubleSliderVariable;
import gui.IntegerSliderVariable;
import gui.Main;
import util.TegraServer;
import vision.CaptureSource;
import vision.DeviceCaptureSource;
import vision.ImageCaptureSource;
import vision.VisionModule;

public class StuyVisionModule extends VisionModule {

    // Thresholds for filtering image input
    // These are held in slider variables as when using the gui it is useful for them
    // to be tweakable, and when running on the Tegra they have insignificant overhead
    public IntegerSliderVariable minH_GREEN = new IntegerSliderVariable("Min H Green", 58,  0,  255);
    public IntegerSliderVariable maxH_GREEN = new IntegerSliderVariable("Max H Green", 123,  0,  255);

    public IntegerSliderVariable minS_GREEN = new IntegerSliderVariable("Min S Green", 104, 0, 255);
    public IntegerSliderVariable maxS_GREEN = new IntegerSliderVariable("Max S Green", 255,  0,  255);

    public IntegerSliderVariable minV_GREEN = new IntegerSliderVariable("Min V Green", 20,  0,  255);
    public IntegerSliderVariable maxV_GREEN = new IntegerSliderVariable("Max V Green", 155, 0, 255);

    // Thresholds regarding the geometry of the bounding box of the region found by the HSV filtering
    public DoubleSliderVariable minAreaThreshold = new DoubleSliderVariable("Min Area Threshold", 200.0, 0.0, 700.0);
    public DoubleSliderVariable maxAreaThreshold = new DoubleSliderVariable("Max Area Threshold", frameArea * 0.1, 0.0, frameArea);
    public DoubleSliderVariable minRatioThreshold = new DoubleSliderVariable("Min ratio theshold", 1.1, 0.0, 4.0);
    public DoubleSliderVariable maxRatioThreshold = new DoubleSliderVariable("Max ratio theshold", 3.0, 0.0, 4.0);

    private static final double frameWidth = 640.0;
    private static final double frameHeight = 480.0;
    private static final double frameArea = frameWidth * frameHeight;

    private static PrintWriter logWriter;

    static {
        // Load opencv native library
        String dir = StuyVisionModule.class.getClassLoader().getResource("").getPath();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
			System.load(dir + "..\\lib\\opencv-3.0.0\\build\\lib\\opencv_java300.dll");
        } else {
			System.load(dir + "../lib/opencv-3.0.0/build/lib/libopencv_java300.so");
        }
        try {logWriter = new PrintWriter("logs.txt");} catch (Exception e) {}
    }

    // For running CV without a gui (e.g., on a Tegra on the bot)
    public static void main(String[] args) {
        System.out.println("Hello from modules.StuyVisionModule.main");

        CaptureSource camera = new DeviceCaptureSource(0);
        System.out.println("Got camera");

        TegraServer server = new TegraServer();
        StuyVisionModule vm = new StuyVisionModule();

        Mat frame = new Mat();
        for (;;) {
            camera.readFrame(frame);
            double[] vectorToGoal = vm.hsvThresholding(frame);
            server.sendData(vectorToGoal);
            System.out.println("Sent vector: " + Arrays.toString(vectorToGoal));
        }
    }

    // For running as a JavaFX gui
    public Object run(Main app, Mat frame) {
        app.postImage(frame, "Camera", this);
        double[] vectorToGoal = hsvThresholding(frame, app);
        printVectorInfo(vectorToGoal, logWriter);
        return vectorToGoal;
    }

    private boolean aspectRatioThreshold(double height, double width) {
        double ratio = width / height;
        return (minRatioThreshold.value() < ratio && ratio < maxRatioThreshold.value())
                || (1 / maxRatioThreshold.value() < ratio && ratio < 1 / minRatioThreshold.value());
    }

    private double[] getLargestGoal(Mat frame, Mat filteredImage, Main app) {
        boolean withGui = app != null;
        // Locate the goals
        Mat drawn = null;
        if (withGui) {
            // `drawn` will be the original image with info about what was
            // found in it drawn onto it
            drawn = frame.clone();
        }
        ArrayList<MatOfPoint> contour = new ArrayList<MatOfPoint>();
        Imgproc.findContours(filteredImage, contour, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        double largestArea = 0.0;
        RotatedRect largestRect = null;

        for (int i = 0; i < contour.size(); i++) {
            double currArea = Imgproc.contourArea(contour.get(i));
            if (currArea < minAreaThreshold.value() || currArea > maxAreaThreshold.value()) {
                continue;
            }
            MatOfPoint2f tmp = new MatOfPoint2f();
            contour.get(i).convertTo(tmp, CvType.CV_32FC1);
            RotatedRect r = Imgproc.minAreaRect(tmp);
            if (!aspectRatioThreshold(r.size.height, r.size.width)) {
                continue;
            }
            if (withGui) {
                // Draw this bounding rectangle onto `drawn`
                Point[] points = new Point[4];
                r.points(points);
                for (int line = 0; line < 4; line++) {
                    Imgproc.line(drawn, points[line], points[(line + 1) % 4], new Scalar(0, 255, 0));
                }
            }
            if (currArea > largestArea) {
                largestArea = currArea;
                largestRect = r;
            }
        }

        if (largestRect == null) {
            if (withGui) {
                // Post the unchanged image anyway for visual consistency
                app.postImage(frame, "No goals found", this);
            }
            // Use three `+Infinity`s to signal that nothing was found in the frame
            // There is exactly one `double` representation of `+Infinity`
            return new double[] {6 / 0.0, 9 / 0.0, 4 / 0.0};
        }

        double[] vector = new double[3];
        vector[0] = largestRect.center.x - (double) (frame.width() / 2);
        vector[1] = largestRect.center.y - (double) (frame.height() / 2);
        vector[2] = largestRect.angle;

        if (withGui) {
            Imgproc.circle(drawn, largestRect.center, 1, new Scalar(0, 0, 255), 2);
            Imgproc.line(drawn, new Point(frame.width() / 2, frame.height() / 2), largestRect.center,
                    new Scalar(0, 0, 255));
            app.postImage(drawn, "Goals!", this);
        }

        return vector;
    }

    /**
     * Process an image to look for a goal, and, if a <code>app</code> is
     * passed, post two intermediate states of the image from during
     * processing to the gui

     * @param frame The image to process

     * @param app (Optional: pass <code>null</code> to ignore) The
     * <code>Main</code> to post intermediate states of the processed image to.

     * @return Three doubles, in a <code>double[3]</code>, ordered as such:
     * <p><code>index 0</code>: The x-offset, in pixels, of the center of the
     * bounding rectangle of the found goal from the center of the image</p>
     * <p><code>index 1</code>: The y-offset, in pixels, of the center of the
     * bounding rectangle of the found goal form the center of the image</p>
     * <p><code>index 2</code>: The angle at which the bounding rectangle is
     * tilted
     */
    public double[] hsvThresholding(Mat frame, Main app) {
        boolean withGui = app != null;
        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
        ArrayList<Mat> greenFilterChannels = new ArrayList<Mat>();

        // Split HSV channels and process each channel
        Core.split(hsv, greenFilterChannels);
        Core.inRange(greenFilterChannels.get(0), new Scalar(minH_GREEN.value()), new Scalar(maxH_GREEN.value()),
                greenFilterChannels.get(0));
        Core.inRange(greenFilterChannels.get(1), new Scalar(minS_GREEN.value()), new Scalar(maxS_GREEN.value()),
                greenFilterChannels.get(1));
        Core.inRange(greenFilterChannels.get(2), new Scalar(minV_GREEN.value()), new Scalar(maxV_GREEN.value()),
                greenFilterChannels.get(2));

        // Merge channels and erode dilate to remove noise
        Mat erodeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Mat dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9, 9));

        Mat greenFiltered = new Mat();
        Core.bitwise_and(greenFilterChannels.get(0), greenFilterChannels.get(1), greenFiltered);
        Core.bitwise_and(greenFilterChannels.get(2), greenFiltered, greenFiltered);
        Imgproc.erode(greenFiltered, greenFiltered, erodeKernel);
        Imgproc.dilate(greenFiltered, greenFiltered, dilateKernel);

        if (withGui) {
            app.postImage(greenFiltered, "Green - After erode/dilate", this);
            app.postImage(greenFiltered, "Merged", this);
        }

        double[] output = getLargestGoal(frame, greenFiltered, app);
        try {
            logWriter.println("Vector calculated: " + Arrays.toString(output));
            logWriter.flush();
        } catch (Exception e) {}
        return output;
    }

    /**
     * Call hsvThresholding with <code>null</code> for the <code>app</code>.
     * See doc for <code>hsvThresholding(Mat, Main)</code> for detail.
     * @param frame Image to process
     * @return
     */
    public double[] hsvThresholding(Mat frame) {
        return hsvThresholding(frame, null);
    }

    /**
     * Tests time taken to process <code>iters</code> frames read from <code>cs</code>
     * @param cs The CaptureSource from which to read frames
     * @param iters The number of frames to read from <code>cs</code> and to process and time
     * @return The average time taken by <code>hsvThresholding</code> to process one of the frames
     */
    public double testProcessingTime(CaptureSource cs, int iters) {
        int total = 0;
        for (int i = 0; i < iters; i++) {
            Mat frame = new Mat();
            cs.readFrame(frame);
            long start = System.currentTimeMillis();
            hsvThresholding(frame);
            total += (int) (System.currentTimeMillis() - start);
        }
        return total / (double) iters;
    }

    // Camera constants
    private static final double MAX_DEGREES_OFF_AUTO_AIMING = 2;
    // TODO: Resolve actual pixel-width of a frame taken from the camera. The
    // following conflicts with `frameWidth`, etc. constants above. If they
    // are right these should be changed, if not, the camera resolution should
    // be lowered in setup-camera.sh
    private static final int CAMERA_FRAME_PX_WIDTH = 1280;
    private static final int CAMERA_FRAME_PX_HEIGHT = 720;
    private static final int CAMERA_VIEWING_ANGLE_X = 60;

    // Methods for demoing or testing auto-aiming logic:
    /**
     * Convert a width in pixels from the camera frame to the number of degrees
     * spanned by those pixels (horizontally)
     * @param px
     * @return The number of degrees <code>px</code> pixels spans horizontally
     */
    private static double pxOffsetToDegrees(double px) {
        return CAMERA_VIEWING_ANGLE_X * px / CAMERA_FRAME_PX_WIDTH;
    }

    private static void printVectorInfo(double[] vectorToGoal, PrintWriter writer) {
        writer.println("\n\n======================================");
        writer.println("Time: " + System.currentTimeMillis());
        writer.println("Vector: " + Arrays.toString(vectorToGoal));
        writer.flush();
        if (vectorToGoal[0] == Double.POSITIVE_INFINITY
                && vectorToGoal[1] == Double.POSITIVE_INFINITY
                && vectorToGoal[2] == Double.POSITIVE_INFINITY) {
            writer.println("NO GOAL IN FRAME");
            writer.flush();
            return;
        }
        double degsOff = pxOffsetToDegrees(vectorToGoal[0]);
        writer.println("Degree offest to account for: " + degsOff);
        if (Math.abs(degsOff) < MAX_DEGREES_OFF_AUTO_AIMING) {
            writer.println("CLOSE ENOUGH. SHOOT.");
            writer.flush();
            return;
        }
        writer.println("TURN " + (degsOff > 0 ? "LEFT " : "RIGHT") + " BY " + Math.abs(degsOff) + "deg");
        writer.flush();
    }
}
