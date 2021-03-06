package ipt.lab.crypt.common.heys;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class HeysConsoleUtility {

    private static final Logger LOGGER = Logger.getLogger(HeysConsoleUtility.class.getName());

    private static final String ENCRYPTION_COMMAND = "%s e%s %d %s %s %s";
    private static final String TEMP_FILE_PREFIX = "diff-crypt";

    private final Path heysExeLocation;
    private final Path tempFilesLocation;

    private final int sBoxNumber;
    private boolean debugMode;

    public HeysConsoleUtility(Path baseDirectory, int sBoxNumber) {
        this.sBoxNumber = sBoxNumber;
        this.heysExeLocation = baseDirectory.resolve("heys.exe");
        this.tempFilesLocation = baseDirectory.resolve("tmp");
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public int[] encrypt(int[] blocks) {
        return encrypt(blocks, null);
    }

    public int[] encrypt(int[] blocks, int[] key) {
        try {
            //temp files for intercommunication with external process
            Path plainTextFile = tempFile();
            Path cipherTextFile = tempFile();
            Path keyFile = writeKeyToFileIfNotNull(key);

            //1. write blocks and key to temporary files

            try (OutputStream out = Files.newOutputStream(plainTextFile)) {
                for (int block : blocks) {
                    write2BytesLE(out, block);
                }
            }

            //2. issue an encryption command, wait until encryption completes

            String command = getEncryptionCommand(plainTextFile, cipherTextFile, keyFile);
            Process encryptionProcess = Runtime.getRuntime().exec(command);

            if (debugMode) {
                runReaderThread(new PrintWriter(System.err), encryptionProcess.getErrorStream());
                runReaderThread(new PrintWriter(System.out), encryptionProcess.getInputStream());
            }

            try (OutputStream encOut = encryptionProcess.getOutputStream()) {
                encOut.write(13); //enter code
            }

            encryptionProcess.waitFor();

            //3. read encrypted block from the file
            int[] result = new int[blocks.length];

            try (InputStream in = Files.newInputStream(cipherTextFile)) {
                for (int i = 0; i < result.length; i++) {
                    result[i] = readBytesLE(in);
                }
            }

            //4. delete temp files
            if (!debugMode) {
                tryDelete(plainTextFile);
                tryDelete(cipherTextFile);
            }

            return result;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Path writeKeyToFileIfNotNull(int[] key) throws IOException {
        if (key == null) {
            return null;
        }

        Path keyFile = tempFile();
        try (OutputStream keyOut = Files.newOutputStream(keyFile)) {
            for (int keyBlock : key) {
                write2BytesLE(keyOut, keyBlock);
            }
        }

        return keyFile;
    }

    private void write2BytesLE(OutputStream out, int block) throws IOException {
        out.write(block & 0xFF);
        out.write((block >> Byte.SIZE) & 0xFF);
    }

    private int readBytesLE(InputStream in) throws IOException {
        return in.read() | (in.read() << Byte.SIZE);
    }

    private Path tempFile() throws IOException {
        return Files.createTempFile(tempFilesLocation, TEMP_FILE_PREFIX, null);
    }

    private void tryDelete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            LOGGER.warning("Failed to delete: " + file);
        }
    }

    private String getEncryptionCommand(Path plainTextFile, Path cipherTextFile, Path keyFile) {
        return String.format(
                ENCRYPTION_COMMAND,
                heysExeLocation,
                (debugMode ? "*" : ""),
                sBoxNumber,
                plainTextFile,
                cipherTextFile,
                (keyFile == null ? "" : keyFile)
        );
    }

    private static void runReaderThread(PrintWriter writer, InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        new Thread(() -> {
            writer.println("Start Reader Thread [" + Thread.currentThread().getName() + "]");
            writer.flush();

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                    writer.flush();
                }
            } catch (IOException e) {
                writer.println(e);
                writer.flush();
            }

            writer.println("Reader Thread [" + Thread.currentThread().getName() + "] is shut down");
            writer.flush();
        }).start();
    }
}
