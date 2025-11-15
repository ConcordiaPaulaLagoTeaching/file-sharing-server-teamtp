package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer implements Runnable{

    private FileSystemManager fsManager;
    private int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;


    public FileServer(int port, String fileSystemName, int totalSize){
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }

    public FileServer() {
    this(5050, "serverfs.dat", 10 * 128);  
    }

    public void run() {
        start();
    }

    public void start() {
        running = true;

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started. Listening on port " + port);

            while (running) {
                Socket clientSocket;

                try {
                    clientSocket = serverSocket.accept();
                } catch (Exception e) {
                    if (!running) break; // accept() unblocked by stop()
                    throw e;
                }

                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    public void stop() {
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();  // Unblocks accept()
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket clientSocket) {
    try (
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter writer = new PrintWriter(
            clientSocket.getOutputStream(), true)
    ) {
        String line;

        while (true) {
            line = reader.readLine();

            // Client disconnected
            if (line == null)
                break;

            // Empty command
            if (line.trim().isEmpty()) {
                writer.println("ERROR empty command");
                continue;
            }

            try {
                processCommand(line, writer);
            } catch (Exception e) {
                writer.println("ERROR " + (e.getMessage() == null ? "unknown error" : e.getMessage()));
            }
        }

    } catch (Exception e) {
        // ignore
    } finally {
        try { clientSocket.close(); } catch (Exception ignored) {}
    }
    }   

private void processCommand(String line, PrintWriter writer) throws Exception {

    String[] parts = line.split(" ", 3);
    String command = parts[0].toUpperCase();

    switch (command) {

        case "CREATE":
            if (parts.length < 2) {
                writer.println("ERROR missing filename");
                return;
            }
            fsManager.createFile(parts[1]);
            writer.println("OK");
            break;

        case "READ":
            if (parts.length < 2) {
                writer.println("ERROR missing filename");
                return;
            }
            writer.println(new String(fsManager.readFile(parts[1])));
            break;

        case "WRITE":
            if (parts.length < 2) {
                writer.println("ERROR missing filename");
                return;
            }
            String data = (parts.length == 3) ? parts[2] : "";
            fsManager.writeFile(parts[1], data.getBytes());
            writer.println("OK");
            break;

        case "DELETE":
            if (parts.length < 2) {
                writer.println("ERROR missing filename");
                return;
            }
            fsManager.deleteFile(parts[1]);
            writer.println("OK");
            break;

        case "LIST":
            writer.println(String.join(" ", fsManager.listFiles()));
            break;

        case "QUIT":
            writer.println("OK");
            break;

        default:
            writer.println("ERROR unknown command");
            break;
    }
    }

}
