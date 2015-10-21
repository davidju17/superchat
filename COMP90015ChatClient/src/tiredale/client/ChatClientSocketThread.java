package tiredale.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.UnknownHostException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.Scanner;

@SuppressWarnings("unchecked")
public class ChatClientSocketThread implements Runnable
{
   // Attributes of current user state. Obtained from server.
   private static String currentUserId = "";
   private static String currentRoom = "";

   // Attributes of thread for opening TCP connection.
   private static BufferedReader in;
   private static OutputStreamWriter out;
   private String serverAddress;
   private int socketPort;

   // Attributes for storing some state information about the thread.
   private static Boolean loginStatus = false;
   private static Boolean quitNotifier = false;
   private static String createRoomNotifier = "";

   public ChatClientSocketThread(String host, int port)
   {
      serverAddress = host;
      socketPort = port;
   }

   @Override
   public void run()
   {
      System.setProperty("javax.net.ssl.trustStore", "C:\\Coding\\servcert");
      System.setProperty("javax.net.ssl.trustStorePassword", "12345678");

      SSLSocketFactory factory =
               (SSLSocketFactory) SSLSocketFactory.getDefault();

      SSLSocket socket = null;

      try
      {
         // As soon as ChatClientMain is instantiated it creates a new socket to
         // connect to server.

         socket = (SSLSocket) factory.createSocket(serverAddress, socketPort);

         // Preparing sending and receiving streams

         in = new BufferedReader
                  (new InputStreamReader(socket.getInputStream(), "UTF-8"));
         out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

         synchronized (ChatClientMain.lock)
         {
            while (!loginStatus)
            {
               clientLoginDetails();
            }

            // Tell server to open scanner thread for asynchronous message
            // receipt.
            ChatClientMain.lock.notify();
         }

         // While loop used to take commands from command line. Executes until
         // quit command is entered.

         while (!quitNotifier)
         {
            // This handles incoming message.
            // Create new objects for each JSON message.
            JSONObject jsonObjIn;

            // For reading in and parsing JSON messages received from server.
            jsonObjIn = ReceiveJsonObject(in);

            // Need to put that message into readJSONObject method.
            readJSONInput(jsonObjIn);
         }

         
         
         in.close();
         out.close();
         socket.close();
         System.out.println("Disconnecting from " + serverAddress);
      }
      catch (UnknownHostException e)
      {
         System.out.println("Unknown host. Exiting the program...");
      }
      catch (IOException e)
      {
         // Issue with reader/writer. Just reset state information and treat
         // message as lost.
      }
   }

   public void clientLoginDetails()
   {
      String option;
      String emailSc;
      String passwordSc;

      System.out
               .println("Welcome to chat. Please select from the items below:");
      System.out
               .println("- If you are an exising user press 1 to login.");
      System.out
               .println("- If you are a new user press 2 to create an account.");
      System.out
               .println("- If you wish to join as a guest press 3.");

      option = ChatClientMain.sc.nextLine();

      JSONObject jsonObjLogin = new JSONObject();
      jsonObjLogin.put("type", "login");

      switch (option)
      {
         case "1":
         {
            System.out.println("Please enter your email address: ");
            emailSc = ChatClientMain.sc.nextLine();
            System.out.println("Please enter your password: ");
            passwordSc = ChatClientMain.sc.nextLine();

            jsonObjLogin.put("email", emailSc);
            jsonObjLogin.put("password", passwordSc);
            jsonObjLogin.put("flag", option);

            SendJsonObject(jsonObjLogin, out);
            loginDetailsResponse();
            break;
         }
         case "2":
         {
            System.out
                     .println("Please enter the email address you wish to use to login: ");
            emailSc = ChatClientMain.sc.nextLine();
            System.out.println("Please enter a new password: ");
            // POSSIBLE VERIFY PASSWORD
            passwordSc = ChatClientMain.sc.nextLine();

            jsonObjLogin.put("email", emailSc.toLowerCase());
            jsonObjLogin.put("password", passwordSc);
            jsonObjLogin.put("flag", option);

            SendJsonObject(jsonObjLogin, out);
            loginDetailsResponse();
            break;
         }
         case "3":
         {
            jsonObjLogin.put("email", "");
            jsonObjLogin.put("password", "");
            jsonObjLogin.put("flag", option);

            SendJsonObject(jsonObjLogin, out);
            loginDetailsResponse();
            break;
         }
         default:
         {
            System.out
                     .println(option +
                              " is not a valid selection. Please try again.");
         }
      }
   }

   public void loginDetailsResponse()
   {
      // This handles incoming message.
      // Create new objects for each JSON message.
      JSONObject jsonObjIn;

      // For reading in and parsing JSON messages received from server.
      jsonObjIn = ReceiveJsonObject(in);

      // Need to put that message into readJSONObject method.
      currentUserId = jsonObjIn.get("identity").toString();

      if (!currentUserId.equals(""))
      {
         loginStatus = true;
      }

      try
      {
         readJSONInput(jsonObjIn);
      }
      catch (IOException e)
      {
      }
   }

   public void readJSONInput(JSONObject jsonObjIn) throws IOException
   {
      // Processes incoming message depending on type.

      switch ((String) jsonObjIn.get("type"))
      {

         case ("newidentity"):
            IdentityChangeResponse(jsonObjIn);
            break;

         case ("roomchange"):
            RoomChangeResponse(jsonObjIn);
            break;

         case ("roomcontents"):
            RoomContentsResponse(jsonObjIn);
            break;

         case ("roomlist"):
            RoomListResponse(jsonObjIn);
            break;

         case ("message"):
            ReceiveMessage(jsonObjIn);
            break;

         default:
            System.out
                     .println("Oops something went wrong with our server. The program will now close.");
            quitNotifier = true;
            throw new IOException();
      }

   }

   public static void readCommandLineInput(String scannerInput)
   {
      String clientInput = scannerInput;

      // Checks if input from command line is a #command or just a message
      // to be sent and passes to the appropriate method.
      if (clientInput.charAt(0) == '#')
      {
         readCommand(clientInput, out, in);
      }

      else
      {
         SendMessage(clientInput, out);
      }
   }

   // Reads the latest command and passes it to the appropriate method.
   private static void readCommand(String command, OutputStreamWriter out,
                                   BufferedReader in)
   {
      StringTokenizer token;
      String id;
      String joinRoom;
      String kickRoom;
      String who;
      String createRoom;
      String deleteRoom;
      int time;

      try
      {
         token = new StringTokenizer(command);

         command = token.nextToken();

         switch (command)
         {
            case "#identitychange":
               id = token.nextToken();
               IdentityChangeRequest(id, out);
               break;

            case "#join":
               joinRoom = token.nextToken();
               RoomChangeRequest(joinRoom, out);
               break;

            case "#who":
               who = token.nextToken();
               RoomContentsRequest(who, out);
               break;

            case "#list":
               RoomListRequest(out);
               break;

            case "#createroom":
               createRoom = token.nextToken();
               CreateRoom(createRoom, out);
               break;

            case "#kick":
               id = token.nextToken();
               kickRoom = token.nextToken();
               time = Integer.valueOf(token.nextToken());
               KickUser(kickRoom, time, id, out);
               break;

            case "#delete":
               deleteRoom = token.nextToken();
               DeleteRoom(deleteRoom, out);
               break;

            case "#quit":
               RoomChangeRequest("", out);
               quitNotifier = true;
               break;

            default:
               System.out.println("Error - This is not a valid command");

         }

      }

      catch (NoSuchElementException io)
      {
         System.out
                  .println("Incorrect argument/s provided for this command. Please try again.");
      }
   }

   private static void IdentityChangeRequest(String identity,
                                             OutputStreamWriter out)
   {
      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "identitychange");
      jsonObjSent.put("identity", identity);

      // Code to send JSON message.
      SendJsonObject(jsonObjSent, out);

      // Then passes identity to the IdentityChangeReceive method to wait for
      // and handle response appropriately.
      // return IdentityChangeResponse(jsonObjRec);
   }

   private void IdentityChangeResponse(JSONObject jsonObjRec)
   {
      String message = "";
      String JSONIdentity = jsonObjRec.get("identity").toString();
      String JSONFormerIdentity = jsonObjRec.get("former").toString();

      // Check if newidentity message is meant for this user.
      if (JSONFormerIdentity.equals(currentUserId))
      {
         if (JSONFormerIdentity.equals(""))
         {
            message =
                     "The credentials you have supplied are incorrect. Please try again.";
         }

         else if (JSONFormerIdentity.equals(JSONIdentity))
         {
            message = "Requested identity invalid or in use";
         }

         // Name change updated here under normal conditions.
         else
         {
            currentUserId = jsonObjRec.get("identity").toString();
            message = JSONFormerIdentity + " is now " +
                      currentUserId;
         }
      }

      // This case only occurs if the user logs in for the first time.
      else if (JSONFormerIdentity.equals("") && JSONIdentity.equals(currentUserId))
      {
         currentRoom = "MainHall";
         message = "Connected to " + serverAddress + " as " + currentUserId;
      }

      // If not directed at this user just print information about name change.
      else
      {
         if (!JSONFormerIdentity.equals(""))
         {
            message = JSONFormerIdentity + " is now " + JSONIdentity;
         }
      }

      System.out.println(message);
   }

   // Allows user to send a request to join a different room.
   private static void RoomChangeRequest(String room, OutputStreamWriter out)
   {
      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "join");
      jsonObjSent.put("roomid", room);

      // Code to send JSON message.
      SendJsonObject(jsonObjSent, out);
   }

   // Handles all incoming room change messages and processed.
   private void RoomChangeResponse(JSONObject jsonObjRec)
   {
      // Check if message intended for current user.
      String message;
      if (jsonObjRec.get("identity").toString().equals(currentUserId))
      {
         if (jsonObjRec.get("former").toString()
                  .equals(jsonObjRec.get("roomid").toString()))
         {
            message = "The requested room is invalid or non existent.";
         }
         else if (jsonObjRec.get("former").toString().equals(""))
         {
            message = currentUserId + " moves to " + jsonObjRec.get("roomid");
         }
         else if (jsonObjRec.get("roomid").toString().equals(""))
         {
            message =
                     jsonObjRec.get("identity") + " leaves " +
                              jsonObjRec.get("former");
            quitNotifier = true;
         }
         else
         {
            currentRoom = jsonObjRec.get("roomid").toString();
            message =
                     currentUserId + " moves from " +
                              jsonObjRec.get("former").toString() + " to " +
                              currentRoom;
         }

      }
      else
      {
         if (jsonObjRec.get("former").toString().equals(""))
         {
            message =
                     jsonObjRec.get("identity") + " moves to " +
                              jsonObjRec.get("roomid");
         }
         else if (jsonObjRec.get("roomid").toString().equals(""))
         {
            message =
                     jsonObjRec.get("identity") + " leaves " +
                              jsonObjRec.get("former");
         }
         else
         {
            message =
                     jsonObjRec.get("identity") + " moves from " +
                              jsonObjRec.get("former").toString() + " to " +
                              jsonObjRec.get("roomid");
         }
      }
      System.out.println(message);
   }

   private static void RoomContentsRequest(String room, OutputStreamWriter out)
   {
      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "who");
      jsonObjSent.put("roomid", room);

      // Code to send JSON message.
      SendJsonObject(jsonObjSent, out);

      // // Then passes identity to the RoomChangeResponse method to wait for
      // // and handle response appropriately.
      // RoomContentsResponse(jsonObjRec);
   }

   private void RoomContentsResponse(JSONObject jsonObjRec)
   {
      // Sample response:
      // {"type":"roomcontents","roomid":"comp90015",
      // "identities":["aaron","adel","chao","guest1"],"owner":"chao"}

      // MainHall contains guest1 adel chao* guest34 guest2 guest5

      String userArrayOut = "";

      JSONArray userArray = (JSONArray) jsonObjRec.get("identities");

      for (int i = 0; i < userArray.size(); i++)
      {
         if (userArray.get(i).toString().equals(jsonObjRec.get("owner")))
         {
            userArrayOut =
                     userArrayOut + " " + userArray.get(i).toString() + "*";
         }
         else
         {
            userArrayOut = userArrayOut + " " + userArray.get(i).toString();
         }

      }
      System.out.println(jsonObjRec.get("roomid").toString() + " contains" +
                         userArrayOut);

   }

   private static void RoomListRequest(OutputStreamWriter out)
   {
      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "list");

      // Code to send JSON message.
      SendJsonObject(jsonObjSent, out);

      // // Then passes identity to the RoomListResponse method to wait for
      // // and handle response appropriately.
      //
      // return RoomListResponse(jsonObjRec);
   }

   private void RoomListResponse(JSONObject jsonObjRec)
   {

      // Return format.
      // {"type":"roomlist","rooms":[{"roomid":"MainHall","count":5},
      // {"roomid":"comp90015","count":7},{"roomid":"FridayNight","count":4}]
      // }

      // State holder to check if this is a list response to a createrooom
      // attempt.
      Boolean createRoomResponse = false;
      Boolean roomCreated = false;

      if (!createRoomNotifier.equals(""))
      {
         createRoomResponse = true;
      }

      String roomString = "";
      JSONArray rooms = (JSONArray) jsonObjRec.get("rooms");

      for (int i = 0; i < rooms.size(); i++)
      {
         JSONObject roomObj = (JSONObject) rooms.get(i);
         roomString =
                  roomString + roomObj.get("roomid").toString() + ": " +
                           roomObj.get("count").toString() + " guests\n";

         // Checks if room was created.
         if (createRoomResponse &&
             roomObj.get("roomid").equals(createRoomNotifier) && !roomCreated)
         {
            roomCreated = true;

         }
      }
      System.out.print(roomString);

      if (createRoomResponse && roomCreated)
      {
         System.out.println("Room " + createRoomNotifier + " created.");
      }
      else if (createRoomResponse && !roomCreated)
      {
         System.out.println("Room " + createRoomNotifier +
                            " is invalid or already in use.");
      }
      createRoomNotifier = "";
   }

   private static void CreateRoom(String room, OutputStreamWriter out)
   {
      // Generate createroom JSON object.
      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "createroom");
      jsonObjSent.put("roomid", room);

      createRoomNotifier = room;

      // Code to send JSON message.
      SendJsonObject(jsonObjSent, out);

      // // Then passes JSON object sent from server to the RoomListResponse
      // method
      // // to handle response appropriately.
      //
      // return RoomListResponse(jsonObjRec);
   }

   private static void KickUser(String room, int time, String identity,
                                OutputStreamWriter out)
   {

      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "kick");
      jsonObjSent.put("roomid", room);
      jsonObjSent.put("time", time);
      jsonObjSent.put("identity", identity);

      // Code to send JSON message. This method call doesn't require a response.
      // If server deems that the client has permission to kick the user this
      // will be indicated in a broadcast roomchange message.
      SendJsonObject(jsonObjSent, out);

   }

   private static void DeleteRoom(String room, OutputStreamWriter out)
   {

      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "delete");
      jsonObjSent.put("roomid", room);

      // Code to send JSON message.
      SendJsonObject(jsonObjSent, out);

      // String jsonmes = jobj.toString();
      // return jsonmes;
   }

   private static void SendMessage(String message, OutputStreamWriter out)
   {
      // Message will be on the command line when sent.
      // Generate JSON object for message to send to server.
      JSONObject jsonObjSent = new JSONObject();
      jsonObjSent.put("type", "message");
      jsonObjSent.put("content", message);

      // Send message to server.
      SendJsonObject(jsonObjSent, out);
   }

   private void ReceiveMessage(JSONObject jsonObjSent)
   {
      System.out.println(jsonObjSent.get("identity") + ": " +
                         jsonObjSent.get("content"));
   }

   private static void SendJsonObject(JSONObject jsonObjSent,
                                      OutputStreamWriter out)
   {
      try
      {
         out.write(jsonObjSent.toString() + "\n");
         out.flush();
      }
      catch (IOException e)
      {
         // Issue with reader/writer. Just reset state information and treat
         // message as lost.
         quitNotifier = false;
         createRoomNotifier = "";
      }

   }

   private JSONObject ReceiveJsonObject(BufferedReader in)
   {
      String response;
      JSONObject jsonObjRec;
      try
      {
         response = in.readLine();
         Object obj = JSONValue.parse(response);
         jsonObjRec = (JSONObject) obj;
      }
      catch (IOException e)
      {
         jsonObjRec = new JSONObject();
         jsonObjRec.put("type", "Error");
         // Socket closed on server side.
      }
      return jsonObjRec;
   }

   public static String getCurrentRoom()
   {
      return currentRoom;
   }

   public static String getCurrentId()
   {
      return currentUserId;
   }
}
