package reso.examples.gobackn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CsvFileHandler {

    private static final String FILENAME = "WindowsSize.csv";

    private FileWriter writer;

    public File file;

    public CsvFileHandler() throws IOException {
        this.file = new File(FILENAME);
        this.writer = new FileWriter(file);
        System.out.println("size writen");
        writer.write("SIZE, PACKET\n");
    }

    public void writeFile(int[] data) throws Exception {
        if (data.length != 2) {
            throw new Exception("data must contain only 2 value");
        }
        writer.append(data[0] + "," + data[1] + "\n");
        System.out.println("I wrote something");
    }

    public void closeFile() throws IOException {
        writer.close();
    }
    
}
