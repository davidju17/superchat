package tiredale.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.primitives.Bytes;

// Each thread represents a connection for a user.

@SuppressWarnings("unchecked")
public class ChatServerThread implements Runnable
{
   // Instance variables for setting up each thread.
   private Socket socket;
   private String msgIn;
   private BufferedWriter out;
   private BufferedReader in;

   // User attributes for this specific client.
   private String email;
   private String password; // Is this safe?
   private Boolean authenticated = false;
   private String identity;

   private String room;
   private ArrayList<String> banRoom = new ArrayList<String>();
   private ArrayList<Long> banStart = new ArrayList<Long>();
   private ArrayList<Long> banLength = new ArrayList<Long>();

   // Maintaining count of number of threads.
   private static int clientCount = 0;
   private byte[] salt;
   private byte[] encryptedPassword;

   // Setters
   public void setSalt(byte[] salt)
   {
      this.salt = salt;
   }

   public void setEncryptedPassword(byte[] encryptedPassword)
   {
      this.encryptedPassword = encryptedPassword;
   }

   public void setIdentity(String identity)
   {
      this.identity = identity;
   }

   public static void setClientCount(int clientCount)
   {
      ChatServerThread.clientCount = clientCount;
   }

   Boolean active;

   // Constructor of the ChatServerThread
   public ChatServerThread(Socket socket)
   {
      this.socket = socket;
      this.identity = "";
      this.room = "";

      this.active = true;
   }

   @Override
   public void run()
   {
      // Opens the TCP connections for sending and receiving TCP through this
      // socket.

      try
      {
         out =
                  new BufferedWriter(
                                     new OutputStreamWriter(
                                                            socket.getOutputStream(),
                                                            "UTF-8"));
         in =
                  new BufferedReader(
                                     new InputStreamReader(
                                                           socket.getInputStream(),
                                                           "UTF-8"));

         do
         {
            // This handles incoming message, processing message and sending
            // response until the 'active' attribute is negated causing the
            // loop to stop.

            // Create new objects for each JSON message.
            JSONObject jsonObjRec;
            Object obj;

            // For reading in messages received from client.
            msgIn = in.readLine();

            obj = JSONValue.parse(msgIn);
            jsonObjRec = (JSONObject) obj;

            ProcessMessage(jsonObjRec);

         } while (active);

         RemoveThread();

         // Close reader and writer.
         in.close();
         out.close();

         // Close the socket.
         if (socket != null)
            socket.close();

      }

      catch (SocketException e)
      {
         // If socket terminates unexpectedly the client needs to be
         // removed as this client is no longer connected.

         UnexpectedClientRemoval();

      }

      catch (IOException e)
      {
         // This is only thrown is when closing a socket and thread is about
         // to
         // terminate. Nothing further required.
      }

   }

   // DAVID THIS IS WHERE WE NEED TO IMPLEMENT LOOKUP IN THE DATABASE.

   private synchronized void VerifyIdentity(JSONObject jsonObjRec)
   {
      String emailIn = jsonObjRec.get("email").toString();
      String passwordIn = jsonObjRec.get("password").toString();
      String flagRec = jsonObjRec.get("flag").toString();
      boolean loginSuccess = true;

      // Initialise the email, password and currentid attributes based on the
      // emailIn and passwordIn provided.

      // flag is 1 if logging into existing account.
      // flag is 2 if initialising new account.
      // flag is 3 if joining as guest.

      switch (flagRec)
      {
         case ("1"):
         {
            // This compares the incoming login details to the corresponding
            // values in the database, where email address is used to lookup.

            JSONObject jsonObjIdInit = new JSONObject();
            jsonObjIdInit.put("type", "newidentity");
            try
            {

               for (int i = 0; i < ChatServerMain.dataBaseLength(); i++)
               {
                  if (ChatServerMain.getUser(i).equals(this))
                  {
                     ChatServerMain
                              .readFile(emailIn, ChatServerMain.getUser(i));
                     break;
                  }
               }
               if (ChatServerMain.authenticate(passwordIn, encryptedPassword,
                                               salt))
               {
                  // if (emailIn.equals(email) && passwordIn.equals(password)) {
                  authenticated = true;
                  clientCount++;

                  // Adds identity from database to the IdentityChange dummy
                  // message, sets identity to empty string so IdentityChange
                  // method is in the correct state to facilitate identity
                  // initialisation at the client.

                  jsonObjIdInit.put("identity", identity);
                  jsonObjIdInit.put("former", "");

                  SendJsonObject(jsonObjIdInit, out);
               }
               else
               {
                  // Login failure is indicated by returning an empty identity.
                  jsonObjIdInit.put("identity", "");
                  jsonObjIdInit.put("former", "");
                  loginSuccess = false;
                  SendJsonObject(jsonObjIdInit, out);
               }

            }
            catch (IOException e)
            {
               e.printStackTrace();

            }
            catch (NoSuchAlgorithmException e)
            {
               e.printStackTrace();
            }
            catch (InvalidKeySpecException e)
            {
               e.printStackTrace();
            }

            break;
         }
         case ("2"):
         {
            // This needs to read in email address from JSONObject and copy to
            // local variables for saving to database.
            email = jsonObjRec.get("email").toString();
            password = jsonObjRec.get("password").toString();

            if (ChatServerMain.authEmailExists(email))
            {
               JSONObject jsonObjIdInit = new JSONObject();
               jsonObjIdInit.put("identity", "");
               jsonObjIdInit.put("former", "");
               loginSuccess = false;
               
               SendJsonObject(jsonObjIdInit, out);
            }
            else
            {
               authenticated = true;

               // Generate unique salt for this new guest
               try
               {
                  byte[] salt = ChatServerMain.generateSalt();

                  // EncryptedPassword
                  byte[] passwordEncrytedByte = ChatServerMain
                           .getEncryptedPassword(password, salt);

                  // check if there is any newline byte in encrypted password or
                  // salt byte array
                  // if there is any generate new one until but does not
                  // contains
                  // any newline byte
                  // and save in file for properly decodification.
                  byte newLine = (byte) '\n';
                  byte newLine2 = (byte) '\r';

                  while (Bytes.contains(passwordEncrytedByte, newLine) ||
                         Bytes.contains(salt, newLine) ||
                         Bytes.contains(passwordEncrytedByte, newLine2) ||
                         Bytes.contains(salt, newLine2))
                  {
                     // generate new salt
                     salt = ChatServerMain.generateSalt();
                     // generate new EncryptedPassword
                     passwordEncrytedByte = ChatServerMain
                              .getEncryptedPassword(password, salt);
                  }

                  // Initial login produces assigned guestId.
                  CreateName();
                  ChatServerMain.writeToFile(email, salt, passwordEncrytedByte,
                                             identity);
               }
               catch (NoSuchAlgorithmException e)
               {
                  e.printStackTrace();
               }
               catch (InvalidKeySpecException e)
               {
                  e.printStackTrace();
               }
               catch (IOException e)
               {
                  e.printStackTrace();
               }

            }// close else

            break;
         }

         case ("3"):
         {
            CreateName();
            break;
         }
         // default:
         // {
         // // No other values possible.
         // }
      }

      // If login successful then add user to MainHall.
      if (loginSuccess)
      {
         // Sends roomchange message to initialise client.
         JSONObject jsonObjRoomInit = new JSONObject();
         jsonObjRoomInit.put("type", "roomchange");
         jsonObjRoomInit.put("identity", identity);
         jsonObjRoomInit.put("former", "");
         jsonObjRoomInit.put("roomid", "MainHall");

         RoomChange(jsonObjRoomInit);
      }
   }

   private synchronized void CreateName()
   {
      boolean test;
      int minInt = 0;
      do
      {
         test = false;

         // Checks is any authorised user has reserved the guestX username.
         if (ChatServerMain.authUserExists("guest" + minInt))
         {
            test = true;
            minInt++;
         }
         else
         {
            for (int i = 0; i < clientCount; i++)
            {
               // Checks is any active user has guestX username.
               if (ChatServerMain.getUser(i).getIdentity()
                        .equals("guest" + minInt))
               {
                  test = true;
                  minInt++;
                  break;
               }
            }
         }

      } while (test);

      String idInit = "guest" + minInt;
      JSONObject jsonObjIdInit = new JSONObject();
      jsonObjIdInit.put("type", "identitychange");
      jsonObjIdInit.put("identity", idInit);

      clientCount++;

      IdentityChange(jsonObjIdInit);
   }

   // Method for parsing incoming jsonObject to determine its type and pass to
   // the appropriate method.
   private void ProcessMessage(JSONObject jsonObjRec)
   {
      String type = jsonObjRec.get("type").toString();

      switch (type)
      {

         case "login":
         {
            VerifyIdentity(jsonObjRec);
            // if (authenticated)
            // {
            // ChatServerMain.addAuthUserIdentity(identity);
            // }
            break;
         }

         case "message":
         {
            Message(jsonObjRec);
            break;
         }

         case "identitychange":
         {
            IdentityChange(jsonObjRec);
            break;
         }

         case "join":
         {
            // Roomchange methods accepts a modified JSON object. This was
            // needed to allow for roomChange method to handle multiple
            // different cases, such as kick, delete room as well as normal
            // join.
            jsonObjRec.put("former", room);
            jsonObjRec.put("identity", identity);
            RoomChange(jsonObjRec);
            break;
         }

         case "who":
         {
            RoomContents(jsonObjRec);
            break;
         }

         case "list":
         {
            RoomList(out);
            break;
         }

         case "createroom":
         {
            CreateRoom(jsonObjRec);
            break;
         }

         case "kick":
         {
            KickUser(jsonObjRec);
            break;
         }

         case "delete":
         {
            DeleteRoom(jsonObjRec);
            break;
         }

         default:
            // No default defined as there are no cases. This would mean any
            // spoof messages which do not have an expected type will not be
            // processed.
      }
   }

   // Used for forwarding messages to users in the same room.
   private synchronized void Message(JSONObject jsonObjRec)
   {
      // Parses JSON request from client and prepares JSON response.
      JSONObject jsonObjServ = jsonObjRec;
      jsonObjServ.put("type", "message");
      jsonObjServ.put("identity", this.identity);
      jsonObjServ.put("content", jsonObjRec.get("content"));

      // First loop required to identify the array index of the user's current
      // room in the roomList object of ChatServerMain.
      for (int k = 0; k < clientCount; k++)
      {
         ChatServerThread userCheck = ChatServerMain.getUser(k);
         if (userCheck.getRoom().equals(room))
         {
            SendJsonObject(jsonObjServ, userCheck.out);
         }
      }
   }

   // Used for:
   // - broadcasting identitychange of a current user to all users (loop
   // through
   // clientList).
   // - unicast initial identity given to new user.
   private synchronized void IdentityChange(JSONObject jsonObjRec)
   {
      // Parses JSON request from client and prepares JSON response.
      JSONObject jsonObjServ = new JSONObject();
      jsonObjServ.put("type", "newidentity");
      jsonObjServ.put("former", identity);

      String newIdentity = jsonObjRec.get("identity").toString();

      Boolean nameAvailable = true;
      if (ChatServerMain.authUserExists(newIdentity))
      {
         nameAvailable = false;
      }
      else
      {
         for (int i = 0; i < clientCount; i++)
         {
            if (newIdentity.equals(ChatServerMain.getUser(i).getIdentity()
                     .toString()))
            {
               nameAvailable = false;
               break;
            }

         }
      }

      // Checks if proposed identity is valid. If not it simply returns the
      // old
      // name.
      if (nameAvailable && newIdentity.matches("^[a-zA-Z]+[a-zA-Z0-9]*$")
          && newIdentity.length() > 2 && newIdentity.length() < 17)
      {
         jsonObjServ.put("identity", jsonObjRec.get("identity").toString());

         // Checks is user is owner of any rooms and updates the name
         // accordingly.
         // Updates the name in current room content list.
         for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
         {
            ChatServerRoom roomCheck = ChatServerMain.getRoom(i);
            if (roomCheck.getOwnerId().equals(this.identity)
                && !roomCheck.getOwnerId().equals(""))
            {
               ChatServerMain.getRoom(i).setOwnerId(
                                                    jsonObjRec.get("identity")
                                                             .toString());
            }
            if (roomCheck.getRoomContents().contains(identity))
            {
               roomCheck.getRoomContents().remove(identity);
               roomCheck.getRoomContents().add(newIdentity);
            }
         }
         // Updates the name in authorised room content list.
         if (authenticated)
         {
            ChatServerMain.updateAuthUserIdentity(identity, newIdentity);
         }

         // Updates the identity attribute of the client.
         identity = jsonObjRec.get("identity").toString();

         // Broadcast to all users the newidentity message.
         for (int i = 0; i < clientCount; i++)
         {
            SendJsonObject(jsonObjServ, ChatServerMain.getUser(i).out);
         }
      }
      else
      // If not a valid name...
      {
         // Old identity is returned as newidentity.
         jsonObjServ.put("identity", this.identity);

         // Unicast newidentity message just to the requesting user.
         SendJsonObject(jsonObjServ, out);
      }

   }

   // Used for:
   // - roomchange unicast if room change unsuccesful. OK
   // - roomchange multicast (former and new room users and initiator) OK
   // - roomchange to mainhall --> unicast roomcontents and roomlist to
   // intiator. OK
   // - initial 'change' to MainHall when first joining (can treat same as
   // above
   // case?)
   // - kick user to MainHall. Will need to call this method with artificial
   // roomChange message? OK
   // - delete room multicast to all users in the room. Can put loop in delete!
   // OK
   // - multicast to users when a user quits (new roomid is empty string). Can
   // put loop in quit! NOK
   public synchronized void RoomChange(JSONObject jsonObjRec)
   {
      int currentRoomCount = ChatServerRoom.getRoomCount();
      String userChanging = jsonObjRec.get("identity").toString();
      String requestedRoom = jsonObjRec.get("roomid").toString();
      String formerRoom = jsonObjRec.get("former").toString();

      // Parses JSON request from client and prepares JSON response.
      JSONObject jsonObjServ = new JSONObject();
      jsonObjServ.put("type", "roomchange");
      jsonObjServ.put("identity", userChanging);
      jsonObjServ.put("former", formerRoom);

      // Checks if banned from room.

      // To check ban need this to be a genuine room change NOT to MainHall.
      // Cases where this is NOT a genuine room change:
      // - Any change TO "MainHall".
      // - Any change TO "" which is the quit scenario.

      Boolean changeAllowed = true;
      if (!(requestedRoom.equals("MainHall") || requestedRoom.equals("")))
      {
         for (int a = 0; a < banRoom.size(); a++)
         {
            // Checks if currently banned from requested room.
            if (System.currentTimeMillis() - banStart.get(a) < (banLength
                     .get(a)) * 1000 && banRoom.get(a).equals(requestedRoom))
            {
               changeAllowed = false;
            }
            else
            {
               changeAllowed = true;
            }
         }
      }

      // First loop required to identify the array index of the user's current
      // room.
      int currentRoom = 0;
      for (int i = 0; i < currentRoomCount; i++)
      {
         if (ChatServerMain.getRoom(i).getRoomIdentity()
                  .equals(jsonObjRec.get("former").toString()))
         {
            currentRoom = i;
            break;
         }
      }

      Boolean roomExists = false;
      ChatServerRoom requestedRoomObject = null;

      // If user is joining for the first time it will have empty string for
      // formerRoom. In this case just add the user to MainHall do not remove
      // it
      // from anywhere.
      if (formerRoom.equals(""))
      {
         jsonObjServ.put("roomid", requestedRoom);
         room = requestedRoom;
      }

      // Checks all rooms to see if the requested room exists. If it exists,
      // instruct the user to change into that room, else instruct the user to
      // stay in the current room.
      // Update the room user content list.
      for (int j = 0; j < currentRoomCount; j++)
      {
         requestedRoomObject = ChatServerMain.getRoom(j);

         // If user is being deleted it will have empty string for
         // requestedRoom. In this case just remove the user from the current
         // room content list, do not add it anywhere.

         if (requestedRoom.equals(""))
         {
            jsonObjServ.put("roomid", requestedRoom);
            room = requestedRoom;

            // If user is being deleted stop listening for incoming messages
            // to
            // allow thread to end.
            this.active = false;

            for (int l = 0; l < currentRoomCount; l++)
            {
               if (ChatServerMain.getRoom(l).getOwnerId().equals(identity)
                   && authenticated == false)
               {
                  ChatServerMain.getRoom(l).setOwnerId("");
               }
            }

            ChatServerMain.getRoom(currentRoom).removeUser(identity);

            roomExists = true;
            break;
         }

         // Otherwise just normal room change.
         else if (requestedRoomObject.getRoomIdentity()
                  .equals(requestedRoom) && changeAllowed)
         {
            jsonObjServ.put("roomid", requestedRoom);

            // Update user list in respective rooms.
            requestedRoomObject.addUser(jsonObjRec.get("identity")
                     .toString());

            if (!formerRoom.equals(""))
            {
               ChatServerMain.getRoom(currentRoom)
                        .removeUser(
                                    jsonObjRec.get("identity").toString());
            }
            roomExists = true;

            // To allow a user to kick user or delete room, need to allow
            // this
            // method to identify the target of the room update and output
            // messages to their output socket.
            for (int k = 0; k < clientCount; k++)
            {
               if (ChatServerMain.getUser(k).getIdentity()
                        .equals(jsonObjRec.get("identity").toString()))
               {
                  ChatServerMain.getUser(k).setRoom(requestedRoom);
                  break;
               }
            }
            break;
         }
      }

      // This covers case where room does not exist or user is banned from
      // existing room.
      if (!roomExists)
      {
         jsonObjServ.put("roomid", room);
         SendJsonObject(jsonObjServ, out);
      }

      // All other cases require multicast to affected rooms.
      else
      {
         // Multicast roomchange message to users in affected rooms.
         for (int k = 0; k < clientCount; k++)
         {
            if (ChatServerMain.getUser(k).getRoom().equals(requestedRoom)
                || ChatServerMain.getUser(k).getRoom()
                         .equals(formerRoom))
            {
               SendJsonObject(jsonObjServ, ChatServerMain.getUser(k)
                        .getWriter());

            }
            // If changing to MainHall requires roomcontents and roomlist
            // messages to be sent to the client as well (except quit case).
            if (ChatServerMain.getUser(k).getIdentity()
                     .equals(jsonObjRec.get("identity").toString())
                && requestedRoomObject.getRoomIdentity().equals(
                                                                "MainHall") &&
                !room.equals(""))
            {
               // Send roomcontents message artificially to avoid loop.
               JSONObject jsonObjMain = new JSONObject();
               jsonObjMain.put("type", "roomcontents");
               jsonObjMain.put("roomid", "MainHall");
               jsonObjMain.put("owner", "");
               jsonObjMain.put("identities",
                               requestedRoomObject.getRoomContents());

               // Send roomlist message.
               RoomList(ChatServerMain.getUser(k).getWriter());
            }
         }

      }
   }

   // Used for:
   // - unicast to client sending #who OK
   // - unicast to new client upon initial connection.
   // - unicast to client joining MainHall. Actually performed articifically in
   // RoomChange method.
   private synchronized void RoomContents(JSONObject jsonObjRec)
   {
      // Parses JSON request from client and prepares JSON response.
      JSONObject jsonObjServ = new JSONObject();
      jsonObjServ.put("type", "roomcontents");
      jsonObjServ.put("roomid", jsonObjRec.get("roomid").toString());

      // Checks all rooms to see if the requested room exists. If it exists,
      // returns the room contents.
      Boolean roomExists = false;
      for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
      {
         if (ChatServerMain.getRoom(i).getRoomIdentity()
                  .equals(jsonObjRec.get("roomid").toString()))
         {
            jsonObjServ.put("identities", ChatServerMain.getRoom(i)
                     .getRoomContents());

            jsonObjServ
                     .put("owner", ChatServerMain.getRoom(i).getOwnerId());
            roomExists = true;
            break;
         }
      }
      if (!roomExists)
      {
         jsonObjServ.put("identities", new JSONArray());
         jsonObjServ.put("owner", "");
      }
      SendJsonObject(jsonObjServ, out);
   }

   // Used for:
   // - unicast when user first joins
   // - unicast to user creating new room
   // - unicast to user deleting a room
   // - unicast to user sending #list
   private synchronized void RoomList(BufferedWriter out)
   {
      JSONArray jsonRoomArray = new JSONArray();

      for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
      {
         JSONObject roomDetail = new JSONObject();
         roomDetail.put("roomid", ChatServerMain.getRoom(i)
                  .getRoomIdentity());
         roomDetail.put("count", ChatServerMain.getRoom(i).getRoomContents()
                  .size());
         jsonRoomArray.add(roomDetail);
      }

      // Parses JSON request from client and prepares JSON response.
      JSONObject jsonObjServ = new JSONObject();
      jsonObjServ.put("type", "roomlist");
      jsonObjServ.put("rooms", jsonRoomArray);

      SendJsonObject(jsonObjServ, out);
   }

   // Used for:
   // - users requesting to create a room - HAS NO SEND but calls the RoomList
   // (unicast).
   private synchronized void CreateRoom(JSONObject jsonObjRec)
   {
      // Check valid name.
      String newRoomId = jsonObjRec.get("roomid").toString();

      Boolean roomNameAvailable = true;

      for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
      {
         if (ChatServerMain.getRoom(i).getRoomIdentity().equals(newRoomId))
         {
            roomNameAvailable = false;
            break;
         }
      }

      if (newRoomId.matches("^[a-zA-Z]+[a-zA-Z0-9]*$")
          && newRoomId.length() > 2 && newRoomId.length() < 33
          && roomNameAvailable)
      {
         ChatServerMain.addRoom(newRoomId, identity);
      }

      // Send roomlist to user.
      RoomList(out);
   }

   private synchronized void KickUser(JSONObject jsonObjRec)
   {
      // Check if sender has permission to kick
      for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
      {
         if (ChatServerMain.getRoom(i).getRoomIdentity()
                  .equals(jsonObjRec.get("roomid"))
             && ChatServerMain.getRoom(i).getRoomContents()
                      .contains(jsonObjRec.get("identity").toString())
             && ChatServerMain.getRoom(i).getOwnerId().equals(identity))
         {
            // Generates dummy join message and passes to RoomChange method.
            JSONObject jsonObjKick = new JSONObject();
            jsonObjKick.put("type", "join");
            jsonObjKick.put("identity", jsonObjRec.get("identity")
                     .toString());
            jsonObjKick.put("former", jsonObjRec.get("roomid").toString());
            jsonObjKick.put("roomid", "MainHall");

            // Dummy initialised variable.
            int userChanging = -1;
            for (int k = 0; k < clientCount; k++)
            {
               if (ChatServerMain.getUser(k).getIdentity()
                        .equals(jsonObjRec.get("identity").toString()))
               {
                  userChanging = k;
                  break;
               }
            }

            // Adds latest ban details to ban attributes.
            ChatServerMain.getUser(userChanging)
                     .addBanRoom(
                                 jsonObjRec.get("roomid").toString());
            ChatServerMain.getUser(userChanging)
                     .addBanStart(
                                  System.currentTimeMillis());
            ChatServerMain.getUser(userChanging)
                     .addBanLength(
                                   (Long) (jsonObjRec.get("time")));

            // Does a clean up of old ban instances.
            for (int j = 0; j < banRoom.size(); j++)
            {
               if (System.currentTimeMillis() - banStart.get(j) > ((Long) (banLength
                        .get(j))).intValue() * 1000)
               {
                  banRoom.remove(j);
                  banStart.remove(j);
                  banLength.remove(j);
               }
            }

            ChatServerMain.getUser(i).RoomChange(jsonObjKick);
            break;
         }
      }
      // If user cannot kick then nothing happens.
   }

   private synchronized void DeleteRoom(JSONObject jsonObjRec)
   {
      // Checks to see if user has permission to delete room.
      for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
      {
         if (ChatServerMain.getRoom(i).getRoomIdentity()
                  .equals(jsonObjRec.get("roomid"))
             && ChatServerMain.getRoom(i).getOwnerId()
                      .equals(this.identity))
         {
            // If they have permission iterate allow users to be removed.
            JSONArray roomContents = ChatServerMain.getRoom(i)
                     .getRoomContents();

            // Empty roomcontents list from the front and send each user a
            // roomchange message.
            while (roomContents.size() != 0)
            {
               JSONObject jsonObjDel = new JSONObject();
               jsonObjDel.put("type", "join");
               jsonObjDel.put("identity", roomContents.get(0).toString());
               jsonObjDel.put("former", jsonObjRec.get("roomid"));
               jsonObjDel.put("roomid", "MainHall");

               RoomChange(jsonObjDel);
            }

            // Call room deletion on this room. Won't clash with room
            // emptying
            // room deletion as the room owner must be still connected to
            // deleter the room.
            ChatServerMain.delRoom(i);

            break;
         }
      }
      // Send RoomList to user to indicate deletion success/fail.
      RoomList(out);
   }

   // Getter and setter-like methods for accessing and modifying object
   // attributes. Synchronization is required to ensure no concurrency issues
   // as
   // these methods are called by different ChatServerThread instances.

   public synchronized String getIdentity()
   {
      return this.identity;
   }

   public synchronized String getRoom()
   {
      return this.room;
   }

   public synchronized void setRoom(String newRoomId)
   {
      this.room = newRoomId;
   }

   public synchronized void addBanRoom(String newBanRoom)
   {
      this.banRoom.add(newBanRoom);
   }

   public synchronized void addBanLength(Long banLength)
   {
      this.banLength.add(banLength);
   }

   public synchronized void addBanStart(Long dateStart)
   {
      this.banStart.add(dateStart);
   }

   public synchronized BufferedWriter getWriter()
   {
      return this.out;
   }

   public synchronized void SendJsonObject(JSONObject jsonObjSent,
                                           BufferedWriter out)
   {
      try
      {
         out.write(jsonObjSent.toString() + "\n");
         out.flush();
      }
      catch (IOException e)
      {
         // No action taken if message fails to send. Message will just be
         // lost.
      }

   }

   private synchronized void RemoveThread()
   {
      for (int i = 0; i < clientCount; i++)
      {
         if (ChatServerMain.getUser(i).equals(this))
         {
            ChatServerMain.delUser(i);
            break;
         }
      }

      // Deprecate client count.
      clientCount--;
   }

   private synchronized void UnexpectedClientRemoval()
   {
      JSONObject jsonObjServ = new JSONObject();
      jsonObjServ.put("type", "roomchange");
      jsonObjServ.put("identity", identity);
      jsonObjServ.put("former", room);
      jsonObjServ.put("roomid", "");

      int userRemove = 0;

      for (int i = 0; i < clientCount; i++)
      {
         if (ChatServerMain.getUser(i).equals(this))
         {
            userRemove = i;
         }

         if (ChatServerMain.getUser(i).getRoom().equals(room))
         {
            SendJsonObject(jsonObjServ, ChatServerMain.getUser(i)
                     .getWriter());
         }
      }

      for (int i = 0; i < ChatServerRoom.getRoomCount(); i++)
      {
         if (ChatServerMain.getRoom(i).getRoomIdentity().equals(room))
         {
            ChatServerMain.getRoom(i).removeUser(identity);
            break;
         }
      }

      ChatServerMain.delUser(userRemove);
      clientCount--;
   }

}
