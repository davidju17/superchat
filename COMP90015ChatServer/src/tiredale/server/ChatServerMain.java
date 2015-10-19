package tiredale.server;

import java.util.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class ChatServerMain
{
   // Array list for storing references to server-side ChatServerThread for each
   // client connection, as well as current room lists.
   private static ArrayList<ChatServerThread> clientList =
            new ArrayList<ChatServerThread>();
   private static ArrayList<ChatServerRoom> roomList =
            new ArrayList<ChatServerRoom>();

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
      CmdLineParser parser = new CmdLineParser(this);

      try
      {
         parser.parseArgument(args);
      }
      catch (CmdLineException e)
      {
         // In case wrong argument entered.
         System.err.println(e.getMessage());
         parser.printUsage(System.err);
      }

      System.setProperty("javax.net.ssl.keyStore", "C:\\Coding\\servcert");
      System.setProperty("javax.net.ssl.keyStorePassword", "12345678");
          
      // Create server socket and initalises the system ready to accept incomign
      // connections from clients.
      SSLServerSocket serverSocket = null;

      roomList.add(new ChatServerRoom("MainHall", ""));

      try
      {
         // New ServerSocket created listening on port specified.
         // serverSocket = new ServerSocket(port);
         SSLServerSocketFactory factory =
                  (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
         
         serverSocket = (SSLServerSocket) factory.createServerSocket(port);

         while (true)
         {
            // Server continually waits for a new connection on incoming socket.
            SSLSocket socket = (SSLSocket) serverSocket.accept();
            // When new incoming connection the server instantiates a new Socket
            // and passes the new connection to this socket.
            // The new socket dedicated to this client is then passed to a new
            // thread which is defined by the ChatServerThread class which takes
            // the new socket as a constructor argument.
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
         // Occurs if client connection is not accepted. In this case no state
         // information of the user has been initilaised so no action is
         // necessary.
      }

      finally
      {
         if (serverSocket != null)
            serverSocket.close();
      }

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

}
