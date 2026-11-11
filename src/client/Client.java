package client;

import common.Packet;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

public class Client {

  // Streams
  private ObjectOutputStream out;
  private ObjectInputStream in;

  // Sockets, IP and Port
  private Socket connection;
  private final String serverIP;
  private final int serverPort;

  // Menu Flags
  private boolean loginFlag = false;
  private boolean menuFlag = false;

  // Scanner
  private final Scanner myObj = new Scanner(System.in);

  // Group Id
  private final String GroupId = "45";

  // Client Id
  private String clientId;
  private String currentUsername;
  private Map<String, String> notificationUsernames = new java.util.HashMap<>();

  // Constructor
  public Client(String ip, int port) {
    this.serverIP = ip;
    this.serverPort = port;
  }

  // Each account owns its local files. Never clear another logged-in client's data.
  private Path accountRoot() throws IOException {
    if (clientId == null || !clientId.matches("[0-9]+")) {
      throw new IOException("No valid client ID for local storage");
    }
    Path root = LocalStoragePaths.usersRoot().resolve(GroupId + clientId);
    Files.createDirectories(root.resolve("directory"));
    Files.createDirectories(root.resolve("profiles"));
    return root;
  }

  private Path imageDirectory() throws IOException {
    return accountRoot().resolve("directory");
  }

  private Path profileDirectory() throws IOException {
    return accountRoot().resolve("profiles");
  }

  // Main 
  public static void main(String[] args) {
    Client client = new Client("localhost", 303);
    System.out.println("\nEstablishing... connection with server!\n");
    client.startConnection();
  }
  
  // Start connection with server and handle Login and Main Menus
  public void startConnection() {
    try {

      connection = new Socket(serverIP, serverPort);

      out = new ObjectOutputStream(connection.getOutputStream());
      in = new ObjectInputStream(connection.getInputStream());

      out.flush();

      System.out.println("Connection Established!");

      out.writeObject("Established connection with server!");
      out.flush();

      // Login Menu
      while(!loginFlag) {
        
        // Print Login Menu
        System.out.println("**** Login Menu ****");
        System.out.println("\n1) Log In\n2) Sign Up\n3) Exit\n\nPlease insert a valid action number from 1-3 to continue:");
        String actionCode = myObj.nextLine();

        // Check if the action code is valid
        while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3"))) {
          System.out.println("Wrong Input! Please insert a valid action number from 1-3:");
          actionCode = myObj.nextLine();                    
        }

        // Send client's action code to the server
        out.writeObject(actionCode);
        out.flush();

        // Switch action code
        switch (actionCode) {

          case "1":
            // Login
            login();
            break;

          case "2":
            // Sign Up
            signup();
            break;

          case "3":
            // Exit
            stopClient();
            break;

        }

      }


      // Main Menu
      while(!menuFlag) {

        // Print Main Menu
        System.out.println("\n****Main Menu ****");
        System.out.println("\n1) Upload Image from Client to Server\n2) Search Image on server\n3) Follow\n4) Unfollow\n5) Access Profile\n6) Check Notifications\n7) Exit");
        System.out.println("\nPlease insert a valid action number from 1-7 to continue:");
        String actionCode = myObj.nextLine();

        // Check if the action code is valid
        while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3") || Objects.equals(actionCode, "4") || Objects.equals(actionCode, "5") || Objects.equals(actionCode, "6") || Objects.equals(actionCode, "7"))) {
          System.out.println("Wrong Input!\nPlease insert a valid action number from 1-7 to continue:");
          actionCode = myObj.nextLine();
        }

        // Send client's action code to the server
        out.writeObject(actionCode);
        out.flush();

        // Switch action code
        switch (actionCode) {

          case "1":
            // Upload Image
            uploadImg();
            break;

          case "2":
            // Search Image
            searchImg();
            break;

          case "3":
            // Follow
            follow();
            break;

          case "4":
            // Unfollow
            unfollow();
            break;

          case "5":
            // Access Profile
            accessProfile();
            break;

          case "6":
            // Manually fetch and respond to notifications without reconnecting.
            checkNotifications();
            break;

          case "7":
            // Exit
            stopClient();
            break;
        }
      }

    } catch (IOException e) {
      System.out.println(e.getMessage());
    } catch (ClassNotFoundException | InterruptedException e) {
      throw new RuntimeException(e);
    }

  }



  // LOGIN MENU -------------------------------------------------------------------------------------------------------------

  // Login Menu Option 1
  // Login
  public void login() throws IOException, ClassNotFoundException, InterruptedException {

    // Ask for username
    System.out.println("\nUsername:");
    String userName = myObj.nextLine();

    // Ask for password
    System.out.println("\nPassword:");
    String password = myObj.nextLine();

    // Send username and password to the server
    out.writeObject(userName);
    out.flush();

    out.writeObject(password);
    out.flush();

    // Server response for login
    String response = (String) in.readObject();

    if(response.equals("SuccessLogin")){

      // update local clientId variable
      clientId = (String) in.readObject();
      currentUsername = userName;

      System.out.println("\nSuccessful login!\n");
      System.out.println("Welcome client " + clientId + ".\n");

      updateLocalFiles();

      loginFlag = true;



    }else{

      // Reset login
      System.out.println("\nFailed login! Username or password is incorrect.\nPlease try again.\n");
      this.login();
    }

  }


  // Login Menu Option 2
  // Sign Up
  public void signup() throws IOException, ClassNotFoundException {

    // Ask for username
    System.out.println("\nCreate a username:");
    String userName = myObj.nextLine();

    // Ask for password
    System.out.println("\nCreate a password:");
    String password = myObj.nextLine();

    // Send username and password to the server
    out.writeObject(userName);
    out.flush();

    out.writeObject(password);
    out.flush();

    // Server response for sign up
    String response = (String) in.readObject();
    if(response.equals("SuccessSignUp")){

      clientId = (String) in.readObject();
      currentUsername = userName;
      System.out.println("\nSuccessful sign up!\nYou are now logged in.\nWelcome client " + clientId + ".\n");
      loginFlag = true;

      fixNewUserlFiles();

    } else{
      System.out.println("\nFailed to sign up! Username already exists.\nPlease try different username.\n");
      this.signup();
    }
  }

  // ------------------------------------------------------------------------------------------------------------------------


  // MAIN MENU --------------------------------------------------------------------------------------------------------------

  // Main Menu Option 1
  // Upload Image
  private void uploadImg() throws IOException, ClassNotFoundException {

    // Check if handshake is accepted
    if(uploadHandshake()) {

      String pathname = "";
      String pathNameClean = "";

      // Check input filename
      while(true){

        // Ask for filename
        System.out.println("\nEnter filename please:");
        pathname = myObj.nextLine();

        boolean imgTag = pathname.contains(".jpg") || pathname.contains(".png") || pathname.contains(".jpeg") || pathname.contains(".JPG") || pathname.contains(".PNG") || pathname.contains(".JPEG");

        // Check if input inludes smt dot and is an image type AND if it is and image
        if((pathname.split("\\.").length == 2) && imgTag){
          break;
        }else{
          System.out.println("\nWrong input! Please insert a valid filename:");
        }

      }

      // Keeps only the first part of filename given  example.png ---> example
      pathNameClean = pathname.split("\\.")[0];


      // Check that the image exists before continuing
      File imageFile = imageDirectory().resolve(pathname).toFile();

      if (!imageFile.isFile()) {
        System.out.println("\nThe image '" + pathname + "' was not found!");
        System.out.println("Please place the image in your personal directory:");
        System.out.println(imageDirectory().toAbsolutePath());

        // Notify the server that the upload was cancelled
        out.writeObject("UPLOAD_CANCELLED");
        out.flush();

        System.out.println("Upload cancelled. Returning to Main Menu.\n");
        return;
      }

      // Send server img name
      out.writeObject(pathname);
      System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(filename)");


      // Load image
      byte[] imageBytes = Files.readAllBytes(imageFile.toPath());

      // Ask for a single description
      System.out.println("\nEnter image description:");
      String description = myObj.nextLine();

      // Send description to server
      out.writeObject(description);
      out.flush();

      // Save description locally
      File file = imageDirectory().resolve(pathNameClean + ".txt").toFile();

      try (FileWriter fw = new FileWriter(file)) {
        fw.write(description);
      }

      // Prepare description for packet transfer
      byte[] descriptionBytes = description.getBytes();

      int descLength = descriptionBytes.length;

      // Check if description is too long
      if (descLength > 255) {
        throw new IllegalArgumentException("Description too long for 1-byte length field");
      }

      // Combine data
      byte[] fullData = new byte[1 + descLength + imageBytes.length];
      fullData[0] = (byte) descLength;

      // Copy data
      System.arraycopy(descriptionBytes, 0, fullData, 1, descLength);
      System.arraycopy(imageBytes, 0, fullData, 1 + descLength, imageBytes.length);

      // Divide into 10 packets
      int packetSize = (int) Math.ceil(fullData.length / 10.0);
      for (int i = 0; i < 10; i++) {
        int start = i * packetSize;
        int end = Math.min(start + packetSize, fullData.length);

        byte[] chunk = new byte[end - start];

        System.arraycopy(fullData, start, chunk, 0, end - start);

        // Send packet to server
        Packet packet = new Packet(i, chunk);
        out.writeObject(packet);
        out.flush();

        // Wait ACK from server
        String ack = in.readLine();
        if (!ack.equals("ACK" + i)) {

          System.out.println("ACK mismatch or timeout. Resending...");
          i--; // resend

        } else {
          System.out.println("Received: " + ack);
        }
      }

      // update user profile.txt
      FileWriter proFileServerWriter = new FileWriter(profileDirectory().resolve("Profile_" + GroupId + clientId + ".txt").toString()	,true);
      proFileServerWriter.append("\n");
      proFileServerWriter.append(currentUsername).append(" posted ").append(pathname);
      proFileServerWriter.close();

    }else{
      System.out.println("\nHandshake Failed! Try again :(");
    }
  }

  // Handshake function for upload
  private boolean uploadHandshake() throws IOException, ClassNotFoundException {

    // Send request
    out.writeObject("Request to upload");
    System.out.println("HANDSHAKE STEP 1: Client sent request");

    // Wait for response
    String handshakeResponse = (String) in.readObject();
    System.out.println("HANDSHAKE STEP 2: Server sent acknowledgement");

    // Check if handshake is accepted
    return handshakeResponse.equals("acceptedUpload");
  }


  // Main Menu Option 2
  // Search Images
  public void searchImg() throws IOException, ClassNotFoundException, InterruptedException {

    String searchImgInput = "";

    while (true) {
      System.out.println("\nEnter file you want to search:");
      searchImgInput = myObj.nextLine().trim();

      if (!searchImgInput.isEmpty()) {
        break;
      }

      System.out.println("Please enter a valid filename.");
    }

    // Send filename to server
    out.writeObject(searchImgInput);
    out.flush();


      // Server response
      @SuppressWarnings("unchecked")
      ArrayList<String[]> results = (ArrayList<String[]>)in.readObject();
      if (results.isEmpty()) {
        System.out.println("No results found :(");
        return;
      }
      boolean more = true;
      while (more) {
        System.out.println("Found the following images:");
        for (int i = 0; i < results.size(); i++) {
          String[] item = results.get(i);
          System.out.println((i + 1) + ". User: " + item[1] + " - Picture: " + displayImageName(item[2]));
        }
        System.out.println("0. Back to Main Menu");
        System.out.println("Select an image to request download [0-" + results.size() + "]:");
        int selection = -1;
        while (selection < 0 || selection > results.size()) {
          try { selection = Integer.parseInt(myObj.nextLine().trim()); }
          catch (NumberFormatException ignored) { }
          if (selection < 0 || selection > results.size()) System.out.println("Invalid image number:");
        }
        if (selection == 0) {
          out.writeObject("done");
          out.flush();
          return;
        }
        // Search only supports download requests; comments are available via Access Profile.
        out.writeObject("download");
        out.flush();
        downloadImg(selection - 1, results.get(selection - 1));
        System.out.println("Select another photo from these results? [y/n]");
        more = readOption("y", "n").equals("y");
        out.writeObject(more ? "again" : "done"); out.flush();
      }
  }

  // Download Picture
  private void downloadImg(int userSelectionNum, String[] imageInfo) throws ClassNotFoundException, IOException {
    if (downloadHandshake()) {
      out.writeObject(userSelectionNum);
      out.flush();
      System.out.println((String) in.readObject());
    }
  }

  // Handshake function for download
  private boolean downloadHandshake() throws IOException, ClassNotFoundException {
    out.writeObject("request to download");
    System.out.println("HANDSHAKE STEP 1: Client sent request");
    String handshakeResponse = (String) in.readObject();
    System.out.println("HANDSHAKE STEP 2: Server sent acknowledgement");
    return handshakeResponse.equals("acceptedDownload");
  }


  // Main Menu Option 3
  // Follow other user function
  private void follow() throws IOException, ClassNotFoundException {

    String userToFollow = "";

    // Check input filename
    while(true){

      System.out.println("\nEnter the username of a user you want to follow");
      userToFollow = myObj.nextLine();

      if(userToFollow != null && !userToFollow.isEmpty()){
        break;
      }else{
        System.out.println("\nWrong input! Please insert a valid username:");
      }

    }

    // Send client's input to the server
    out.writeObject(userToFollow);
    out.flush();

    // Server response
    System.out.println((String)in.readObject());
  }


  // Main Menu Option 4
  // Unfollow other user
  private void unfollow() throws IOException, ClassNotFoundException {
      String userToUnFollow = "";

      // check input filename
      while(true){
          System.out.println("\nEnter the username of a user you want to unfollow.");
          userToUnFollow = myObj.nextLine();

          if(userToUnFollow != null && !userToUnFollow.isEmpty()){
              break;
          }else{
              System.out.println("\nWrong input! Please insert a valid username:");
          }
      }

      // send client's unfollow input to the server
      out.writeObject(userToUnFollow);
      out.flush();

      System.out.println("\n" + (String)in.readObject());
  }


  // Main Menu Option 5
  // Access Profile
  private void accessProfile() throws IOException, ClassNotFoundException, InterruptedException {

    boolean accessProfileFlag = true;
    ArrayList<String> usersList = (ArrayList<String>) in.readObject();

    while(accessProfileFlag) {

      System.out.println("\nWhich profile would you like to access?");

      for (int i = 0; i < usersList.size(); i++) {
        System.out.println((i + 1) + ". " + usersList.get(i));
      }

      String choice = "";
      int selectedProfile = -1;

      while (true) {
        System.out.println("Enter profile number [1-" + usersList.size() + "]:");

        String input = myObj.nextLine().trim();

        try {
          selectedProfile = Integer.parseInt(input);

          if (selectedProfile >= 1 && selectedProfile <= usersList.size()) {
            choice = usersList.get(selectedProfile - 1);
            break;
          }
        } catch (NumberFormatException e) {
          // Invalid number; display the message below
        }

        System.out.println("\nWrong input! Please enter a valid profile number.");
      }

      // Send to server the profile name client wants to access
      out.writeObject(choice);
      out.flush();

      // Read server message (access_approved/denied)
      String response = (String) in.readObject();

      if (response.equals("access_approved")) {
        // Read profile
        String profileContent = (String) in.readObject();

        // Print profile content
        System.out.println("\n" + hideExtensionsInProfile(profileContent) +"\n");



        ArrayList<String[]> imgsToDownload = (ArrayList<String[]>) in.readObject();

        if (choice.equals(usersList.get(0))) {
          System.out.println("This is your own profile. Comment and Download are not available.");
        } else if (imgsToDownload.isEmpty()) {
          System.out.println("No images available in this profile.");
        } else {
          boolean more = true;
          while (more) {
            System.out.println("The following images are available for download and/or comment:");
            for (int i = 0; i < imgsToDownload.size(); i++) {
              System.out.println((i + 1) + ". " + displayImageName(imgsToDownload.get(i)[2]));
            }
            System.out.println("0. Exit Profile");

            int imageNumber = -1;
            String action = "";
            while (true) {
              System.out.println("Enter image number and action (e.g. 1 download or 2 comment), or 0 to exit:");
              String input = myObj.nextLine().trim();
              if (input.equals("0")) {
                more = false;
                break;
              }
              String[] tokens = input.split("\\s+");
              if (tokens.length == 2) {
                try { imageNumber = Integer.parseInt(tokens[0]); }
                catch (NumberFormatException ignored) { imageNumber = -1; }
                action = tokens[1].toLowerCase();
                if (imageNumber >= 1 && imageNumber <= imgsToDownload.size()
                        && (action.equals("download") || action.equals("comment"))) {
                  break;
                }
              }
              System.out.println("Invalid input. Please try again.");
            }

            if (!more) break;

            out.writeObject(action); out.flush();
            if (action.equals("download")) downloadImg(imageNumber - 1, imgsToDownload.get(imageNumber - 1));
            else handleComment(imageNumber - 1, imgsToDownload.get(imageNumber - 1));
            System.out.println("Would you like to select another image from this profile? [y/n]");
            more = readOption("y", "n").equals("y");
          }
        }
        out.writeObject("done"); out.flush();

      } else {
        System.out.println("Access denied! You can only access profiles of the users that you follow.\n");
      }

      do {
        System.out.println("\nWould you like to access another profile? [y/n]");
        choice = myObj.nextLine();

      } while (!(choice.equals("y") | choice.equals("Y") | choice.equals("n") | choice.equals("N")));

      if (choice.equals("n") | choice.equals("N")) {
        accessProfileFlag = false;

        out.writeObject("no_retry");
        out.flush();

      } else {
        out.writeObject("retry");
        out.flush();
      }
    }
  }

  private void handleComment(int userSelectionNum, String[] imageInfo) throws IOException, ClassNotFoundException {
    out.writeObject(imageInfo[2]);
    System.out.println("\nWrite a comment for image: " + displayImageName(imageInfo[2]));
    String comment = myObj.nextLine();
    out.writeObject(comment);
    out.flush();
    System.out.println(((String) in.readObject()).equals("Accepted")
            ? "Comment posted successfully!" : "Comment denied or image unavailable.");
  }

  private String hideExtensionsInProfile(String profile) {
    return profile.replaceAll("(?i)\\.(jpg|jpeg|png|gif|bmp|webp)(?=\\s|$)", "");
  }

  private String displayImageName(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  // -----------------------------------------------------------------------------------------------------------------------


  // GENERAL FUNCTIONS ----------------------------------------------------------------------------------------------------------------------------------------

  // Checks user notifications txt file for new notifications after login
  private void checkNotifications() throws IOException, ClassNotFoundException, InterruptedException {
    while (true) {
      @SuppressWarnings("unchecked")
      Map<String, String> names = (Map<String, String>) in.readObject();
      notificationUsernames = names;
      @SuppressWarnings("unchecked")
      Set<String> currentlyFollowing = (Set<String>) in.readObject();
      @SuppressWarnings("unchecked")
      ArrayList<String> notifications = (ArrayList<String>) in.readObject();
      System.out.println("\n========== NOTIFICATIONS ==========");
      for (int i = 0; i < notifications.size(); i++) {
        System.out.println((i + 1) + ". " + notificationLabel(notifications.get(i)));
      }
      if (notifications.isEmpty()) System.out.println("No pending notifications.");
      System.out.println("0. Return to Main Menu");
      int selected;
      while (true) {
        System.out.print("Select a notification [0-" + notifications.size() + "]: ");
        try { selected = Integer.parseInt(myObj.nextLine().trim()); }
        catch (NumberFormatException e) { selected = -1; }
        if (selected >= 0 && selected <= notifications.size()) break;
        System.out.println("Invalid selection.");
      }
      if (selected == 0) {
        out.writeObject("EXIT"); out.flush(); return;
      }
      String notification = notifications.get(selected - 1);
      out.writeObject("OPEN " + (selected - 1)); out.flush();
      String[] parts = notification.split("\\s+", 4);
      System.out.println("\n" + notificationLabel(notification));
      String action = "READ";
      switch (parts[0]) {
        case "follow":
          if (currentlyFollowing.contains(parts[1])) {
            System.out.println("1. Accept  2. Reject  3. Ignore for now");
            String choice = readOption("1", "2", "3");
            action = choice.equals("3") ? "4" : choice;
          } else {
            System.out.println("1. Accept  2. Reject  3. Accept and follow back  4. Ignore for now");
            action = readOption("1", "2", "3", "4");
          }
          break;
        case "download":
          System.out.println("1. Approve  2. Reject  3. Ignore for now");
          action = readOption("1", "2", "3");
          break;
        case "AcceptedDownload":
          System.out.println("Would you like to download the photo right now? [y/n]");
          action = readOption("y", "n");
          break;
        case "comment":
          if (parts.length == 4) {
            System.out.println("Comment: " + parts[3].replace(',', ' ').replace("[", "").replace("]", ""));
          }
          System.out.println("Press Enter to return to notifications.");
          myObj.nextLine();
          break;
        case "NewComment":
          if (parts.length == 4) {
            try { System.out.println("Comment: " + new String(java.util.Base64.getDecoder().decode(parts[3]), java.nio.charset.StandardCharsets.UTF_8)); }
            catch (IllegalArgumentException ignored) { }
          }
          System.out.println("Press Enter to return to notifications.");
          myObj.nextLine();
          break;
        default:
          System.out.println("Press Enter to return to notifications.");
          myObj.nextLine();
      }
      out.writeObject(action); out.flush();
      if (parts[0].equals("AcceptedDownload") && action.equals("y")) {
        String status = (String) in.readObject();
        if (status.equals("READY")) {
          try { downloadSomething(parts[2], false); }
          catch (IOException | ClassNotFoundException | InterruptedException e) {
            System.out.println("Download failed: " + e.getMessage());
            throw e;
          }
        } else System.out.println(status);
      }
    }
  }

  private String readOption(String... options) {
    while (true) {
      String choice = myObj.nextLine().trim();
      for (String option : options) if (option.equals(choice)) return choice;
      System.out.println("Invalid choice. Try again:");
    }
  }

  private String displayUsername(String id) {
    return notificationUsernames.getOrDefault(id, id);
  }

  private String notificationLabel(String entry) {
    String[] parts = entry.split("\\s+", 4);
    if (parts.length < 2) return entry;
    switch (parts[0]) {
      case "follow": return "Follow request from " + displayUsername(parts[1]);
      case "FollowAccepted": return "Follow request accepted by " + displayUsername(parts[1]);
      case "FollowRejected": return "Follow request rejected by " + displayUsername(parts[1]);
      case "download": return parts.length >= 3 ? "Download request from " + displayUsername(parts[1]) + " for " + displayImageName(parts[2]) : entry;
      case "AcceptedDownload": return parts.length >= 3 ? "Download approved by " + displayUsername(parts[1]) + " for " + displayImageName(parts[2]) : entry;
      case "RejectedDownload": return parts.length >= 3 ? "Download rejected by " + displayUsername(parts[1]) + " for " + displayImageName(parts[2]) : entry;
      case "comment": return parts.length >= 3 ? "New comment from " + displayUsername(parts[1]) + " on " + displayImageName(parts[2]) : entry;
      case "NewComment": return parts.length >= 3 ? "New comment from " + displayUsername(parts[1]) + " on " + displayImageName(parts[2]) : entry;
      default: return entry;
    }
  }

  // Empty client directories after login for a new session
  public static void emptyFolder(File folder) {

    // Get all the files from the folder
    File[] files = folder.listFiles();

    // Check if it is file and if it is delete it
    if(files!=null) {

      for(File f: files) {

        if(f.isDirectory()) {

          emptyFolder(f);

        }else{

          if (!f.delete()){
            System.out.println("Failed to delete file.");
          }

        }

      }

    }

  }

  // Updates client's local files after login
  private void updateLocalFiles() throws IOException, ClassNotFoundException, InterruptedException {

    // Empty client directories
    emptyFolder(imageDirectory().toFile());
    emptyFolder(profileDirectory().toFile());

    String fileName = "";
    boolean finishedDownload = false;

    while(!finishedDownload){

      fileName = (String)in.readObject();

      if(fileName.equals("NotFound") || fileName.equals("DONE")){
        finishedDownload = true;

      }else{
        downloadSomething(fileName, true);
      }
    }

    copyProfileFromServer();

  }

  // General function to download a file from server to client images and bind txt files
  private void downloadSomething(String imgName, Boolean isLoginOrSignup) throws IOException, ClassNotFoundException, InterruptedException {

      // Array to keep track of the Acknowledgements not sent
      // The value firstTimeReceivingPackage[0] tells me whether I already received the 3rd package and so on
      boolean[] firstTimeReceivingPackage = {true,true, false,true,true,false,false,false,false,false};

      // 9.g message from server
      System.out.println("Txt status from server:\n" + in.readObject()+ "\n");

      int expectedSeqNum = 0;
      Map<Integer, byte[]> receivedPackets = new TreeMap<>();

      int delayedAcknowledgementsCounter = 0;

      while (receivedPackets.size() < 10) {
        Packet packet = (Packet) in.readObject();
        System.out.println("--------------------------------------");
        System.out.println("Received packet #" + packet.sequenceNumber);

        if (packet.sequenceNumber == expectedSeqNum) {

          if((expectedSeqNum == 2 || ( (expectedSeqNum >= 5) && (expectedSeqNum < 10 ) ) )
                  && delayedAcknowledgementsCounter <= 5
                  && !firstTimeReceivingPackage[expectedSeqNum]
                  && !isLoginOrSignup){

            System.out.println("--------------------------------------");
            System.out.println("Didn't send acknowledgement on purpose");
            firstTimeReceivingPackage[expectedSeqNum] = true;
            delayedAcknowledgementsCounter++;
            continue;
          }

          System.out.println("Sending Ack "+ expectedSeqNum + "");
          receivedPackets.put(packet.sequenceNumber, packet.data);

          out.writeObject("ACK" + packet.sequenceNumber);
          out.flush();

          expectedSeqNum++;

        } else {
          // Re-ack the last correct packet to help server
          out.writeObject("ACK" + (expectedSeqNum - 1));
          out.flush();
        }
      }

      // Reconstruct the image and description
      ByteArrayOutputStream combined = new ByteArrayOutputStream();
      for (int i = 0; i < 10; i++) {
          combined.write(receivedPackets.get(i));
      }

      byte[] fullData = combined.toByteArray();

      // First byte = description length
      int descLength = fullData[0] & 0xFF;  // unsigned byte to int

      byte[] descriptionBytes = new byte[descLength];
      System.arraycopy(fullData, 1, descriptionBytes, 0, descLength);
      String description = new String(descriptionBytes);

      byte[] imageBytes = new byte[fullData.length - 1 - descLength];
      System.arraycopy(fullData, 1 + descLength, imageBytes, 0, imageBytes.length);

      // Save image
      FileOutputStream fos = new FileOutputStream(imageDirectory().resolve(imgName).toString());

      // Create txt with description given
      File file = new File(imageDirectory().resolve(imgName.substring(0, imgName.lastIndexOf('.')) + ".txt").toString());
      if ( !file.createNewFile() ) {
          System.out.println("\nFile already exists.");
      }
      FileWriter fw = new FileWriter(file);
      fw.write(description);
      fw.close();

      // Update profile.txt
      if(!isLoginOrSignup){
        FileWriter proFileServerWriter = new FileWriter(profileDirectory().resolve("Profile_" + GroupId + clientId + ".txt").toString()	,true);
        proFileServerWriter.append("\n");
        proFileServerWriter.append(currentUsername).append(" posted ").append(imgName);
        proFileServerWriter.close();
      }

      fos.write(imageBytes);
      fos.close();

    Object serverOutput = in.readObject();
    while (!(serverOutput instanceof String str)){
      System.out.println("Wrong Server Message, transmission still open. Message was "+ serverOutput);
      serverOutput = in.readObject();
    }

    if ( str.equals("Transmission Complete")) {
      System.out.println("\nThe transmission is completed!");
    } else {
      System.out.println("Wrong Server Message but it's a string " + str);
    }

  }

  // Option EXIT for both menus
  // Sign out Client and terminate everything necessary
  public void stopClient() {

    try {

      myObj.close();

      loginFlag = true;
      menuFlag = true;

      in.close();
      out.close();

      connection.close();

      System.out.println("Connection closed");

    } catch (IOException e) {
      System.out.println(e.getMessage());
    }

  }

  // Clean all client files for new user
  private void fixNewUserlFiles() throws IOException {

    // Empty client directories
    emptyFolder(imageDirectory().toFile());
    emptyFolder(profileDirectory().toFile());

    try {

      File file = new File(profileDirectory().resolve("Profile_" + GroupId + clientId + ".txt").toString());
      file.createNewFile();
      System.out.println("File: " + file + " created.");

      File file2 = new File(profileDirectory().resolve("Others_" + GroupId + clientId + ".txt").toString());
      file2.createNewFile();
      System.out.println("File: " + file2 + " created.");

    }catch(Exception e) {
      e.printStackTrace();
    }
  }

  private void copyProfileFromServer() throws ClassNotFoundException, IOException{

    String content = (String) in.readObject();
    Files.writeString(Paths.get(profileDirectory().resolve("Profile_" + GroupId + clientId + ".txt").toString()), content);
    System.out.println("Received Profile_" + GroupId + clientId + ".txt");

    String content2 = (String) in.readObject();
    Files.writeString(Paths.get(profileDirectory().resolve("Others_" + GroupId + clientId + ".txt").toString()), content2);
    System.out.println("Received Others_" + GroupId + clientId + ".txt");

  }

  // ----------------------------------------------------------------------------------------------------------------------------------------------------------

}