package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class UsersLoader {

  // HashMap<String, List<String>> users
  private HashMap<String, List<String>> users = new HashMap<>();

  // Filepath of users.txt
  private final String filepath;

  // Constructor
  public UsersLoader(String filepath) {
    this.filepath = filepath;
  }

  // Loads users.txt and stores them in users HashMap
  private void loadUsers() {

    try  {

      // Reader
      BufferedReader reader = new BufferedReader(new FileReader(filepath));
      String line;

      while ((line = reader.readLine()) != null) {

          String[] parts = line.trim().split(":");

          if(parts.length >= 1) {

            String username = parts[0].trim();
            List<String> info = new ArrayList<>();

            if (parts.length > 1 && !parts[1].trim().isEmpty()) {

                String[] infoArray = parts[1].split(",");

                for (String f : infoArray) {
                  info.add(f.trim());
                }

            }

            users.put(username, info);

          }else {
            System.out.println("Empty line.");
          }
      }

      // Close reader
      reader.close();

    }catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Get selected user info
  public List<String> getUserInfo(String username) {
    loadUsers();
    return users.getOrDefault(username, new ArrayList<>());
  }

  // Return all users
  public Set<String> getAllUsers() {
    loadUsers();
    return users.keySet();
  }

  // Return clientId by searching for username and password
  public String checkUser(String username, String password) {

    loadUsers();

    List<String> infoToCheck = users.get(username);

    if (infoToCheck != null && Objects.equals(password, infoToCheck.get(0))) {
      return infoToCheck.get(1); // always clientId
    }

    return null;

  }

  // Adds new user to users.txt
  public void addUser ( String newInfo) throws IOException {

      FileWriter writer = null;

      try{
        writer = new FileWriter(filepath,true);
        writer.append("\n");
        writer.append(newInfo);

      } catch (IOException e) {
        throw new RuntimeException(e);

      }finally {
        assert writer != null;
        writer.close();
      }
  }

  // Print all users
  public void printUsers() {
    loadUsers();
    for (String user : users.keySet()) {
      System.out.println(user + " has info: " + users.get(user));
    }
  }

  // Look for a userid and return it's username or "" if not found
  public String getUserName(String userId) {

    loadUsers();

    for (HashMap.Entry<String, List<String>> user : users.entrySet()) {

      String tempClientId =  user.getValue().get(1);

      if(tempClientId.equals(userId)){
        return user.getKey();
      }

    }

    return "";
  }

  //Look for a username and return it's userid
  public String getUserId(String userName) {

    loadUsers();

    for (HashMap.Entry<String, List<String>> user : users.entrySet()) {

        String tempClientName =  user.getKey();

        if(tempClientName.equals(userName)){
          return user.getValue().get(1);
        }

    }

    return "";
  }

}
