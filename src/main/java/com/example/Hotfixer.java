package com.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Hotfixer {
    private static JTextArea statusArea; // Declare JTextArea for status messages

    public static void main(String[] args) {
        // Create the frame
        JFrame frame = new JFrame("My GUI Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Set frame to full screen size
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        frame.setSize(screenSize.width, screenSize.height);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Username Label and TextField
        JLabel usernameLabel = new JLabel("Username:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        frame.add(usernameLabel, gbc);

        JTextField usernameField = new JTextField(20);
        gbc.gridx = 1;
        frame.add(usernameField, gbc);

        // Password Label and TextField
        JLabel passwordLabel = new JLabel("Password:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        frame.add(passwordLabel, gbc);

        JPasswordField passwordField = new JPasswordField(20);
        gbc.gridx = 1;
        frame.add(passwordField, gbc);

        // URL Label and TextField
        JLabel urlLabel = new JLabel("Webpage URL:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        frame.add(urlLabel, gbc);

        JTextField urlField = new JTextField(20);
        gbc.gridx = 1;
        frame.add(urlField, gbc);

        // Folder Selection Button
        JLabel folderLabel = new JLabel("Select Folder:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        frame.add(folderLabel, gbc);

        JButton selectFolderButton = new JButton("Browse...");
        gbc.gridx = 1;
        frame.add(selectFolderButton, gbc);

        // Folder Path Display
        JTextField folderPathField = new JTextField(20);
        folderPathField.setEditable(false);
        gbc.gridy = 4;
        frame.add(folderPathField, gbc);

        // Submit Button
        JButton submitButton = new JButton("Submit");
        gbc.gridx = 1;
        gbc.gridy = 5;
        frame.add(submitButton, gbc);

        // Status Area with JScrollPane
        statusArea = new JTextArea(10, 60);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(statusArea);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        frame.add(scrollPane, gbc);

        // Action Listener for Submit Button
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Capture the values entered in the text fields
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                String url = urlField.getText();
                String folderPath = folderPathField.getText();

                // Create HttpClient instance
                HttpClient httpClient = HttpClient.newBuilder().build();

                // Perform login request with authentication
                HttpRequest loginRequest = null;
                try {
                    loginRequest = HttpRequest.newBuilder()
                            .uri(new URI("https://confluence.opentext.com/login.action"))
                            .header("Authorization", "Basic " + encodeCredentials(username, password))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                } catch (URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }

                // Execute login request
                HttpResponse<String> loginResponse = null;
                try {
                    loginResponse = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException ex) {
                    throw new RuntimeException(ex);
                }

                // Check if login was successful
                if (loginResponse.statusCode() == 200) {
                    // Get the cookies from the login response
                    String cookies = loginResponse.headers().firstValue("Set-Cookie").orElse("");

                    // Now, access the page after successful login
                    HttpRequest request = null;
                    try {
                        request = HttpRequest.newBuilder()
                                .uri(new URI(url))
                                .header("Cookie", cookies)  // Set the cookies for subsequent requests
                                .GET()
                                .build();
                    } catch (URISyntaxException ex) {
                        throw new RuntimeException(ex);
                    }

                    // Execute request to get the page content
                    HttpResponse<String> response = null;
                    try {
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    } catch (IOException | InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }

                    // Check if the access was successful
                    if (response.statusCode() == 200) {
                        // Parse the HTML content using Jsoup
                        Document doc = Jsoup.parse(response.body());

                        // Example: Extract text from elements with class="content"
                        Elements tables = doc.select("table.relative-table.wrapped.confluenceTable");

                        if (!tables.isEmpty()) {
                            Element table = tables.get(1); // Assuming the second table on the page
                            Elements headers = table.select("thead");
                            Elements rows = table.select("tr");

                            // Iterate over rows and print their contents
                            TreeMap<String, String> jars = new TreeMap<>();
                            for (Element row : rows) {
                                Elements cells = row.select("td");
                                for (int i = 0; i < cells.size(); i++) {
                                    jars.put(cells.get(0).text().replace(".jar", "").toLowerCase(), cells.get(1).text());
                                }
                            }

                            // Check for compile-time dependencies
                            ArrayList<String> li = new ArrayList<>();
                            for (Element row : rows) {
                                Elements cells = row.select("td");
                                for (int i = 0; i < cells.size(); i++) {
                                    if (!cells.get(2).text().isEmpty()) {
                                        for (int j = 0; j < cells.get(2).childrenSize(); j++) {
                                            if (!li.contains(cells.get(2).child(j).text())
                                                    && !jars.containsKey(extractText(cells.get(2).child(j).text().trim()))
                                                    && !cells.get(2).child(j).text().trim().contains(":")) {
                                                li.add(cells.get(2).child(j).text());
                                            }
                                        }
                                    }
                                }
                            }
                            ArrayList<String> finalList = new ArrayList<>();
                            for (String data : li) {
                                String[] arr = data.split("[ /]");
                                for (String str : arr) {
                                    if (!str.isEmpty() && !finalList.contains(str))
                                        finalList.add(str);
                                }
                            }
                            String currentJar = "";
                            String currentVersion = "";
                            for (String str : finalList) {
                                if (checkNumericText(str)) {
                                    currentVersion = str;
                                    jars.put(currentJar.toLowerCase(), currentVersion);
                                } else {
                                    currentJar = str;
                                }
                            }

                            // KeywordSearch Functionality Starts
                            for (Map.Entry<String, String> m : jars.entrySet()) {

                                // Specify the starting directory and the details for the replacement
                                Path startDir = Paths.get(folderPath);
                                String keyword = m.getKey(); // The keyword to search for
                                String newVersion = m.getValue(); // New version to replace the old version
                                // Specify the file extensions to search in
                                String[] extensions = {".xml", ".md", ".ipr", ".jardef", "dardef", ".artifact", ".javalibrary", ".properties"}; // Add or remove extensions as needed

                                updateStatus("Updating " + keyword + " to the latest version: " + newVersion);

                                try {
                                    Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
                                        @Override
                                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                            // Process each file if it matches the specified extensions
                                            if (matchesExtension(file, extensions)) {
                                                searchAndReplaceVersionInFile(file, keyword, newVersion);
                                            }
                                            return FileVisitResult.CONTINUE;
                                        }
                                    });
                                } catch (IOException err) {
                                    updateStatus("No Need to update");
                                }
                                // KeywordSearch Functionality Ends
                            }
                        } else {
                            updateStatus("Table not found.");
                        }
                    } else if (response.statusCode() == 302) {
                        // Handle redirection: Get the new location and follow the redirect
                        String redirectUrl = response.headers().firstValue("Location").orElse("");
                        updateStatus("Redirecting to: " + redirectUrl);
                        // You can implement logic here to handle the redirect if necessary
                    } else {
                        updateStatus("Failed to retrieve page. Status code: " + response.statusCode());
                    }
                } else {
                    updateStatus("Login failed with status code " + loginResponse.statusCode());
                }
            }
        });

        // Folder Selection Action
        selectFolderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser folderChooser = new JFileChooser();
                folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnValue = folderChooser.showOpenDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFolder = folderChooser.getSelectedFile();
                    folderPathField.setText(selectedFolder.getAbsolutePath());
                }
            }
        });

        // Display the frame
        frame.setVisible(true);
    }

    // Helper method to encode credentials for Basic Authentication
    private static String encodeCredentials(String username, String password) {
        return java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    // Method to check if a string contains only numbers and dots
    private static boolean checkNumericText(String text) {
        // Regular expression to match numbers and dots only
        String regex = "^\\d+(\\.\\d+)*$";

        // Check if the entire string matches the regex
        return text.matches(regex);
    }

    // Method to extract alphabetic characters (text) from a string until the last space or version number
    private static String extractText(String input) {
        // Use a pattern that matches until the last space or digits with dots
        int idx = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == ' ') {
                idx = i;
                changed = true;
                break;
            }
        }
        return changed ? input.substring(0, idx) : input;
    }

    private static boolean matchesExtension(Path file, String[] extensions) {
        String fileName = file.getFileName().toString();
        for (String ext : extensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static void searchAndReplaceVersionInFile(Path file, String keyword, String newVersion) {
        try {
            // Read all lines from the file
            List<String> lines = Files.readAllLines(file);
            boolean replaced = false;

            // Define patterns to find version numbers and rev attributes
            String regex = String.format("%s-(\\d+(\\.\\d+){0,%d})(-[\\w]+)?", Pattern.quote(keyword), keyword.length() - 1);
            Pattern keywordVersionPattern = Pattern.compile(regex);
            String reg = String.format("name=\"%s\" rev=\"(\\d+(\\.\\d+){0,%d})(-[\\w]+)?\"", Pattern.quote(keyword), keyword.length() - 1);
            Pattern revPattern = Pattern.compile(reg);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                // Check if the line contains the keyword
                if (line.contains(keyword)) {
                    // Replace version number associated with the keyword
                    Matcher keywordVersionMatcher = keywordVersionPattern.matcher(line);
                    if (keywordVersionMatcher.find()) {
                        String newLine = keywordVersionMatcher.replaceAll(keyword + "-" + newVersion);
                        lines.set(i, newLine);
                        replaced = true;
                    }

                    // Replace rev attribute value
                    Matcher revMatcher = revPattern.matcher(line);
                    if (revMatcher.find()) {
                        String oldVersion = revMatcher.group(1) + (revMatcher.group(3) != null ? revMatcher.group(3) : "");
                        String newLine = line.replaceAll(oldVersion, newVersion);
                        lines.set(i, newLine);
                        replaced = true;
                    }
                }
            }
            // Write the updated lines back to the file
            Files.write(file, lines);
            updateStatus("Version replaced and saved in file: " + file);

            // Rename the file if it contains the keyword
            String fileName = file.getFileName().toString();
            if (fileName.contains(keyword)) {
                String newFileName = fileName.replaceAll(keyword + "-\\d+(\\.\\d+)*(-\\w+)?", keyword + "-" + newVersion);
                Path newFilePath = file.resolveSibling(newFileName);
                Files.move(file, newFilePath);
                updateStatus("File renamed to: " + newFilePath);
            }
        } catch (IOException e) {
            updateStatus("No Need To Update");
        }
    }

    // Method to update the status area text
    private static void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(message + "\n");
            // Automatically scroll to the end
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }
}
