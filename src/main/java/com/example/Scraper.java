package com.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.TreeMap;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scraper {
    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        // Authentication credentials
        String username = "rahulc";
        String password = "Jul@30Ot2024";

        // URL of the web page you want to scrape (requires authentication)
        String url = "https://confluence.opentext.com/display/XCP/24.4+Security+Fixes";

        // Create HttpClient instance
        HttpClient httpClient = HttpClient.newBuilder().build();

        // Perform login request with authentication
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(new URI("https://confluence.opentext.com/login.action"))
                .header("Authorization", "Basic " + encodeCredentials(username, password))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        // Execute login request
        HttpResponse<String> loginResponse = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        // Check if login was successful
        if (loginResponse.statusCode() == 200) {
            // Get the cookies from the login response
            String cookies = loginResponse.headers().firstValue("Set-Cookie").orElse("");

            // Now, access the page after successful login
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Cookie", cookies)  // Set the cookies for subsequent requests
                    .GET()
                    .build();

            // Execute request to get the page content
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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
                    TreeMap<String, String> jars = new TreeMap<String, String>();
                    for (Element row : rows) {
                        Elements cells = row.select("td");
                        for (int i=0;i<cells.size();i++){
                            jars.put(cells.get(0).text().replace(".jar", ""), cells.get(1).text());
                        }
                    }
                    // Check for compile-time dependencies
                    ArrayList<String> li = new ArrayList<String>();
                    for (Element row : rows) {
                        Elements cells = row.select("td");
                        for (int i=0;i<cells.size();i++){
                            if(!cells.get(2).text().isEmpty()) {
                                for(int j=0;j<cells.get(2).childrenSize();j++){
                                    if(!li.contains(cells.get(2).child(j).text())
                                       && !jars.containsKey(extractText(cells.get(2).child(j).text().trim()))
                                       && !cells.get(2).child(j).text().trim().contains(":")) {
                                        li.add(cells.get(2).child(j).text());
                                    }
                                }
                            }
                        }
                    }
                    ArrayList<String> finalList = new ArrayList<String>();
                    for(String data: li) {
                        String[] arr = data.split("[ /]");
                        for(String str: arr) {
                            if(!str.isEmpty() && !finalList.contains(str))
                                finalList.add(str);
                        }
                    }
                    String currentJar = "";
                    String currentVersion = "";
                    for(String str: finalList) {
                        if(checkNumericText(str)) {
                            currentVersion = str;
                            jars.put(currentJar, currentVersion);
                        }else{
                            currentJar = str;
                        }
                    }
                    for(Map.Entry<String, String> m: jars.entrySet()) {

                        // KeywordSearch Functionality Starts

                        // Specify the starting directory and the details for the replacement
                        Path startDir = Paths.get("C:/Users/rahulc/HOTFIXES/24.2.1/bpm");
                        String keyword = m.getKey(); // The keyword to search for
                        String newVersion = m.getValue(); // New version to replace the old version
                        // Specify the file extensions to search in
                        String[] extensions = {".xml", ".md", ".ipr", ".jardef", "dardef", ".artifact", ".javalibrary", ".properties"}; // Add or remove extensions as needed

                        System.out.println("Updating " + keyword + " to the latest version: " + newVersion);

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

                                @Override
                                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                                    // Handle the error if the file could not be accessed
                                    System.err.println("Error accessing file: " + file + " (" + exc.getMessage() + ")");
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } catch (IOException e) {
                            System.out.println("No Need to update");
                        }
                        // KeywordSearch Functionality Ends
                    }


                } else {
                    System.out.println("Table not found.");
                }
            } else if (response.statusCode() == 302) {
                // Handle redirection: Get the new location and follow the redirect
                String redirectUrl = response.headers().firstValue("Location").orElse("");
                System.out.println("Redirecting to: " + redirectUrl);
                // You can implement logic here to handle the redirect if necessary
            } else {
                System.out.println("Failed to retrieve page. Status code: " + response.statusCode());
            }
        } else {
            System.out.println("Login failed with status code " + loginResponse.statusCode());
        }
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
        int idx=0;
        boolean changed=false;
        for(int i=0;i<input.length();i++){
            if(input.charAt(i) == ' '){
                idx=i;
                changed=true;
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
            String reg = String.format("name=\"%s\" rev=\"(\\d+(\\.\\d+){0,%d})(-[\\w]+)?\"", Pattern.quote(keyword), keyword.length()-1);
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

            if (replaced) {
                // Write the updated lines back to the file
                Files.write(file, lines);
                System.out.println("Version replaced and saved in file: " + file);
            }
        } catch (IOException e) {
//            System.err.println("Error reading/writing file: " + file + " (" + e.getMessage() + ")");
            System.out.println("No Need To Update");
        }
    }
}
