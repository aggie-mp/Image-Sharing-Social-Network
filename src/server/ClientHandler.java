package server;

import common.Packet;
import common.SenderState;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ClientHandler extends Thread {
  
    // Sockets
    private final Socket clientSocket;

    // Loaders
    private final UsersLoader usersLoader = new UsersLoader(ProjectPaths.dataFile("users.txt"));
    SocialGraphLoader socialLoader = new SocialGraphLoader();

    // Streams
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;


    // Menu Flags
    private boolean loginFlag = false;
    private boolean menuFlag = false;

    // Client ID
    private String clientId;

    // Group ID
    private final String GroupId = "45";

    // Map with locks per file
    private static final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    //Volatile flag to stop GoBackN packets after download is completed
    private volatile boolean stopResending = false;


    // Constructor
    public ClientHandler(Socket socket) throws IOException {
    this.clientSocket = socket;
    }

    public void run() {

        try {
            // Streams
            outStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inStream = new ObjectInputStream(clientSocket.getInputStream());
            outStream.flush();

            // connection message
            String clientMessage = (String) inStream.readObject();
            System.out.println("client.Client message: " + clientMessage);

            // Login Menu
            while (!loginFlag) {

                // user login menu Selection
                String actionCode = (String) inStream.readObject();
                System.out.println("client.Client message: " + actionCode);

                switch (actionCode) {

                case "1":
                    login();
                    break;

                case "2":
                    signup();
                    break;

                case "3":
                    // Exit
                    this.stopClientHandler();
                    break;

                }

            }


            // Main Menu
            while (!menuFlag) {

                // Read menu action
                String actionCode = (String) inStream.readObject();
                System.out.println("client.Client message: " + actionCode);

                switch (actionCode) {

                    case "1":
                        handleUpload();
                        break;

                    case "2":
                        handleSearch();
                        break;

                    case "3":
                        handleFollow();
                        break;

                    case "4":
                        handleUnfollow();
                        break;

                    case "5":
                        handleAccessProfile();
                        break;

                    case "6":
                        // Manual notification check, synchronized with the client menu.
                        checkNotifications();
                        break;

                    case "7":
                        //Exit
                        this.stopClientHandler();
                        break;

                    default:
                        System.out.println("Wrong input for menu action!");
                        break;
                }
            }

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
        }

    }


    // LOGIN MENU FUNCTIONS ------------------------------------------------------------------------------------------------------------------------------------

    // Login Menu Option 1
    // Login
    private void login() throws IOException, ClassNotFoundException {

    // Read username and password
    String userName = (String) inStream.readObject();
    String password = (String) inStream.readObject();

    // Search for client's ID and successfully login, from users.txt using username and password
    clientId = usersLoader.checkUser(userName, password);

    if (clientId != null) {

      // Send success login response
      outStream.writeObject("SuccessLogin");
      outStream.flush();

      // Send client ID
      outStream.writeObject(clientId);
      outStream.flush();

      // stop login menu
      loginFlag = true;

      // Save client's IP and port to server
      Server.clientDirectory.put(clientId, clientSocket.getRemoteSocketAddress());
      System.out.println("\nOnline clients: ");
      Server.clientDirectory.forEach((key, value) -> System.out.println(usersLoader.getUserName(key) + " " +  key + " " + value));

      updateClientsDirectoryFiles();
      updateClientProfileFiles();

    } else {
      outStream.writeObject("FailedLogin");
      outStream.flush();

      // resetLogin to retry
      this.login();
    }
    }

    // Login Menu Option 2
    // Sign Up
    private void signup() throws IOException, ClassNotFoundException {
        // Read username and password
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();

        // Check if username already exists
        if (usersLoader.getUserInfo(userName).isEmpty()) {

            // Generate a random 4-digit client ID and ensure it is unique
            String tempId = String.format("%04d", (int) (Math.random() * 10000));

            while (!usersLoader.getUserName(tempId).isEmpty()) {
                tempId = String.format("%04d", (int) (Math.random() * 10000));
            }

          clientId = tempId;

          // Add user to users.txt
          String formattedInfo = userName + ":" + password + "," + clientId;
          usersLoader.addUser(formattedInfo);

          // Send success signup response
          outStream.writeObject("SuccessSignUp");
          outStream.flush();

          // Send client ID
          outStream.writeObject(clientId);
          outStream.flush();

          // Save client's IP and port to server
          Server.clientDirectory.put(clientId, clientSocket.getRemoteSocketAddress());
          System.out.println("\nOnline clients: ");
          Server.clientDirectory.forEach((key, value) -> System.out.println(usersLoader.getUserName(key) + " " +  key + " " + value));

          fixNewUserFiles();

          // Stop login menu
          loginFlag = true;

        } else {
          // Send failed signup response
          outStream.writeObject("Failed");
          outStream.flush();

          // resetSignup
          this.signup();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------


    // MAIN MENU -----------------------------------------------------------------------------------------------------------------------------------------------

    // Menu option 1
    // Upload
    private void handleUpload() throws IOException, ClassNotFoundException {

        // Check handshake
        if (uploadHandshake()) {

            System.out.println("Upload sequence initiated");
            Map<Integer, byte[]> receivedPackets = new TreeMap<>();

            String imgNameGiven = (String) inStream.readObject();

            // Check if the client cancelled the upload
            if ("UPLOAD_CANCELLED".equals(imgNameGiven)) {
                System.out.println("Upload cancelled by client: " + clientId);
                System.out.println("Returning to Main Menu.");
                return;
            }

            System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(filename)");
            String[] imgNameArray = imgNameGiven.split("\\.");

            // Receive image description
            String description = (String) inStream.readObject();


            // Receive 10 packets
            for (int i = 0; i < 10; i++) {
                Packet packet = (Packet) inStream.readObject();
                System.out.println("Received packet #" + packet.sequenceNumber);
                receivedPackets.put(packet.sequenceNumber, packet.data);

                // Send ACK
                outStream.write(("ACK" + packet.sequenceNumber + "\n").getBytes());
                outStream.flush();
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

            byte[] imageBytes = new byte[fullData.length - 1 - descLength];
            System.arraycopy(fullData, 1 + descLength, imageBytes, 0, imageBytes.length);

            // Save image
            FileOutputStream fos = new FileOutputStream(ProjectPaths.serverRoot() + "/directories/" + "directory_" + GroupId + clientId + "/" + imgNameGiven);

            // Create txt with description given
            File file = new File(ProjectPaths.serverRoot() + "/directories/" + "directory_" + GroupId + clientId + "/" + imgNameArray[0] + ".txt");
            if ( !file.createNewFile() ) {
                System.out.println("File already exists.");
            }

            FileWriter fw = new FileWriter(file);
            fw.write(description);
            fw.close();

            String profileTxtpath = ProjectPaths.serverRoot() + "/profiles/" + "Profile_" + GroupId + clientId + ".txt";

            // lock profile.txt to prevent other clients from editing
            lockFile(profileTxtpath);
            // update profile.txt
            FileWriter proFileServerWriter = new FileWriter(profileTxtpath, true);
            proFileServerWriter.append("\n");
            proFileServerWriter.append(usersLoader.getUserName(clientId)).append(" posted ").append(imgNameGiven);
            proFileServerWriter.close();
            // unlock profile.txt
            unlockFile(profileTxtpath);


            String[] following = socialLoader.getFollowers(clientId);
            for (String myFollowing : following) {

                String otherTxtpath = ProjectPaths.serverRoot() + "/profiles/" + "Others_" + GroupId + myFollowing + ".txt";
                // lock others.txt to prevent other clients from editing
                lockFile(otherTxtpath);
                // update others.txt
                FileWriter proFileServerWriter2 = new FileWriter(otherTxtpath, true);
                proFileServerWriter2.append("\n");
                proFileServerWriter2.append(usersLoader.getUserName(clientId)).append(" posted ").append(imgNameGiven);
                proFileServerWriter2.close();
                // unlock others.txt
                unlockFile(otherTxtpath);
            }

            // Write image
            fos.write(imageBytes);
            fos.close();


        } else {
            System.out.println("Handshake Failed! Try again :(");
        }

    }

    // Handshake for upload
    private boolean uploadHandshake() throws IOException, ClassNotFoundException {

        String handshakeResponse = (String) inStream.readObject();

        System.out.println("\nHANDSHAKE STEP 1: Client sent request");

        if (handshakeResponse.equals("Request to upload")) {

            outStream.writeObject("acceptedUpload");
            System.out.println("\nHANDSHAKE STEP 2: Server sent acknowledgement");

            return true;

        } else {

            System.out.println("\nHANDSHAKE REJECTED");
            outStream.writeObject("rejected");

            return false;

        }
    }


    // Menu Option 2
    // Search
    private void handleSearch() throws IOException, ClassNotFoundException, InterruptedException {

        // read input image name from client
        String searchImgName = (String) inStream.readObject();

        ArrayList<String> following = socialLoader.getFollowing(clientId);

        ArrayList<String[]> results = new ArrayList<>();
        boolean foundExactMatch = false;

        String profileTxtpath;
        for (String tempclientId : following) {
            profileTxtpath = ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + tempclientId + ".txt";

            try {

                lockFile(profileTxtpath);

                BufferedReader reader = new BufferedReader(new FileReader(profileTxtpath));
                String line;

                while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {

                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        String photoFullName = line.split("\\s+")[2];
                        if (photoFullName.trim().toLowerCase().contains(searchImgName.trim().toLowerCase())) {
                            boolean alreadyAdded = false;

                            for (String[] result : results) {
                                if (result[0].equals(tempclientId)
                                        && result[2].equals(photoFullName)) {
                                    alreadyAdded = true;
                                    break;
                                }
                            }

                            if (!alreadyAdded) {
                                results.add(new String[]{
                                        tempclientId,
                                        usersLoader.getUserName(tempclientId),
                                        photoFullName
                                });
                            }

                        }
                    }
                }

                reader.close();
                unlockFile(profileTxtpath);

            } catch (Exception e) {
                unlockFile(profileTxtpath);
                System.out.println(e.getMessage());
            }
        }
        outStream.writeObject(results);

        if (!results.isEmpty()) {
            String next;
            do {
                String action = (String) inStream.readObject();
                if (action.equals("done")) break; // User exited Search without a download.
                if (action.equals("download")) handleDownload(results);
                next = (String) inStream.readObject();
            } while (next.equals("again"));
        }
    }

    private void handleDownload(ArrayList<String[]> imageInfo) throws ClassNotFoundException, IOException {
        if (!downloadHandshake()) return;
        int index = (int) inStream.readObject();
        String status = "Invalid image selection";
        if (index >= 0 && index < imageInfo.size()) {
            String[] selected = imageInfo.get(index);
            String owner = selected[0];
            String image = selected[2];
            if ((owner.equals(clientId) || socialLoader.getFollowing(clientId).contains(owner))
                    && validImage(owner, image)) {
                appendNotification(owner, "download " + clientId + " " + image);
                status = "Download request sent. You will be notified when the owner responds.";
            } else status = "Download request denied: image unavailable or access not allowed.";
        }
        outStream.writeObject(status);
        outStream.flush();
    }

    private boolean validImage(String owner, String image) {
        if (image.contains("/") || image.contains("\\") || image.contains("..")) return false;
        return Files.isRegularFile(Paths.get(ProjectPaths.serverRoot(), "directories",
                "directory_" + GroupId + owner, image));
    }

    private String notificationPath(String user) {
        return ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + user + "/notifications.txt";
    }

    private void appendNotification(String user, String entry) throws IOException {
        String filename = notificationPath(user);
        lockFile(filename);
        try {
            Files.writeString(Paths.get(filename), entry + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } finally { unlockFile(filename); }
    }

    private List<String> readNotifications() throws IOException {
        String filename = notificationPath(clientId);
        lockFile(filename);
        try { return new ArrayList<>(Files.readAllLines(Paths.get(filename))); }
        finally { unlockFile(filename); }
    }

    private boolean removeNotification(String entry) throws IOException {
        String filename = notificationPath(clientId);
        lockFile(filename);
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(Paths.get(filename)));
            if (!lines.remove(entry)) return false;
            Files.write(Paths.get(filename), lines);
            return true;
        } finally { unlockFile(filename); }
    }

    private void completeApprovedDownload(String notification) throws IOException {
        String[] parts = notification.split("\\s+", 3);
        if (parts.length != 3 || !parts[0].equals("AcceptedDownload")
                || !(parts[1].equals(clientId) || socialLoader.getFollowing(clientId).contains(parts[1]))
                || !validImage(parts[1], parts[2])) {
            outStream.writeObject("Download unavailable or access revoked.");
            outStream.flush();
            return;
        }
        String image = parts[2];
        String description = image.substring(0, image.lastIndexOf('.')) + ".txt";
        outStream.writeObject("READY");
        outStream.flush();
        downloadSomething(image, description, parts[1]);
        String profile = ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + clientId + ".txt";
        lockFile(profile);
        try (FileWriter writer = new FileWriter(profile, true)) {
            writer.append(System.lineSeparator()).append(usersLoader.getUserName(clientId)).append(" reposted ").append(image);
        } finally { unlockFile(profile); }
        String source = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + parts[1];
        String target = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId;
        copyFiles(source, target, new String[]{image, description});
        removeNotification(notification);
    }

    private String askPermission(String imageName, String IdToDownloadFrom) throws InterruptedException, IOException {

        String pathToWrite = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + IdToDownloadFrom + "/notifications.txt";
        lockFile(pathToWrite);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
            writer.write("download " + clientId + " " + imageName);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to " + pathToWrite);
        }
        unlockFile(pathToWrite);

        boolean otherClientAccepted = false;
        String myNotificationsPath = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId + "/notifications.txt";
        String expectedAccepted = "AcceptedDownload " + IdToDownloadFrom + " " + imageName;
        String expectedRejected = "RejectedDownload " + IdToDownloadFrom + " " + imageName;
        while (true) {
            System.out.println("Waiting for response...");
            Thread.sleep(5000);
            lockFile(myNotificationsPath);
            try {
                Path path = Paths.get(myNotificationsPath);
                List<String> lines = Files.readAllLines(path);
                for (int i = 0; i < lines.size(); i++) {
                    String response = lines.get(i).trim();
                    if (response.equals(expectedAccepted) || response.equals(expectedRejected)) {
                        otherClientAccepted = response.equals(expectedAccepted);
                        lines.remove(i);
                        Files.write(path, lines);
                        System.out.println("Consumed download response: " + response);
                        return otherClientAccepted ? "Accepted" : "Rejected";
                    }
                }
            } finally {
                unlockFile(myNotificationsPath);
            }
        }
    }

    /** Remove exactly the download requests answered in this session. */
    private void deleteAnsweredDownloadRequests(List<String> notifications) throws IOException {
        String filename = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId + "/notifications.txt";
        lockFile(filename);
        try {
            Path path = Paths.get(filename);
            List<String> lines = Files.readAllLines(path);
            for (String notification : notifications) {
                String[] parts = notification.split("\\s+", 2);
                if (parts.length != 2) continue;
                lines.remove("download " + parts[0] + " " + parts[1]);
            }
            Files.write(path, lines);
        } finally {
            unlockFile(filename);
        }
    }

    // Handshake for download
    private boolean downloadHandshake() throws IOException, ClassNotFoundException {
      String handshakeResponse = (String) inStream.readObject();
      System.out.println("HANDSHAKE STEP 1: Client sent request");
      if (handshakeResponse.equals("request to download")) {
          outStream.writeObject("acceptedDownload");
          System.out.println("HANDSHAKE STEP 2: Server sent acknowledgement");
          return true;
      } else {
          outStream.writeObject("rejected");
          return false;
      }
    }

    // 9.h
    private void copyFiles(String sourceDest, String targetDest, String[] filesToCopyArr) {
      Path sourceDir = Paths.get(sourceDest);
      Path targetDir = Paths.get(targetDest);

      for (String fileName : filesToCopyArr) {
        Path sourceFile = sourceDir.resolve(fileName);
        Path targetFile = targetDir.resolve(fileName);
        try {
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("\n" + "Copied: " + sourceFile + " to " + targetFile + "\n");
        } catch (IOException e) {
            System.err.println("\n" + "Failed to copy " + fileName + ": " + e.getMessage() + "\n");
        }
      }
    }


    // Menu Option 3
    // Follow
    private void handleFollow() throws IOException, ClassNotFoundException {
      String response;
      // read input from client
      String userToFollow = (String) inStream.readObject();
      String userToFollowId = usersLoader.getUserId(userToFollow);
      if (userToFollowId.isEmpty()) {
          response = "User not found! Try again.";
      } else if (userToFollowId.equals(clientId)) {
          response = "You cannot follow yourself.";
      } else if (socialLoader.getFollowing(clientId).contains(userToFollowId)) {
          response = "You are already following " + userToFollow + ".";
      } else {
          String notificationFile = notificationPath(userToFollowId);
          lockFile(notificationFile);
          try {
              List<String> pending = Files.readAllLines(Paths.get(notificationFile));
              if (pending.contains("follow " + clientId)) {
                  response = "You have already sent a follow request to " + userToFollow + ".";
              } else {
                  try (BufferedWriter writer = new BufferedWriter(new FileWriter(notificationFile, true))) {
                      writer.write("follow " + clientId);
                      writer.newLine();
                  }
                  response = "Follow request sent successfully!";
              }
          } finally {
              unlockFile(notificationFile);
          }
      }
      outStream.writeObject(response);

    }

    private void sendFollowRequests(String clientId, ArrayList<String> sendTo){
      for (String sendToId : sendTo){
          String filePath = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + sendToId + "/notifications.txt";
          try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
              writer.write("follow " + clientId);
              writer.newLine();
          } catch (IOException e) {
              System.err.println("Failed to write to " + filePath);
          }
      }
    }


    // Menu option 4
    private void handleUnfollow() throws ClassNotFoundException, IOException{
      String response;

      // read input from client
      String userToUnFollow = (String) inStream.readObject();
      String userToUnFollowId = usersLoader.getUserId(userToUnFollow);

      if (userToUnFollowId.isEmpty()){
          response = "The user you are trying to unfollow does not exist! Try again.";
      }else{
          response = socialLoader.unfollowUser(clientId, userToUnFollowId);
          System.out.println("User " + clientId + " successfully unfollowed user " + userToUnFollow);
      }
      outStream.writeObject(response);
    }


    // Menu option 5
    // Handle Access Profile
    private void handleAccessProfile() throws IOException, ClassNotFoundException, InterruptedException {
        boolean flag = true;


        // Build a list containing the user's own profile
        // and the profiles they follow
        List<String> usersList = new ArrayList<>();

        String ownUsername = usersLoader.getUserName(clientId);
        usersList.add(ownUsername);

        ArrayList<String> followingIds = socialLoader.getFollowing(clientId);

        for (String followedId : followingIds) {
            String followedUsername = usersLoader.getUserName(followedId);

            if (!followedUsername.isEmpty()
                    && !usersList.contains(followedUsername)) {
                usersList.add(followedUsername);
            }
        }

        // Send available profiles to client
        outStream.writeObject(new ArrayList<>(usersList));
        outStream.flush();


        while (flag) {

            // Read the profile name client wants to access
            String profileName = (String) inStream.readObject(); // ProfileName, client requested access

            System.out.println("Client wants to access profile " + profileName);

            ArrayList<String> following = socialLoader.getFollowing(clientId);
            String selectedID = usersLoader.getUserInfo(profileName).get(1); // ProfileID, client requested access

            if (selectedID.equals(clientId) || following.contains(selectedID)) {
                outStream.writeObject("access_approved");
                outStream.flush();

                //File profileFile = new File(ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + selectedID);

                String content = Files.readString(Paths.get(ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + selectedID + ".txt"));
                outStream.writeObject(displayProfile(content));
                outStream.flush();

                lockFile(ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + selectedID + ".txt");
                BufferedReader reader = new BufferedReader(new FileReader(ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + selectedID + ".txt"));
                String line;

                // Keep all available images to download from profile
                ArrayList<String[]> imgsToDownload = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    ArrayList<String> splitLine = new ArrayList<>(Arrays.asList(line.split("\\s+")));

                    if (splitLine.size() == 3) {
                        if (splitLine.get(1).equals("posted") || splitLine.get(1).equals("reposted")) {
                            imgsToDownload.add(new String[]{selectedID, profileName, splitLine.get(2)});
                        }
                    }
                }

                reader.close();

                unlockFile(ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + selectedID + ".txt");

                // Send img list to client
                outStream.writeObject(imgsToDownload);
                outStream.flush();

                // Allow multiple actions in the same profile without returning to the main menu.
                String selectedAction;
                while (!(selectedAction = (String) inStream.readObject()).equals("done")) {
                    if (!selectedID.equals(clientId)) {
                        if (selectedAction.equals("download")) handleDownload(imgsToDownload);
                        else if (selectedAction.equals("comment")) handleComment(selectedID);
                    }
                }


            } else {
                outStream.writeObject("access_denied");
                outStream.flush();
            }

            if (((String) inStream.readObject()).equals("no_retry")) flag = false;
        }
    }

    private void handleComment(String uploaderId) throws IOException, ClassNotFoundException {
        String imageName = (String) inStream.readObject();
        String comment = (String) inStream.readObject();
        if (uploaderId.equals(clientId) || !socialLoader.getFollowing(clientId).contains(uploaderId)
                || !validImage(uploaderId, imageName) || comment.isBlank()) {
            outStream.writeObject("Rejected");
            outStream.flush();
            return;
        }
        String profile = ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + clientId + ".txt";
        lockFile(profile);
        try (FileWriter writer = new FileWriter(profile, true)) {
            writer.append(System.lineSeparator()).append(usersLoader.getUserName(clientId)).append(" commented ")
                    .append(comment.replace('\n', ' ')).append(" ").append(imageName)
                    .append(" ").append(usersLoader.getUserName(uploaderId));
        } finally { unlockFile(profile); }
        if (!uploaderId.equals(clientId)) {
            String encoded = java.util.Base64.getEncoder().encodeToString(comment.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            appendNotification(uploaderId, "NewComment " + clientId + " " + imageName + " " + encoded);
        }
        outStream.writeObject("Accepted");
        outStream.flush();
    }

    private String askCommentPermission(String imageName,String comment, String IdToDownloadFrom) throws InterruptedException, IOException {

        String pathToWrite = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + IdToDownloadFrom + "/notifications.txt";
        lockFile(pathToWrite);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
            writer.write("comment " + clientId + " " + imageName + " " + comment);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to " + pathToWrite);
        }
        unlockFile(pathToWrite);

        boolean hasResponseArrived = false;
        boolean otherClientAccepted = false;
        String myNotificationsPath = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId + "/notifications.txt";
        while(!hasResponseArrived){
            System.out.println("Waiting for response...");
            Thread.sleep(5000);

            lockFile(myNotificationsPath);
            try (BufferedReader reader = new BufferedReader(new FileReader(myNotificationsPath))) {

                String line;
                String notificationType;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    System.out.println("Line: " + line);

                    String[] splitNotification = line.split("\\s+");
                    notificationType = splitNotification[0];
                    System.out.println("33Notification: " + splitNotification[0] + " " + splitNotification[1]);
                    if ((splitNotification.length != 4) ) continue;

                    System.out.println("36Notification: " + splitNotification[0] + " " + splitNotification[1]);

                    if (notificationType.equals("RejectedComment") && splitNotification[1].equals(imageName)) {
                        hasResponseArrived = true;
                        break;
                    } else if (notificationType.equals("AcceptedComment") && splitNotification[1].equals(imageName)){
                        hasResponseArrived = true;
                        otherClientAccepted = true;
                        break;
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            unlockFile(myNotificationsPath);
        }
        System.out.println("Response received: " + otherClientAccepted);
        deleteCommentNotification();
        return otherClientAccepted? "Accepted" : "Rejected";
    }

    private void deleteCommentNotification() throws IOException {
        Path path = Paths.get(ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId + "/notifications.txt");
        List<String> lines = Files.readAllLines(path);
        List<String> updatedLines = new ArrayList<>();

        boolean notificationFound = false;

        for (String line : lines) {

            String[] parts = line.trim().split("\\s+");

            if ( parts.length > 0 && ( parts[0].equals("RejectedComment") || parts[0].equals("AcceptedComment") )) {
                notificationFound = true;
                continue;
            }

            updatedLines.add(line);
        }

        if(notificationFound) {
            // Rewrite the file
            Files.write(path, updatedLines);
            System.out.println("Deleted notification.");

        }

    }

    private void sendCommentResponse(ArrayList<String[]> commentResponses) {
        String pathToWrite;
        for (String[] responseInfo : commentResponses){
            pathToWrite = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + responseInfo[0] + "/notifications.txt";
            lockFile(pathToWrite);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
                writer.write(responseInfo[1]);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write to " + pathToWrite);
            }
            unlockFile(pathToWrite);
        }

    }

    private void sendDownloadResponse(ArrayList<String[]> downloadResponses) {

        String pathToWrite;
        for (String[] responseInfo : downloadResponses){
            pathToWrite = ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + responseInfo[0] + "/notifications.txt";
            lockFile(pathToWrite);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
                writer.write(responseInfo[1]);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write to " + pathToWrite);
            }
            unlockFile(pathToWrite);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------


    // GENERAL FUNCTIONS -------------------------------------------------------------------------------------------------------------------

    // Sync all files from server to client
    private void updateClientsDirectoryFiles() throws IOException {

        System.out.println("Synchronizing files with client...");

        File directoryFolder = new File(ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId);
        File[] files = directoryFolder.listFiles();

        boolean imgTag;
        String fileName;
        String descriptionName;

        if (files != null) {

          for (File file : files) {
            fileName = file.getName();
            imgTag = fileName.contains(".jpg") || fileName.contains(".png") || fileName.contains(".jpeg") || fileName.contains(".JPG") || fileName.contains(".PNG") || fileName.contains(".JPEG");

            if (imgTag) {

                outStream.writeObject(fileName);
                descriptionName = fileName.split("\\.")[0] + ".txt";
                downloadSomething(fileName, descriptionName, clientId);

            }
          }

          // Send DONE
          outStream.writeObject("DONE");

        } else {
          outStream.writeObject("NotFound");
        }

    }

    // Sync profile file from server to client
    private void updateClientProfileFiles() throws IOException{
        String sourceFile = ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + clientId + ".txt";
        String content = Files.readString(Paths.get(sourceFile));
        outStream.writeObject(displayProfile(content));
        System.out.println("Sending Profile_" + GroupId + clientId + ".txt");

        String sourceFile2 = ProjectPaths.serverRoot() + "/profiles/Others_" + GroupId + clientId + ".txt";
        String content2 = Files.readString(Paths.get(sourceFile2));
        outStream.writeObject(content2);
        System.out.println("Sending Others_" + GroupId + clientId + ".txt");
    }

    // Sign out client and terminate everything necessary
    public void stopClientHandler() {

        try {
          // Remove client from online clients in Server
          Server.clientDirectory.remove(clientId);
          System.out.println("\nOnline clients: ");
          Server.clientDirectory.forEach((key, value) -> System.out.println(usersLoader.getUserName(key) + " " +  key + " " + value));
          System.out.println("\n");

          loginFlag = true;
          menuFlag = true;
          inStream.close();
          outStream.close();

          System.out.println("Connection closed");

        } catch (IOException e) {
          System.out.println(e.getMessage());
        }

    }

    // Convert IDs to display names without changing stored protocol records.
    private String displayUser(String id) {
        String name = usersLoader.getUserName(id);
        return name.isEmpty() ? id : name;
    }

    private String displayProfile(String raw) {
        StringBuilder result = new StringBuilder();
        for (String line : raw.split("\\R", -1)) {
            String[] tokens = line.split("\\s+", 2);
            if (tokens.length == 2 && tokens[0].matches("\\d{4}")) {
                line = displayUser(tokens[0]) + " " + tokens[1];
                // Comment records end with the owner ID.
                if (tokens[1].startsWith("commented ")) {
                    int lastSpace = line.lastIndexOf(' ');
                    if (lastSpace >= 0) {
                        String owner = line.substring(lastSpace + 1);
                        if (owner.matches("\\d{4}"))
                            line = line.substring(0, lastSpace + 1) + displayUser(owner);
                    }
                }
            }
            if (result.length() > 0) result.append(System.lineSeparator());
            result.append(line);
        }
        return result.toString();
    }

    // Check notifications in notifications.txt after successful login
    private void checkNotifications() throws IOException, ClassNotFoundException {
        while (true) {
            List<String> all = readNotifications();
            all.removeIf(String::isBlank);
            Map<String, String> displayNames = new HashMap<>();
            for (String username : usersLoader.getAllUsers()) {
                displayNames.put(usersLoader.getUserId(username), username);
            }
            outStream.writeObject(displayNames);
            outStream.writeObject(new HashSet<>(socialLoader.getFollowing(clientId)));
            outStream.writeObject(new ArrayList<>(all));
            outStream.flush();
            String command = (String) inStream.readObject();
            if (command.equals("EXIT")) return;
            if (!command.startsWith("OPEN ")) continue;
            int index;
            try { index = Integer.parseInt(command.substring(5)); }
            catch (NumberFormatException e) { continue; }
            if (index < 0 || index >= all.size()) continue;
            String notification = all.get(index);
            String[] fields = notification.split("\\s+", 4);
            String action = (String) inStream.readObject();
            if (fields.length < 2) continue;
            switch (fields[0]) {
                case "follow":
                    if (action.equals("1") || action.equals("3")) {
                        ArrayList<String> accepted = new ArrayList<>();
                        accepted.add(fields[1]);
                        socialLoader.acceptFollowRequests(clientId, accepted);
                        if (action.equals("3") && !socialLoader.getFollowing(clientId).contains(fields[1]))
                            sendFollowRequests(clientId, accepted);
                        removeNotification(notification);
                        appendNotification(fields[1], "FollowAccepted " + clientId);
                    } else if (action.equals("2")) {
                        removeNotification(notification);
                        appendNotification(fields[1], "FollowRejected " + clientId);
                    }
                    break;
                case "download":
                    if (fields.length >= 3 && (action.equals("1") || action.equals("2"))) {
                        appendNotification(fields[1], (action.equals("1") ? "AcceptedDownload " : "RejectedDownload ")
                                + clientId + " " + fields[2]);
                        removeNotification(notification);
                    }
                    break;
                case "AcceptedDownload":
                    if (action.equals("y")) completeApprovedDownload(notification);
                    break;
                case "RejectedDownload":
                case "FollowAccepted":
                case "FollowRejected":
                case "NewComment":
                case "comment": // Legacy notifications from earlier versions.
                    if (action.equals("READ")) removeNotification(notification);
                    break;
            }
        }
    }

    private void deleteFollowNotification() throws IOException {
        Path path = Paths.get(ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId + "/notifications.txt");
        List<String> lines = Files.readAllLines(path);
        List<String> updatedLines = new ArrayList<>();

        boolean notificationFound = false;

        for (String line : lines) {

            String[] parts = line.trim().split("\\s+");

            if ( parts.length > 0 && ( parts[0].equals("follow") )) {
                notificationFound = true;
                continue;
            }

            updatedLines.add(line);
        }

        if(notificationFound) {
            // Rewrite the file
            Files.write(path, updatedLines);
            System.out.println("Deleted follow notification.");
        }
    }    

    // General function to handle download from server to client
    private void downloadSomething(String imageName, String descriptionName, String userId) throws IOException {

        // Directories
        String imageDirectory = ProjectPaths.serverRoot() + "/directories/" + "directory_" + GroupId + userId + "/" + imageName;
        String descriptionDirectory = ProjectPaths.serverRoot() + "/directories/" + "directory_" + GroupId + userId + "/" + descriptionName;

        System.out.println("Image Directory: " + descriptionDirectory);

        Path imagePath = Paths.get(imageDirectory);

        String descriptionLine = "";
        String txtLine = "";
        byte[] descriptionBytes;
        int descriptionLength;

        byte[] imageBytes = Files.readAllBytes(imagePath);

        lockFile(descriptionDirectory);

        // try catch to check if bind txt exists
        try {
            BufferedReader reader = new BufferedReader(new FileReader(descriptionDirectory));
            while ((txtLine = reader.readLine()) != null) {
                descriptionLine += txtLine + "\n";
            }
            //   descriptionLine = reader.readLine();
            reader.close();
            outStream.writeObject("Selected Picture has bind .txt file.");

        } catch (Exception e) {

          // 9.g
          outStream.writeObject("Selected Picture didn't have bind .txt file.");
        }

        unlockFile(descriptionDirectory);

        descriptionBytes = descriptionLine.getBytes();
        descriptionLength = descriptionBytes.length;

        // Combine data
        byte[] fullData = new byte[1 + descriptionLength + imageBytes.length];
        fullData[0] = (byte) descriptionLength;
        System.arraycopy(descriptionBytes, 0, fullData, 1, descriptionLength);
        System.arraycopy(imageBytes, 0, fullData, 1 + descriptionLength, imageBytes.length);

        // Divide into 10 packets
        int packetSize = (int) Math.ceil(fullData.length / 10.0);
        boolean ignoreNext = false;

        stopResending = false;

        SenderState state = new SenderState(); // Class has the GBN window base & sequence number for the next packet to be sent

        int windowSize = 3;
        Timer timer = new Timer();
        Map<Integer, Packet> packetBuffer = new HashMap<>();
        Set<String> receivedAcks = new HashSet<>();

        while (state.base < 10) {
            while (state.nextSeqNum < state.base + windowSize && state.nextSeqNum < 10) {
                int start = state.nextSeqNum * packetSize;
                int end = Math.min(start + packetSize, fullData.length);
                byte[] chunk = new byte[end - start];

                System.arraycopy(fullData, start, chunk, 0, chunk.length);
                Packet packet = new Packet(state.nextSeqNum, chunk);

                System.out.println("Sending packet " + state.nextSeqNum);

                outStream.writeObject(packet);
                outStream.flush();

                packetBuffer.put(state.nextSeqNum, packet); // Link sequence number to packet

                if (state.base == state.nextSeqNum) {
                    // Start timer
                    startTimer(timer, () -> {
                        if (stopResending) return;  // Prevent resends after GBN ends
                        synchronized (state) {
                            if (state.base >= 10) return; // Make sure you don't resend anything after base becomes 10
                            resendPackets(state, packetBuffer, outStream);
                        }
                    });
                }

                state.nextSeqNum++;
            }

            int originalTimeout = clientSocket.getSoTimeout();
            try {
                clientSocket.setSoTimeout(3000);
                Object ackObj = inStream.readObject();

                if (ackObj instanceof String ack && ack.startsWith("ACK")) {
                    int ackNum = Integer.parseInt(ack.substring(3));
                    System.out.println("Received but not yet accepted: " + ack);

                    if (!receivedAcks.contains(ack)) {
                        receivedAcks.add(ack); // If Server has not received this ACK before add it to receivedAcks
                        System.out.println("Received and ACCEPTED: ACK" + ackNum);
                    }

                    if (ackNum == state.base) {
                        // Increase GBN window
                        state.base++;

                        while (receivedAcks.contains("ACK" + state.base)) {
                            state.base++;
                        }

                        // When base becomes 10, we have sent all packets from 0 to 9
                        if (state.base == 10) {
                            timer.cancel();
                            break;
                        } else {
                            timer.cancel();
                            startTimer(timer, () -> {
                                if (stopResending) return;  // Prevent resends after GBN ends
                                synchronized (state) {
                                    if (state.base >= 10) return; // Make sure you don't resend anything after base becomes 10
                                    resendPackets(state, packetBuffer, outStream);
                                }
                            });                      }
                    }
                }

            } catch (SocketTimeoutException | ClassNotFoundException e) {

                System.out.println("ACK timeout! Resending from packet " + state.base);

                // Retransmit from base
                for (int i = state.base; i < state.nextSeqNum; i++) {
                    Packet resendPacket = packetBuffer.get(i);
                    outStream.writeObject(resendPacket);
                    outStream.flush();
                    System.out.println("Resent packet " + i);
                }
            } finally {
                try {
                    stopResending = true;
                    timer.cancel();  // To catch timeout exits or exceptions
                    timer.purge();
                    clientSocket.setSoTimeout(originalTimeout); // Restore timeout
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("--------------------------------------------------Done-----------------------------------------------------");
        outStream.writeObject("Transmission Complete");
        outStream.flush();
    }

    // Starts timer for timeout
    private void startTimer(Timer timer, Runnable task) {
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, 3000);
    }

    // Resends all packets in GBN window
    private void resendPackets(SenderState state, Map<Integer, Packet> packetBuffer, ObjectOutputStream outStream) {
        if (state.base >= state.nextSeqNum ) {
            return; // Don't resend if transmission is already complete
        }

        try {
            System.out.println("Timeout! Resending packets from " + state.base + " to " + (state.nextSeqNum - 1));

            // Resending packets that are in GBN window
            for (int i = state.base; i < state.nextSeqNum; i++) {
                Packet resendPacket = packetBuffer.get(i);

                outStream.writeObject(resendPacket);
                outStream.flush();

                System.out.println("Resent packet " + i);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Locks selected file
    private void lockFile(String filePath) {

        fileLocks.putIfAbsent(filePath, new ReentrantLock());
        ReentrantLock lock = fileLocks.get(filePath);

        while (true) {

          if (lock.tryLock()) {
            System.out.println("\nClient " + clientId + ": granted access to " + filePath);
            break;

          } else {

            System.out.println("\nClient " + clientId + ": Trying to access file: " + filePath + " but is locked. Waiting...");

            try {
              Thread.sleep(2000); // 2 Seconds before trying again

            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }

          }
        }
    }

    // Unlocks the selected file
    private void unlockFile(String filePath) {

        ReentrantLock lock = fileLocks.get(filePath);

        // Check if the file is locked or already unlocked
        if (lock != null && lock.isHeldByCurrentThread()) {
          lock.unlock();
          System.out.println("File Unlocked: " + filePath + "\n");
        }

    }

    // Creates necessary files for client after signup
    private void fixNewUserFiles() throws IOException{

        System.out.println("Creating all necessary user files...");
        new File(ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId).mkdirs();

        // add user to social graph txt
        socialLoader.addUser(clientId);

        try {

          File file = new File(ProjectPaths.serverRoot() + "/directories/directory_" + GroupId + clientId + "/notifications.txt");
          file.createNewFile();
          System.out.println("File: " + file + " created.");

          File file2 = new File(ProjectPaths.serverRoot() + "/profiles/Profile_" + GroupId + clientId + ".txt");
          file2.createNewFile();
          System.out.println("File: " + file2 + " created.");


          File file3 = new File(ProjectPaths.serverRoot() + "/profiles/Others_"+ GroupId + clientId + ".txt");
          file3.createNewFile();
          System.out.println("File: " + file3 + " created.");

        } catch(Exception e) {
          e.printStackTrace();
        }

    }

  // --------------------------------------------------------------------------------------------------------------------------------------

}