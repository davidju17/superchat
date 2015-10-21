package tiredale.client;

import java.util.Scanner;

public class ChatClientScannerThread implements Runnable
{
   private Scanner sc;

   public ChatClientScannerThread(Scanner scan)
   {
      this.sc = scan;//new Scanner(System.in);
   }

   @Override
   public void run()
   {
      String input = "";

      try
      {
         // Sleeps the scanner thread for 1200ms to allow for certificate
         // authentication.
         Thread.sleep(1200);
      }
      catch (InterruptedException e1)
      {
         // No interruption expected.
      }
      
      do
      {         
         try
         {
            // Sleeps the scanner thread for 200ms to allow for message
            // transmission, receipt and processing. This parameter may need to
            // be tuned depending on the real network latency.
            Thread.sleep(200);

            if (Thread.interrupted())
            {
               throw new InterruptedException();
            }
         }
         catch (InterruptedException e)
         {
            return;
         }
         
         System.out.print("[" + ChatClientSocketThread.getCurrentRoom() + "] " +
                          ChatClientSocketThread.getCurrentId() + "> ");

         // Scanner is blocking so thread will wait here until command is
         // entered.
         input = sc.nextLine();

         if (input.length() > 0)
         {
            ChatClientSocketThread.readCommandLineInput(input);
         }

      } while (!input.equals("#quit"));

      sc.close();

   }
}
