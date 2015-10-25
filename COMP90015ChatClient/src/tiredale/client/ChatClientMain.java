package tiredale.client;

import java.io.IOException;
import java.util.Scanner;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class ChatClientMain
{
   static Scanner sc = new Scanner(System.in);

   // For command line argument input.
   @Argument(usage = "Hostname", required = true)
   private static String host;

   @Option(name = "-p", usage = "Defines the server port number.",
            required = false)
   private static int port = 4444; // Default port 4444.

   // Main method calls doMain() required to implement CmdLineParser.

   final static Object lock = new Object();

   public static void main(String[] args) throws IOException
   {
      new ChatClientMain().doMain(args);
   }

   public void doMain(String[] args) throws IOException
   {
      CmdLineParser parser = new CmdLineParser(this);

      try
      {
         parser.parseArgument(args);
      }
      catch (CmdLineException e)
      {
         // handling of wrong arguments
         System.err.println(e.getMessage());
         parser.printUsage(System.err);
      }
      
      // Creates ChatClientSocketThread to handle TCP interface.
      Thread sock = new Thread(new ChatClientSocketThread(host, port));

      try
      {
         synchronized (lock)
         {
            sock.start();
            // Wait for socket to indicate that login has been successful.
            lock.wait();
         }
      }
      catch (InterruptedException e1)
      {
         // No interruption expected.
      }

      // After login, start scanner thread for handling normal command line
      // input.
      Thread scan = new Thread(new ChatClientScannerThread(sc));
      scan.start();
      
      // Waits until both socket thread and scanner thread have ended before
      // terminating.
      try
      {
         sock.join();
         scan.join();
      }
      catch (InterruptedException e)
      {
         System.out.println("Critical error. Exiting the program...");
      }

   }

}
