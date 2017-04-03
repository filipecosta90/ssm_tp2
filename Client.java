/* ------------------
   Client
usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
---------------------- */

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

public class Client{

  //GUI
  //----
  JFrame f = new JFrame("Client");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton tearButton = new JButton("Teardown");
  JPanel mainPanel = new JPanel();
  JPanel buttonPanel = new JPanel();
  JLabel iconLabel = new JLabel();
  ImageIcon icon;

  //RTP variables:
  //----------------
  DatagramPacket rcvdp; //UDP packet received from the server
  DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
  static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets

  Timer timer; //timer used to receive data from the UDP socket
  byte[] buf; //buffer used to store data received from the server


  Map<Integer, Integer> time_data_tracker;
  Map<Integer, Integer> accum_time_data_tracker;
    Map<Integer, Double>  time_bits_second_tracker;
  int accum_packet_length; 
  //RTSP variables
  //----------------
  //rtsp states
  final static int INIT = 0;
  final static int READY = 1;
  final static int PLAYING = 2;
  static int state; //RTSP state == INIT or READY or PLAYING
  Socket RTSPsocket; //socket used to send/receive RTSP messages
  //input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String VideoFileName; //video file to request to the server
  int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
  int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)

  final static String CRLF = "\r\n";

  //Video constants:
  //------------------
  static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
  private  long first_frame_time;
  public static final int MSECS_PER_SEC = 1000;


  //--------------------------
  //Constructor
  //--------------------------
  public Client() {

    //build GUI
    //--------------------------

    //Frame
    f.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });

    //Buttons
    buttonPanel.setLayout(new GridLayout(1,0));
    buttonPanel.add(setupButton);
    buttonPanel.add(playButton);
    buttonPanel.add(pauseButton);
    buttonPanel.add(tearButton);
    setupButton.addActionListener(new setupButtonListener());
    playButton.addActionListener(new playButtonListener());
    pauseButton.addActionListener(new pauseButtonListener());
    tearButton.addActionListener(new tearButtonListener());

    //Image display label
    iconLabel.setIcon(null);

    //frame layout
    mainPanel.setLayout(null);
    mainPanel.add(iconLabel);
    mainPanel.add(buttonPanel);
    iconLabel.setBounds(0,0,380,280);
    buttonPanel.setBounds(0,280,380,50);

    f.getContentPane().add(mainPanel, BorderLayout.CENTER);
    f.setSize(new Dimension(390,370));
    f.setVisible(true);

    //init timer
    //--------------------------
    timer = new Timer(20, new timerListener());
    timer.setInitialDelay(0);
    timer.setCoalesce(true);

    //allocate enough memory for the buffer used to receive data from the server
    buf = new byte[15000];
    time_data_tracker = new TreeMap<Integer, Integer>();
    accum_time_data_tracker = new TreeMap<Integer, Integer>();
      time_bits_second_tracker =  new TreeMap<Integer, Double>();
    accum_packet_length = 0; 

  }

  //------------------------------------
  //main
  //------------------------------------
  public static void main(String argv[]) throws Exception
  {
    //Create a Client object
    Client theClient = new Client();

    //get server RTSP port and IP address from the command line
    //------------------
    int RTSP_server_port = Integer.parseInt(argv[1]);
    System.out.println("Server port " + RTSP_server_port );
    String ServerHost = argv[0];
    InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);
    //get video filename to request:
    VideoFileName = argv[2];

    //Establish a TCP connection with the server to exchange RTSP messages
    //------------------
    try{
      theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

      //Set input and output stream filters:
      RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()) );
      RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()) );

      //init RTSP state:
      state = INIT;
    } catch (UnknownHostException e) {
      e.printStackTrace();
      System.exit(0); 
    } catch (ConnectException e) {
      System.out.println("Error in the connection to: " + ServerHost + " at port: " + RTSP_server_port );
      e.printStackTrace();
      System.exit(0); 
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(0); 
    }
  }


  //------------------------------------
  //Handler for buttons
  //------------------------------------

  //.............
  //TO COMPLETE
  //.............

  //Handler for Setup button
  //-----------------------
  /* 
   * SETUP
   * A SETUP request specifies how a single media stream must be transported. 
   * This must be done before a PLAY request is sent. 
   * The request contains the media stream URL and a transport specifier. 
   */ 
  class setupButtonListener implements ActionListener{
    public void actionPerformed(ActionEvent e){

      System.out.println("Setup Button pressed !");
      if (state == INIT)
      {
        //Init non-blocking RTPsocket that will be used to receive data
        try{
          //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
          RTPsocket = new DatagramSocket(RTP_RCV_PORT);

          //set TimeOut value of the socket to 5msec.
          timer.setDelay(5);

        }
        catch (SocketException se)
        {
          System.out.println("Socket exception: "+se);
          System.exit(0);
        }

        //init RTSP sequence number
        RTSPSeqNb = 1;

        //Send SETUP message to the server
        send_RTSP_request("SETUP");

        //Wait for the response
        if (parse_server_response() != 200)
          System.out.println("Invalid Server Response");
        else
        {
          //change RTSP state and print new state
          state = READY;
          System.out.println("New RTSP state: " + state );
        }
      }//else if state != INIT then do nothing
    }
  }

  //Handler for Play button
  //-----------------------
  class playButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

      System.out.println("Play Button pressed !");

      if (state == READY)
      {
        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send PLAY message to the server
        send_RTSP_request("PLAY");

        //Wait for the response
        if (parse_server_response() != 200)
          System.out.println("Invalid Server Response");
        else
        {
          //change RTSP state and print out new state
          state = PLAYING;
          System.out.println("New RTSP state: " + state );

          //start the timer
          timer.start();
        }
      }//else if state != READY then do nothing
    }
  }


  //Handler for Pause button
  //-----------------------
  class pauseButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

      System.out.println("Pause Button pressed !");
      if (state == PLAYING)
      {
        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send PAUSE message to the server
        send_RTSP_request("PAUSE");

        //Wait for the response
        if (parse_server_response() != 200)
          System.out.println("Invalid Server Response: " + parse_server_response() );
        else
        {
          //change RTSP state and print out new state
          state = READY;
          System.out.println("New RTSP state: " + state );

          //stop the timer
          timer.stop();
        }
      }
      //else if state != PLAYING then do nothing
    }
  }

  //Handler for Teardown button
  //-----------------------
  class tearButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){

      System.out.println("Teardown Button pressed !");

      //increase RTSP sequence number
      RTSPSeqNb++;

      //Send TEARDOWN message to the server
      send_RTSP_request("TEARDOWN");

      //Wait for the response
      if (parse_server_response() != 200)
        System.out.println("Invalid Server Response");
      else
      {
        //change RTSP state and print out new state
        state = INIT;

        System.out.println("New RTSP state: " + state );

        //stop the timer
        timer.stop();

          //save csv results
          save_csv_results();
          //exit
        System.exit(0);
      }
    }
  }


  //------------------------------------
  //Handler for timer
  //------------------------------------

  class timerListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {

      //Construct a DatagramPacket to receive data from the UDP socket
      rcvdp = new DatagramPacket(buf, buf.length);

      try{
          long datagram_start_time = System.currentTimeMillis();
        //receive the DP from the socket:
        RTPsocket.receive(rcvdp);
        //create an RTPpacket object from the DP
        RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
          long datagram_end_time = System.currentTimeMillis();
          int packet_length = rtp_packet.getlength();
    
          
          long elapsedTime = datagram_end_time - datagram_start_time;
          double bits_per_second = (8*packet_length) / ((double)elapsedTime / 1000.0f )  ;

        // Calculate statistics about the session.
        int diff_time;
        if (first_frame_time == 0L) {
          first_frame_time = System.currentTimeMillis();
          diff_time = 0;
        } else {
          long current_frame_time = System.currentTimeMillis();
          diff_time = (int) (current_frame_time - first_frame_time);
        }
        //int m_secs = diff_time % MSECS_PER_SEC;
        accum_packet_length += packet_length;
          time_bits_second_tracker.put(diff_time, bits_per_second );
        time_data_tracker.put(diff_time, packet_length );
        accum_time_data_tracker.put(diff_time, accum_packet_length );

        //print important header fields of the RTP packet received:
        System.out.println("Got RTP packet with SeqNum # "+rtp_packet.getsequencenumber()+" TimeStamp "+rtp_packet.gettimestamp()+" ms, of type "+rtp_packet.getpayloadtype());

        //print header bitstream:
        rtp_packet.printheader();
        System.out.println("Length " + rtp_packet.getlength());
        //get the payload bitstream from the RTPpacket object
        int payload_length = rtp_packet.getpayload_length();
        byte [] payload = new byte[payload_length];
        rtp_packet.getpayload(payload);

        //get an Image object from the payload bitstream
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.createImage(payload, 0, payload_length);

        //display the image as an ImageIcon object
        icon = new ImageIcon(image);
        iconLabel.setIcon(icon);
      }
      catch (InterruptedIOException iioe){
        //System.out.println("Nothing to read");
      }
      catch (IOException ioe) {
        System.out.println("Exception caught: "+ioe);
      }
    }
  }

  //------------------------------------
  //Parse Server Response
  //------------------------------------
  private int parse_server_response() 
  {
    int reply_code = 0;

    try{
      //parse status line and extract the reply_code:
      String StatusLine = RTSPBufferedReader.readLine();
      System.out.println("RTSP Client - Received from Server:");
      System.out.println(StatusLine);

      StringTokenizer tokens = new StringTokenizer(StatusLine);
      tokens.nextToken(); //skip over the RTSP version
      reply_code = Integer.parseInt(tokens.nextToken());

      //if reply code is OK get and print the 2 other lines
      if (reply_code == 200)
      {
        String SeqNumLine = RTSPBufferedReader.readLine();
        System.out.println(SeqNumLine);

        String SessionLine = RTSPBufferedReader.readLine();
        System.out.println(SessionLine);

        //if state == INIT gets the Session Id from the SessionLine
        tokens = new StringTokenizer(SessionLine);
        tokens.nextToken(); //skip over the Session:
        RTSPid = Integer.parseInt(tokens.nextToken());
      }
    }
    catch(Exception ex)
    {
      System.out.println("Exception caught: "+ex);
      System.exit(0);
    }

    return(reply_code);
  }

  //------------------------------------
  //Send RTSP Request
  //------------------------------------

  //.............
  //TO COMPLETE
  //.............

  private void send_RTSP_request(String request_type)
  {
    try{
      //Use the RTSPBufferedWriter to write to the RTSP socket

      //write the request line:
      RTSPBufferedWriter.write( request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);

      //write the CSeq line: 
      RTSPBufferedWriter.write( "CSeq: " + RTSPSeqNb + CRLF );

      /*
       * check if request_type is equal to "SETUP" and in this case write the Transport: 
       * line advertising to the server the port used to receive the RTP packets RTP_RCV_PORT 
       */
      /*
       * This specifier (transport) typically includes a local port for receiving RTP data (audio or video), 
       * and another for RTCP data (meta information). 
       * The server reply usually confirms the chosen parameters, and fills in the missing parts, 
       * such as the server's chosen ports. Each media stream must be configured using SETUP 
       * before an aggregate play request may be sent.
       * */
      if (request_type.equals("SETUP")){
        RTSPBufferedWriter.write( "Transport: RTP/UDP; client_port= "+RTP_RCV_PORT+CRLF); 
      }
      //otherwise, write the Session line from the RTSPid field
      else{
        RTSPBufferedWriter.write( "Session: " + RTSPid + CRLF );
      }
      RTSPBufferedWriter.flush();
    }
    catch(Exception ex)
    {
      System.out.println("Exception caught: "+ex);
      System.exit(0);
    }
  }

  private void save_csv_results(){

    

    try{
        StringBuilder sb = new StringBuilder();
        String savestr = "data_time.csv";
      File f = new File(savestr);
      PrintWriter out = null;
      out = new PrintWriter(savestr);
      sb.append("ms");
      sb.append(',');
      sb.append("bytes");
      sb.append('\n');
      for (Map.Entry<Integer, Integer> entry : time_data_tracker.entrySet()){
          sb.append(entry.getKey());
          sb.append(',');
          sb.append(entry.getValue());
          sb.append('\n');
      }
      out.write(sb.toString());
      out.close();
    } catch (FileNotFoundException e) {
    }
      
      
      try{
          StringBuilder sb1 = new StringBuilder();
          String savestr1 = "accum_date_time.csv";

          File f = new File(savestr1);
          PrintWriter out1 = null;
          out1 = new PrintWriter(savestr1);
          sb1.append("ms");
          sb1.append(',');
          sb1.append("bytes");
          sb1.append('\n');
          for (Map.Entry<Integer, Integer> entry : accum_time_data_tracker.entrySet()){
              sb1.append(entry.getKey());
              sb1.append(',');
              sb1.append(entry.getValue());
              sb1.append('\n');
          }
          out1.write(sb1.toString());
          out1.close();
      } catch (FileNotFoundException e) {
      }
      
      try{
          StringBuilder sb = new StringBuilder();
          String savestr = "bits_per_sec.csv";
          File f = new File(savestr);
          PrintWriter out = null;
          out = new PrintWriter(savestr);
          sb.append("ms");
          sb.append(',');
          sb.append("bps");
          sb.append('\n');
          for (Map.Entry<Integer, Double> entry : time_bits_second_tracker.entrySet()){
              sb.append(entry.getKey());
              sb.append(',');
              sb.append(entry.getValue());
              sb.append('\n');
          }
          out.write(sb.toString());
          out.close();
      } catch (FileNotFoundException e) {
      }
      

  }
}//end of Class Client
