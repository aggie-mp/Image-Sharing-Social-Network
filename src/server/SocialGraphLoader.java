package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SocialGraphLoader {
  
  // Social graph map
  private final Map<String, List<String>> graph;

  // Social graph path
  private final String socialGraphPath = ProjectPaths.dataFile("SocialGraph.txt");

  // Constructor
  public SocialGraphLoader() throws IOException {
    this.graph = new HashMap<>();
  }

  // returns String[] of the people that follow given client
  public String[] getFollowers(String clientId) throws IOException {

    try (BufferedReader reader = new BufferedReader(new FileReader(socialGraphPath))) {

      String line;

      while ((line = reader.readLine()) != null) {
        String[] all = line.split("\\s+");

        if(all.length >= 1 && all[0].equals(clientId)) {
          String[] parts = new String[all.length - 1];

          System.arraycopy(all, 1, parts, 0, all.length - 1);

          return parts;
        }
        
      }

    }
    // If the client is not in the social graph
    return null;
  }

  // returns ArrayList<String> of the people that given client follows
  public ArrayList<String> getFollowing(String clientId) throws IOException {

    try (BufferedReader reader = new BufferedReader(new FileReader(socialGraphPath))) {

      String line;
      ArrayList<String> following = new ArrayList<>();

      while ((line = reader.readLine()) != null) {

        String[] all = line.split("\\s+");

        if(all.length >= 1 && !all[0].equals(clientId)) {

          for(int i=1; i<all.length; i++) {
            if(all[i].equals(clientId)) following.add(all[0]);
          }

        }

      }

      reader.close();

      return following;
    }
  }

  // Accepts follow requests
  public void acceptFollowRequests(String clientId, ArrayList<String> acceptFrom) throws IOException {

    Path path = Paths.get(socialGraphPath);
    List<String> lines = Files.readAllLines(path);
    List<String> updatedLines = new ArrayList<>();

    boolean updated = false;

    for (String line : lines) {

      String[] parts = line.trim().split("\\s+");

      if (parts.length > 0 && parts[0].equals(clientId)) {

        // check if the targetClientID was found
        updated = true;

        StringBuilder lineBuilder = new StringBuilder(line);
        for(String followerID : acceptFrom) {
            // Check if follower already exists to avoid duplicates
            boolean alreadyFollowed = Arrays.asList(parts).contains(followerID);
            if (!alreadyFollowed) {
                lineBuilder.append(" ").append(followerID);
            }

        }
        
        line = lineBuilder.toString();

      }

      updatedLines.add(line);
    }

    // If the targetClientID was not found, optionally add it as a new line
    if (!updated) {
        StringBuilder newLine = new StringBuilder(clientId);
      for(String followerID : acceptFrom){
          newLine.append(" ").append(followerID);
      }
      updatedLines.add(newLine.toString());
    }

    // Rewrite the file
    Files.write(path, updatedLines);

  }


  // Unfollow user
  public String unfollowUser(String clientId, String unfollowedId) throws IOException {

    Path path = Paths.get(socialGraphPath);
    List<String> lines = Files.readAllLines(path);
    List<String> updatedLines = new ArrayList<>();

    boolean userFound = false;

    for (String line : lines) {

      String[] parts = line.trim().split("\\s+");

      if (parts.length > 0 && parts[0].equals(unfollowedId)) {

        userFound = true;
        String newLine = line.replace(" " + clientId, "");
        line = newLine;

      }
      
      updatedLines.add(line);
      
    }

    if(userFound) {
      // Rewrite the file
      Files.write(path, updatedLines);  
      return "Unfollow completed successfully!";

    }else{
      return "The user you are trying to unfollow does not exist! Try again.";
    }
      
  }



  public void addUser(String clientId) throws IOException{
    FileWriter writer = null;

    try{
      writer = new FileWriter(socialGraphPath,true);
      writer.append("\n");
      writer.append(clientId);

    } catch (IOException e) {
      throw new RuntimeException(e);

    }finally {
      assert writer != null;
      writer.close();
    }
  }

}