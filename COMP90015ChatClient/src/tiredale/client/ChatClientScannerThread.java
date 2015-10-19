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

      do
      {
         try
         {
            // Sleeps the scanner thread for 1000ms to allow for certificate
            // authentication, followed by message transmission, receipt and
            // processing. This parameter may need to be tuned depending on the
            // real network latency.
            Thread.sleep(1000);

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
