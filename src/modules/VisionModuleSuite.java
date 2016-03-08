package modules;

import java.io.File;

import vision.DeviceCaptureSource;
import vision.ImageCaptureSource;
import vision.ModuleRunner;

public class VisionModuleSuite {

    /**
     * Add any mappings here from capture sources to vision modules
     * Available capture sources:
     *   - DeviceCaptureSource
     *   - VideoCaptureSource
     *   - ImageCaptureSource
     */
    static {
        // Device number 1, as on most computers 0 refers to
        // the front-facing camera
        //runFromCamera(0);
        //runFromDirectory();
        runFromFile("C:/Users/Wilson/694/stuy-vision-2016/images/13.jpg");//VisionModuleSuite.class.getResource("").getPath() + "../../images/4.png");
    }

    private static void runFromFile(String fileName) {
        System.out.println(fileName);
        ModuleRunner.addMapping(new ImageCaptureSource(fileName), new StuyVisionModule());
    }

    private static void runFromDirectory() {
        String imageDirectory = VisionModuleSuite.class.getResource("").getPath() + "../../images/";
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            imageDirectory = imageDirectory.substring(1); // Remove leading "/" before "C:"
        }
        System.out.println(imageDirectory);
        File directory = new File(imageDirectory);
        File[] directoryListing = directory.listFiles();
        for (int i = 0; i < directoryListing.length && i < 8; i++) {
            if (i == 1 || i == 2) continue;
            System.out.println(directoryListing[i].getName());
            ModuleRunner.addMapping(new ImageCaptureSource(imageDirectory + directoryListing[i].getName()), new StuyVisionModule());
        }
    }

    private static void runFromCamera(int deviceNumber) {
        DeviceCaptureSource cs = new DeviceCaptureSource(deviceNumber);
        cs.setExposure(-10);
        ModuleRunner.addMapping(cs, new StuyVisionModule());
    }
}
