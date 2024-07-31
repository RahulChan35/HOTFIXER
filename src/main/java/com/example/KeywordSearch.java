package com.example;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeywordSearch {

    public static void main(String[] args) {
        // Specify the starting directory and the details for the replacement
        Path startDir = Paths.get("C:/Users/rahulc/HOTFIXES/24.4/bpm");
        String keyword = "commons-codec"; // The keyword to search for
        String newVersion = "2.0.0"; // New version to replace the old version
        // Specify the file extensions to search in
        String[] extensions = {".xml", ".md", ".ipr", ".jardef", ".dardef", ".javalibrary", ".artifact"}; // Add or remove extensions as needed

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
            e.printStackTrace();
        }
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
            Pattern keywordVersionPattern = Pattern.compile(keyword + "-\\d+(\\.\\d+){0,5}");
            Pattern revPattern = Pattern.compile("<dependency[^>]*rev=\"([^\"]+)\"[^>]*>");

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
                        String oldVersion = revMatcher.group(1);
                        String newLine = line.replace(oldVersion, newVersion);
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
            System.err.println("Error reading/writing file: " + file + " (" + e.getMessage() + ")");
        }
    }
}
