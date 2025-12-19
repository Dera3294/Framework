package framework.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class UploadedFile {
    private String fileName;
    private String contentType;
    private long size;
    private byte[] bytes;

    public UploadedFile(String fileName, String contentType, long size, byte[] bytes) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.bytes = bytes;
    }

    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public byte[] getBytes() { return bytes; }

    public File saveToDisk(String baseDirectory) throws IOException {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        File dateDir = new File(baseDirectory, dateFolder);
        if (!dateDir.exists() && !dateDir.mkdirs()) {
            throw new IOException("Impossible de créer le dossier : " + dateDir.getAbsolutePath());
        }
    
        File outFile = new File(dateDir, fileName);
        int counter = 1;
        String nameWithoutExt = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }
    
        while (outFile.exists()) {
            outFile = new File(dateDir, nameWithoutExt + "_" + counter + extension);
            counter++;
        }
    
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(bytes);
        }
    
        return outFile;
    }
    
}
