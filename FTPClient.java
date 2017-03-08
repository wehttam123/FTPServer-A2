/**
 * FastFTP Class
 * FastFtp implements a basic FTP application based on UDP data transmission and
 * alternating-bit stop-and-wait concept
 * @author      Matthew Hylton
 * @version     1.0, 18 Feb 2017
 *
 */
 import java.io.*;
 import java.net.*;
 import java.util.*;

class TimeOutHandler extends TimerTask {

  /**
   * Constructor to initialize the program
   *
   * @param segment	  segment
   * @param IPAddress	IP address
   * @param port		  port
   * @param UDPsocket UDP socket
   * @param time      timeout
   */

    private Segment segment;
    private InetAddress IPAddress;
    private int port;
    private DatagramSocket UDPsocket;
    private int time;

    public TimeOutHandler(Segment segment, InetAddress IPAddress, int port, DatagramSocket UDPsocket, int time)
    {
      this.segment = segment;
      this.IPAddress = IPAddress;
      this.port = port;
      this.UDPsocket = UDPsocket;
      this.time = time;
    }

     @Override
     public void run() {
       try {
         // Resend segment
         DatagramPacket sendPacket =  new DatagramPacket(segment.getBytes(), segment.getLength(), IPAddress, port);
         if (!UDPsocket.isClosed())
         {
           UDPsocket.send(sendPacket);

           // Restart timer
           TimerTask timerTask = new TimeOutHandler(segment, IPAddress, port, UDPsocket, time);
           Timer timer = new Timer(true);
           timer.schedule(timerTask,time);
        }
       }
       catch (Exception e)
       {
         System.out.println("Error: " + e.getMessage());
       }
     }
}

public class FTPClient {

    /**
     * Constructor to initialize the program
     *
     * @param serverName	server name
     * @param server_port	server port
     * @param file_name		name of file to transfer
     * @param timeout		Time out value (in milli-seconds).
     */
  public String name;
  public int port;
  public String filename;
  public int time;

  public final static int MAX_PAYLOAD_SIZE = 1000; // bytes

	public FTPClient(String server_name, int server_port, String file_name, int timeout) {

	/* Initialize values */
  name = server_name;
  port = server_port;
  filename = file_name;
  time = timeout;

	}


    /**
     * Send file content as Segments
     */
	public void send() {

    // File stream
    FileInputStream fileIn = null;

    // Server streams
    DataInputStream serverIn = null;
    DataOutputStream serverOut = null;

    // Segment Payload arrays
    byte[] bytes = new byte[MAX_PAYLOAD_SIZE];
    byte[] ack = new byte[MAX_PAYLOAD_SIZE];

    // list of file segments
    ArrayList<byte[]> Segments = new ArrayList<byte[]>();

    // TCP and UDP sockets
    Socket TCPsocket = null;
    DatagramSocket UDPsocket = null;

    // Server Packets
    DatagramPacket sendPacket = null;
    DatagramPacket receivePacket = null;

    // File segments
    Segment ackData = null;
    Segment segment = null;

    int content = 0; // Number of bytes from read
    int seq = 0;     // Sequence number

    // Timer
    TimerTask timerTask = null;
    Timer timer = null;

    // Read data from file
    try {
      // Create filestream
      fileIn = new FileInputStream(filename);

      // Read until end of file
      while(content != -1){
        content = fileIn.read(bytes);

        // If this is the last byte in the file then shrink array
        if (content < MAX_PAYLOAD_SIZE && content != -1)
        {
          byte[] lastbytes = Arrays.copyOf(bytes, content);
          Segments.add(lastbytes);
        } else if (content != -1) {
          Segments.add(bytes);
        }
        bytes = new byte[MAX_PAYLOAD_SIZE];
      }
    } catch (Exception e)
    {
      System.out.println("Error: " + e.getMessage());
    }

    try {
      // Setup ports
		  TCPsocket = new Socket(name, port);
      UDPsocket = new DatagramSocket();
      InetAddress IPAddress = InetAddress.getByName("localhost");

      // Create necessary streams
      serverIn = new DataInputStream(TCPsocket.getInputStream());
      serverOut = new DataOutputStream(TCPsocket.getOutputStream());

      // initiate TCP Handshake
      while(true)
      {
        // Write filename to server
        serverOut.writeUTF(filename);
        serverOut.flush();

        // Exit if message from server is 0
        if(serverIn.readByte() == 0){break;}
      }

      // Send File by segments
      for (int i = 0; i < Segments.size(); i++) {
        // Send segment
        segment = new Segment(seq,Segments.get(i));
        sendPacket =  new DatagramPacket(segment.getBytes(), segment.getLength(), IPAddress, port);
        UDPsocket.send(sendPacket);

        // Start timer
        timerTask = new TimeOutHandler(segment, IPAddress, port, UDPsocket, time);
        timer = new Timer(true);
        timer.schedule(timerTask,time);

        // Recive ACK
        receivePacket =  new DatagramPacket(ack, ack.length);
        ackData = new Segment();

        //Check sequence number is equal
        do {
          UDPsocket.receive(receivePacket);
          ackData.setBytes(receivePacket.getData());
        } while(ackData.getSeqNum() != seq);

        // Stop timer
        timer.cancel();
        timer.purge();

        // Change sequence number
        if (seq == 0) {seq = 1;} else { seq = 0;}
      }

      // Send EOT
      serverOut.writeByte(0);

      // Clean Up
      fileIn.close();
      serverIn.close();
      serverOut.close();
    }
    catch (Exception e)
    {
      System.out.println("Error: " + e.getMessage());
    }
    finally
    {
      if (TCPsocket != null)
      {
        try
        {
          TCPsocket.close();
        }
        catch (IOException ex) {}
      }
      if (UDPsocket != null)
      {
        UDPsocket.close();
      }
    }
	}

/**
     	* A simple test driver
    	 *
     	*/
	public static void main(String[] args) {

		String server = "localhost";
		String file_name = "";
		int server_port = 8888;
                int timeout = 50; // milli-seconds (this value should not be changed)


		// check for command line arguments
		if (args.length == 3) {
			// either provide 3 parameters
			server = args[0];
			server_port = Integer.parseInt(args[1]);
			file_name = args[2];
		}
		else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java FTPClient server port file");
			System.exit(0);
		}


		FTPClient ftp = new FTPClient(server, server_port, file_name, timeout);

		System.out.printf("sending file \'%s\' to server...\n", file_name);
		ftp.send();
		System.out.println("file transfer completed.");
	}

}
