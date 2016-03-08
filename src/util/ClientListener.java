package util;

import java.io.BufferedReader;
import java.io.IOException;

public class ClientListener implements Runnable {

    private BufferedReader reader;
    private int imagesToSave;

    public ClientListener(BufferedReader reader) {
        this.reader = reader;
        imagesToSave = 0;
    }

    /**
     *
     * @return Whether the next frame processed should be saved to a file
     */
    public boolean shouldSaveImage() {
        boolean shouldSave = imagesToSave > 0;
        imagesToSave = Math.max(0, imagesToSave - 1);
        return shouldSave;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String input = reader.readLine();
                if (input.equals("save")) {
                    imagesToSave += 1;
                }
            } catch (IOException e) {
                System.out.println("An IOException has occured. Message: " + e.getMessage());
            }
        }
    }

}
