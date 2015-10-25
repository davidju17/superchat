package tiredale.server;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;

public class ChatServerMain
{
   // Array list for storing references to server-side ChatServerThread for
   // each
   // client connection, as well as current room lists.
   private static ArrayList<ChatServerThread> clientList =
            new ArrayList<ChatServerThread>();
   private static ArrayList<ChatServerRoom> roomList =
            new ArrayList<ChatServerRoom>();
   private static ArrayList<String> authenticatedUserList =
            new ArrayList<String>();
   private static ArrayList<String> authenticatedEmailList =
            new ArrayList<String>();

   public synchronized static int dataBaseLength()
   {
      return authenticatedUserList.size();

   }

   // Allows for port option at command line.
   @Option(name = "-p", usage = "Defines the server port number.",
            required = false)
   private static int port = 4444;

   public static void main(String[] args) throws IOException
   {
      // Just call method doMain, outlined below.
      new ChatServerMain().doMain(args);

   }

   // Method required to enable command line parser.
   public void doMain(String[] args) throws IOException
   {
      System.out.println("Server is running.");

      CmdLineParser parser = new CmdLineParser(this);
      File file = new File("database.data");

      // if file does not exists, then create it
      if (!file.exists())
      {
         file.createNewFile();
      }

      try
      {
         parser.parseArgument(args);
         loadDataBase();
      }
      catch (CmdLineException e)
      {
         // In case wrong argument entered.
         System.err.println(e.getMessage());
         parser.printUsage(System.err);
      }

      System.setProperty("javax.net.ssl.keyStore", "servcert");
      System.setProperty("javax.net.ssl.keyStorePassword", "12345678");

      // Create server socket and initalises the system ready to accept
      // incoming connections from clients.
      SSLServerSocket serverSocket = null;

      roomList.add(new ChatServerRoom("MainHall", ""));

      try
      {
         // New ServerSocket created listening on port specified.
         // serverSocket = new ServerSocket(port);
         SSLServerSocketFactory factory =
                  (SSLServerSocketFactory) SSLServerSocketFactory
                           .getDefault();

         serverSocket = (SSLServerSocket) factory.createServerSocket(port);

         while (true)
         {
            // Server continually waits for a new connection on incoming
            // socket.
            SSLSocket socket = (SSLSocket) serverSocket.accept();
            // When new incoming connection the server instantiates a new
            // SSLSocket and passes the new connection to this socket.
            // The new SSLSocket dedicated to this client is then passed to a
            // new thread which is defined by the ChatServerThread class which
            // takes the new socket as a constructor argument.
            clientList.add(new ChatServerThread(socket));

            // We now have a new thread solely dedicated to the recently
            // connected user. This user thread is then run - see
            // ChatServerThread for details of this method.
            new Thread(clientList.get(clientList.size() - 1)).start();

            // Loops around ready to receive another connection.
         }

         // To do when closing the server:
         // Need to look at clientList one last time and then join all the
         // remaining threads as they are closed.

      }
      catch (SocketException e)
      {
         // Occurs if client connection is not accepted. In this case no
         // state information of the user has been initilised so no action is
         // necessary.
      }

      finally
      {
         if (serverSocket != null)
            serverSocket.close();
      }

      System.out.println("Server is closed.");
   }

   // Getter and setter-like methods for accessing and modifying object
   // references in the lists of clients and rooms. Synchronization is required
   // to ensure no concurrency issues as these methods are called by different
   // ChatServerThread instances.

   public synchronized static ChatServerRoom getRoom(int roomId)
   {
      return (ChatServerRoom) roomList.get(roomId);
   }

   public synchronized static ChatServerThread getUser(int arrayId)
   {
      return (ChatServerThread) clientList.get(arrayId);
   }

   public synchronized static Boolean authUserExists(String identity)
   {
      if (authenticatedUserList.contains(identity))
      {
         return true;
      }
      else
      {
         return false;
      }
   }

   public synchronized static Boolean authEmailExists(String email)
   {
      if (authenticatedEmailList.contains(email))
      {
         return true;
      }
      else
      {
         return false;
      }
   }

   public synchronized static void addAuthUserIdentity(String newIdentity)
   {
      authenticatedUserList.add(newIdentity);
   }

   public synchronized static void updateAuthUserIdentity(
                                                          String formerIdentity,
                                                          String newIdentity)
   {
      authenticatedUserList.remove(formerIdentity);
      authenticatedUserList.add(newIdentity);

      // Save new identity store in database
      try
      {
         replaceFile(newIdentity, formerIdentity);
      }
      catch (IOException e)
      {
         System.out.println("Error in database. Details may not be updated.");
      }

   }

   public synchronized static void addRoom(String newRoomId, String identity)
   {
      roomList.add(new ChatServerRoom(newRoomId, identity));
   }

   public synchronized static void delUser(int userIndex)
   {
      clientList.remove(userIndex);
   }

   public synchronized static void delRoom(int roomIndex)
   {
      roomList.remove(roomIndex);
      ChatServerRoom.depRoomCount();
   }

   // Methods to authenticate password
   public synchronized static boolean authenticate(String attemptedPassword,
                                                   byte[] encryptedPassword,
                                                   byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException
   {
      // Encrypt the clear-text password using the same salt that was used to
      // encrypt the original password
      byte[] encryptedAttemptedPassword =
               getEncryptedPassword(
                                    attemptedPassword, salt);
      // Authentication succeeds if encrypted password that the user entered
      // is equal to the stored hash
      return Arrays.equals(encryptedAttemptedPassword, encryptedPassword);
   }

   // Encrypt Password
   public synchronized static byte[] getEncryptedPassword(String password,
                                                          byte[] salt)
            throws NoSuchAlgorithmException,
            InvalidKeySpecException
   {

      // PBKDF2 with SHA-1 as the hashing algorithm. Note that the NIST
      // specifically names SHA-1 as an acceptable hashing algorithm for
      // PBKDF2
      String algorithm = "PBKDF2WithHmacSHA1";
      // SHA-1 generates 160 bit hashes, so that's what makes sense here
      int derivedKeyLength = 160;
      // Pick an iteration count that works for you. The NIST recommends at
      // least 1,000 iterations:
      // http://csrc.nist.gov/publications/nistpubs/800-132/nist-sp800-132.pdf
      // iOS 4.x reportedly uses 10,000:
      // http://blog.crackpassword.com/2010/09/smartphone-forensics-cracking-blackberry-backup-passwords/

      int iterations = 20000;
      KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations,
                                    derivedKeyLength);
      SecretKeyFactory f = SecretKeyFactory.getInstance(algorithm);
      return f.generateSecret(spec).getEncoded();
   }

   // generateSalt is every time a new customer is coming
   public synchronized static byte[] generateSalt()
            throws NoSuchAlgorithmException
   {

      // VERY important to use SecureRandom instead of just Random
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

      // Generate a 8 byte (64 bit) salt as recommended by RSA PKCS5
      byte[] salt = new byte[8];
      random.nextBytes(salt);
      return salt;

   }

   public static synchronized void writeToFile(String username,
                                               byte[] saltUserName,
                                               byte[] password, String identity)
            throws IOException
   {

      BufferedOutputStream bufferedOut =
               new BufferedOutputStream(
                                        new FileOutputStream("database.data",
                                                             true));

      bufferedOut.write(username.getBytes());
      bufferedOut.write("\n".getBytes());
      bufferedOut.write(saltUserName);
      bufferedOut.write("\n".getBytes());
      bufferedOut.write(password);
      bufferedOut.write("\n".getBytes());
      bufferedOut.write(identity.getBytes());
      bufferedOut.write("\n".getBytes());

      bufferedOut.close();

   }

   public static synchronized void readFile(String username,
                                            ChatServerThread name)
            throws IOException
   {
      BufferedInputStream bufferedInput =
               new BufferedInputStream(
                                       new FileInputStream("database.data"));
      BufferedReader read =
               new BufferedReader(
                                  new InputStreamReader(
                                                        bufferedInput,
                                                        StandardCharsets.ISO_8859_1));
      String line = "";
      String identity = "guest0";
      boolean flag = false;
      boolean flag2 = false;
      boolean flag3 = false;

      // initialise in case username does not exit
      byte[] salt = "sfdsfdsgfdhg".getBytes(StandardCharsets.ISO_8859_1);
      byte[] ecryptedPassword = "nakgdajfoblsfbk"
               .getBytes(StandardCharsets.ISO_8859_1);

      while ((line = read.readLine()) != null)
      {

         if (flag3 == true)
         {
            identity = line;
            flag3 = false;
            break;
         }
         if (flag2 == true)
         {
            ecryptedPassword = line.getBytes(StandardCharsets.ISO_8859_1);
            flag2 = false;
            flag3 = true;
         }
         if (flag == true)
         {
            salt = line.getBytes(StandardCharsets.ISO_8859_1);
            flag = false;
            flag2 = true;
         }
         if (line.equals(username))
         {
            flag = true;
         }

      }
      name.setIdentity(identity);
      name.setEncryptedPassword(ecryptedPassword);
      name.setSalt(salt);

      read.close();
   }

   public static synchronized void replaceFile(String newidentity,
                                               String formerIdentity)
            throws IOException
   {

      List<String> lines = new ArrayList<String>();
      String line = "";
      String putData = null;
      BufferedReader br = null;
      BufferedWriter bw = null;

      br = new BufferedReader(new FileReader("database.data"));

      while ((line = br.readLine()) != null)
      {

         if (line.equals(formerIdentity))
         {
            line = line.replace(formerIdentity, newidentity);

         }
         lines.add(line);
      }

      br.close();

      bw = new BufferedWriter(new FileWriter("database.data"));

      for (String s : lines)
      {
         bw.write(s);
         bw.write("\n");

      }
      bw.close();

   }

   // method to load information from database into server
   public void loadDataBase() throws IOException
   {

      BufferedReader br = null;
      String line = "";
      int count = 0;

      br = new BufferedReader(new FileReader("database.data"));

      while ((line = br.readLine()) != null)
      {
         count++;
         if (count % 4 == 1)
         {
            authenticatedEmailList.add(line);

         }
         if (count % 4 == 0)
         {
            authenticatedUserList.add(line);
         }
      }

      br.close();

   }

}
