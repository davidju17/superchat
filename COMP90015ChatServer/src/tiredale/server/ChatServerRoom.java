package tiredale.server;

import org.json.simple.JSONArray;

@SuppressWarnings("unchecked")
public class ChatServerRoom
{
   // Objects of ChatServerRoom contain attributes of room name, room owner ID,
   // and the current users in the room. A static variable roomCount also tracks
   // how many rooms exist. 
   
   private String name = "";
   private String ownerId;
   private JSONArray roomContents;

   private static int roomCount = 0;

   public ChatServerRoom(String roomId, String ownerId)
   {
      this.name = roomId;
      this.ownerId = ownerId;
      this.roomContents = new JSONArray();

      roomCount++;
   }

   // Getter and setter-like methods for accessing and modifying room
   // attributes. Synchronization is required to ensure no concurrency issues as
   // these methods are called by different ChatServerThread instances.

   public synchronized String getRoomIdentity()
   {
      return this.name;
   }

   public synchronized String getOwnerId()
   {
      return this.ownerId;
   }

   public synchronized JSONArray getRoomContents()
   {
      return this.roomContents;
   }

   public synchronized static int getRoomCount()
   {
      return roomCount;
   }

   public synchronized void setOwnerId(String ownerId)
   {
      this.ownerId = ownerId;
   }

   public synchronized void addUser(String user)
   {
      this.roomContents.add(user);
   }

   public synchronized void removeUser(String user)
   {
      System.out.println(name+" contents "+roomContents);
      
      this.roomContents.remove(user);
      
      System.out.println(name+" contents "+roomContents);
      System.out.println("Room count = "+roomCount);
      
      System.out.println("");
      System.out.println("ownerId = "+ownerId);
      System.out.println("Room name: "+name);
      System.out.println("Number of occupants = "+roomContents.size());

      // If the room is empty after the user is removed and the owner has left
      // then this room is deleted.
      if (ownerId.equals("") && !name.equals("MainHall") && roomContents.size() == 0)
      {
         for (int i = 0; i < roomCount; i++)
         {
            if (ChatServerMain.getRoom(i).getRoomIdentity().equals(name))
            {
               ChatServerMain.delRoom(i);
               break;
            }
         }
      }
      
      System.out.println(name+" contents "+roomContents);
      System.out.println("Room count = "+roomCount);
   }

   public synchronized static void depRoomCount()
   {
      roomCount--;
   }
}
