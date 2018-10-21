
//**************************************************************
//                     CssiConsumer.java
//**************************************************************

package com.cssi;    // TODO: rename this to something cssi

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Properties;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.FileNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solacesystems.jms.SupportedProperty;

//--------------------------------------------
//    CSSI
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

//--------------------------------------------
//    CSSI

//   d/l from ????:   postgresql-42.2.5.jar
//dup: import java.sql.Connection;
//dup: import java.sql.DriverManager;
//dup: import java.sql.SQLException;
import java.sql.*;
import java.io.StringReader;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import java.util.regex.*;

//--------------------------------------------

/**
 * Creates a single consumer and consumes data into a given directory.
 */
// public class SingleThreadedConsumer implements ExceptionListener, MessageListener
public class CssiConsumer implements ExceptionListener, MessageListener
{
   // Variables that are read in from consumerConnection.prop
   private String theUsername;
   private String thePassword;
   private String theConnectionURL;
   private String theConnectionFactory;
   private String theDestination;
   private String theJNDIContext;
   private String theOutputDirectory;
   private String theSolaceVPN;

   private Context theContext;
   //pg: private Connection theConnection;
   private javax.jms.Connection theConnection;
   private Session theSession;
   private MessageConsumer theConsumer;
   private File theOutputDirectoryFile;
   private static int theConsumerConnectionTime;
   private static int theConsumerReconnectionTimeOut;
   private static int theConsumerNumberOfRetryReconnection;
   private static final int MIN_TO_MS_MULTIPLIER_CONSUMING = 60000;
   private static final int MS_CONSUMER = 1000;

   private String thePropertiesFileLocation;
   private boolean theConnected = false;

   // The class logger
   private static final Logger theLogger = LoggerFactory.getLogger(CssiConsumer.class);

   // ------------------------------------
   //    CSSI
   // postgres items
   private final String url      = "jdbc:postgresql://localhost:5433/swim";
   private final String user     = "postgres";
   private final String password = "425thirdst";
   //v9 private final String url      = "jdbc:postgresql://localhost:5432/cssitest";
   //v9 private final String user     = "postgres";
   //v9 private final String password = "cssisuper";
   public java.sql.Connection pgconn;   // explicity state which Connection to use

    boolean write_out_xml = false;
    String jms_destination_name = "none";

   // ------------------------------------
   //
   /**
    * Constructor that loads properties and establishes a connection
    *
    * @param aPropertiesFileLocation
    *           Where to look for the properties file.
    */

   CssiConsumer(String aPropertiesFileLocation)
   {
      thePropertiesFileLocation = aPropertiesFileLocation;

      pgconn       = pgconnect();   // postgres connection
      theConnected = setup();       // SWIM connection
   }


   /**
    * Establishes a SWIM connection
    *
    * @return if it's able to set up the SWIM connection or not
    * @see boolean
    */
   private boolean setup()
   {
      loadPropertiesFromFile(thePropertiesFileLocation);

      // Set up environment for initial context
      Hashtable<String, String> environment = new Hashtable<String, String>();
      environment.put(Context.INITIAL_CONTEXT_FACTORY, theJNDIContext);
      environment.put(Context.PROVIDER_URL, theConnectionURL);
      environment.put(Context.SECURITY_PRINCIPAL, theUsername);
      environment.put(Context.SECURITY_CREDENTIALS, thePassword);
      if (theSolaceVPN != null)
      {
         environment.put(SupportedProperty.SOLACE_JMS_VPN, theSolaceVPN);
      }

      try
      {
         // Get context
         theContext = new InitialContext(environment);

         // Get ConnectionFactory
         ConnectionFactory connectionFactory = (ConnectionFactory) theContext.lookup(theConnectionFactory);

         // Get Destination
         // TODO: allow send_to_db to use 'destination' to select handler !!!
         jms_destination_name    = (theContext.lookup(theDestination)).toString();
         //System.out.println("jms_dn=" + jms_destination_name);
         //System.exit(1);

         Destination destination = (Destination) theContext.lookup(theDestination);

         // Create Connection using the user name and password
         // Also set listener to detect issues with the connection
         theConnection = connectionFactory.createConnection(theUsername, thePassword);
         theConnection.setExceptionListener(this);

         // Create session
         theSession = theConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Create consumer to send the message, and set it's message listener
         theConsumer = theSession.createConsumer(destination);
         theConsumer.setMessageListener(this);
         theConnection.start();
         return true;
      }
      catch (Exception e)
      {
         theLogger.error("Error setting up JMS", e);
         return false;
      }
   }

   /**
    * Function set if the consumer connected or not.
    *
    * @param aConnected
    *           Will be true if consumer connected once.
    */
   public void setConnected(boolean aConnected)
   {
      theConnected = aConnected;
   }

   /**
    * Function that reads in connection data from a file.
    *
    * @param aPropertiesFileLocation
    *           Where to look for the properties file.
    */
   public void loadPropertiesFromFile(String aPropertiesFileLocation)
   {
      // This constructor simply reads in the properties file and gets the
      // connection information.
      Properties theProp = new Properties();
      try
      {
         FileInputStream fileInputStream = new FileInputStream(aPropertiesFileLocation);
         theProp.load(fileInputStream);
         theUsername = theProp.getProperty("USERNAME");
         thePassword = theProp.getProperty("PASSWORD");
         theConnectionURL = theProp.getProperty("CONNECTION_URL");
         theConnectionFactory = theProp.getProperty("CONNECTION_FACTORY");
         theDestination = theProp.getProperty("JMS_DESTINATION_NAME");
         theOutputDirectory = theProp.getProperty("OUTPUT_DIRECTORY");
         setOutputDirectory(theOutputDirectory);
System.out.println(">>>" + theOutputDirectory);

         theJNDIContext = theProp.getProperty("CONTEXT_FACTORY");
         theSolaceVPN = theProp.getProperty("SOLACE_VPN");
         theConsumerConnectionTime = Integer.parseInt(theProp.getProperty("CONSUMER_CONNECTION_TIME"));
         theConsumerNumberOfRetryReconnection = Integer.parseInt(theProp.getProperty("CONSUMER_RETRY_RECONNECTION"));
         theConsumerReconnectionTimeOut = Integer.parseInt(theProp.getProperty("CONSUMER_RECONNECTION_TIME_OUT"));
         fileInputStream.close();
      }
      catch (FileNotFoundException e)
      {
         theLogger.error("Error The properties file not found: ", aPropertiesFileLocation, e);
         System.exit(1);
      }
      catch (ClassCastException e)
      {
         theLogger.error("The properties file contains non-string values: ", e);
         System.exit(1);
      }
      catch (IOException e)
      {
         theLogger.error("IO Exception: ", e);
         System.exit(1);
      }
   }

   /**
    * Sets the directory to output consumed messages to.
    *
    * @param anOutputDirectory
    *           The directory to output messages to.
    */
   public void setOutputDirectory(String anOutputDirectory)
   {
System.out.println("2>>" + anOutputDirectory);
      if (anOutputDirectory == null)
      {
         throw new IllegalArgumentException("Output directory is null");
      }
      File theFile = new File(anOutputDirectory);
      if (!theFile.isDirectory() && theFile.mkdirs())
      {
         throw new IllegalArgumentException("Invalid output directory: " + anOutputDirectory);
      }
      if (!theFile.canWrite())
      {
         throw new IllegalArgumentException("Unable to write to directory: " + anOutputDirectory);
      }
      this.theOutputDirectoryFile = theFile;
   }

   /** Closes everything. **/
   public void close()
   {
      try
      {
         theConsumer.close();
      }
      catch (Exception e)
      {
         theLogger.error("Problem closing consumer: ", e);
      }
      try
      {
         theSession.close();
      }
      catch (Exception e)
      {
         theLogger.error("Problem closing session: ", e);
      }
      try
      {
         theConnection.close();
      }
      catch (Exception e)
      {
         theLogger.error("Problem closing connection: ", e);
      }
      try
      {
         theContext.close();
      }
      catch (Exception e)
      {
         theLogger.error("Problem closing context: ", e);
      }
   }

   /**
    * Method called when listener detects message. Processes the message and
    * writes it to an output file in a new thread.
    *
    * @param aMessage
    *           The consumed message.
    */
   public void onMessage(Message aMessage)
   {
      OutputStream outputStream = null;

      // set up a file to write the message to
      String fileName;
      try
      {
         String cssiMessage = null;
         if (aMessage.propertyExists("filename"))
         {
            fileName = aMessage.getStringProperty("filename") + ".xml" ;
         }
         else
         {
            fileName = String.valueOf(aMessage.getJMSTimestamp()) + ".xml" ;
         }
         File outputFile = new File(theOutputDirectoryFile + File.separator + fileName);

         if (write_out_xml) {
             outputStream = new FileOutputStream(outputFile);
         };

         // Bytes or Text Messages are supported
         // an error is thrown for other message types
         if (aMessage instanceof BytesMessage)
         {
            BytesMessage theMessage = (BytesMessage) aMessage;
            byte[] message = new byte[(int) theMessage.getBodyLength()];

            if (write_out_xml) {
                outputStream.write(theMessage.readBytes(message));
            };

            // -------------------------------------
            // NOTE: first 4 lines are almost same as above
            BytesMessage byteMessage = null; // set byteMessage

            byte[] byteData = null;
            byteData = new byte[(int) theMessage.getBodyLength()];

            byteMessage.readBytes(byteData);
            byteMessage.reset();    // stupid java: reset cursor position
            cssiMessage = new String(byteData);
            // -------------------------------------
         }
         else if (aMessage instanceof TextMessage)
         {
            if (write_out_xml) {
                outputStream.write(((TextMessage) aMessage).getText().getBytes());
            };
            // -------------------------------------
            // however, save just the xml for our db insert processing...
            cssiMessage = ((TextMessage) aMessage).getText();
            // -------------------------------------
         }
         else
         {
            theLogger.error("Received incorrect message type.");
         }

         if (write_out_xml) {
             outputStream.flush();
             outputStream.close();
             String logMessage = "Received File: " + outputFile.getAbsolutePath();
             theLogger.info("\n");                                      // wt
             theLogger.info("--------------------------- message:");    // wt
             theLogger.info(logMessage);
         }

         // now send both XML text and the filename it was written to

         send_to_db( cssiMessage, fileName  );

         //theLogger.info("--------------------------- done");        // wt
         //theLogger.info("\n");                                      // wt
      }
      catch (Exception e)
      {
         theLogger.error("Error receiving message: ", e);
      }
   }

   /**
    * Will detect errors with the connection and invoke recover consumer method.
    * Will retry if there is a initial connection Retry by set number of time
    * with a time between retry attempts
    *
    * @param anError
    *           The error detected.
    */
   public void onException(JMSException anError)
   {
      int i = 0;
      while (i < theConsumerNumberOfRetryReconnection && theConnected)
      {
         theLogger.info("Reconnecting ..........");

         try
         {
            theConnection.close(); // unregisters the ExceptionListener
         }
         catch (Exception ex)
         {
            // I will get an Exception anyway, since the connection to the
            // server is
            // broken, but close() frees up resources associated with the
            // connection
         }

         try
         {
            Thread.sleep(theConsumerReconnectionTimeOut * MS_CONSUMER);
         }
         catch (InterruptedException ex)
         {
            theLogger.error("Error with thread sleeping: ", ex);
         }

         boolean setupOK = setup();

         if (setupOK)
         {
            theLogger.info("Connection re-established");
            return;
         }
         else
         {
            theLogger.info("Re-creating connection failed, retrying ...");
         }
         i++;
      }
      theConnected = false;
   }

   /**
    * Creates a consumer and begins consuming.
    *
    * @param args
    *           The command line arguments. The first command line input is used
    *           to find the connection properties file
    */
   public static void main(String[] args)
   {

      //SingleThreadedConsumer consumer = null;
      CssiConsumer consumer = null;

      // Load all properties and initialize.
      try
      {
         //consumer = new SingleThreadedConsumer(args[0]);
         consumer = new CssiConsumer(args[0]);
      }
      catch (Exception e)
      {
         theLogger.error("Error creating the consumer: ", e);
         System.exit(1);
      }

      // Wait for this long while the consumer processes messages.
      try
      {
         Thread.sleep(theConsumerConnectionTime * MIN_TO_MS_MULTIPLIER_CONSUMING);
      }
      catch (InterruptedException e)
      {
         consumer.setConnected(false);
         theLogger.error("The Consumer was not able to stay connected for the specified time:", e);
      }
      finally
      {
         consumer.close();
      }
   }

   //=============================================================================
   // beginning of cssi part
   // TODO move this section to its own src .java file

   /**
     * Connect to the PostgreSQL database
     *
     * @return a Connection object
     */
    private java.sql.Connection pgconnect() {
        java.sql.Connection pgconn2 = null;
        try {
            pgconn2 = DriverManager.getConnection(url, user, password);
            pgconn2.setAutoCommit(false);
            System.out.println("Connected to the PostgreSQL server successfully.");

        } catch (SQLException e) {
            System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
            System.out.println("NOT Connected to the PostgreSQL server");
            System.err.println( e.getClass().getName()+"::" + e.getMessage() );
            System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
            System.exit(1);
        }
        return pgconn2;
    }
   //=============================================================================

   /**
     * Parse string as XML and write to PostGIS
     *
     * @return none  ; TODO: fail if bad insert (so caller can write out xml file)
     */

String insert;
String values;

String processing_this_field = "";
String processing_this_value = "";
String xml_tree              = "?>";   // tree (list as string) of xml tree depth

String src_airport = "??";   // stdds


    //=============================================================================

    // message is XML string
    // in progress: make separate 'handler()' for all 3 (now 4) of them

    //=============================================================================

    public void send_to_db( String message, String filename)
    {
        processing_this_field = "";   // TODO: move these elsewhere!
        processing_this_value = "";   // TODO: move these elsewhere!
        src_airport = "??";           // TODO: move these elsewhere!

        try {

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser      = factory.newSAXParser();
            DefaultHandler my_handler = null;

            // 'destination' is now available, we use that to
            // select the following...

            switch (jms_destination_name) {

            case "CSSI.STDDS.Q03.OUT":
               my_handler = new q03_Handler();    // surface
                break;

            case "CSSI.STDDS.Q02.OUT":
               my_handler = new q02_Handler();   // terminal
                break;

            case "CSSI.FDPS.Q01.OUT":
               my_handler = new q_f01_Handler();  // enroute
                break;

            case "CSSI.STDDS.Q01.OUT":
               my_handler = new q01_Handler();  // rvr
                break;

            default:
                System.out.println("jms_dn=" + jms_destination_name);
                System.exit(1);
            };

            saxParser.parse(new org.xml.sax.InputSource(new StringReader(message)),
                          my_handler);

        } catch (Exception e) {
            System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
            e.printStackTrace();
            System.err.println( "NOT saxParser"  );
            System.err.println( e.getClass().getName()+"::" + e.getMessage() );
            System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
            System.exit(1);    // stop on error (prob. TAStatus msg)
        }
    } // send_to_db

//=============================================================================

// ------------------------------- print (or not) xml tree
public void xprint( String s ) {
      //System.out.println( s );    // comment out to disable tree printing
};

// ------------------------------- pop element from xml_tree string
// consider: making this a class...

// remove this element ( "foo>" ) from xml tree (really a string)
public String xml_pop( String xml_tree ) {

    // consider just some sort of: length(s)-1 ???
    int p = xml_tree.lastIndexOf('>');   // first, remove just the '>'
    xml_tree = xml_tree.substring(0,p);

    int q = xml_tree.lastIndexOf('>');   // s.b. '>' before this element

    /* ********************* nope, check failed
    // just to make sure, that last element must be the current one...
    // TODO: check against ???
    if ( !( xml_tree.substring(q+1,p).equals( processing_this_field ))) {
        System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
        System.err.println( "trying to remove bad tree level"  );
        System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
        System.exit(1);
    };
    ********************** nope, check failed */

    xml_tree = xml_tree.substring(0,q+1);

    return( xml_tree);
};

// ------------------------------- make a string for geometry/point

String make_point( String lon, String lat ) {

    String pt = "???";

    try {

        String wkt = "'POINT(" + lon + ' ' + lat + ")'" ;
        pt = "ST_GeomFromText(" + wkt + ", 4326)";

    } catch (Exception e) {
        // saw once: position>35.077778-92.939167
        System.out.println("    position> BAD FORMAT" );
        return "BAD";

    }
    return pt;
};

// =========================================================================
// =================== xml:     asdexmsg / smesmessage / surfaceMovement
// =================== config:  CSSI.STDDS.Q03.OUT
// =================== postgis: asdex
// =========================================================================

public class q03_Handler extends DefaultHandler {

    Map< String, String > sql_insert  = null;

    // only process useful values...
    String good_ones = " seqNum time track stid latitude longitude " +
                       " speed heading quality " +
                       " startRange endRange startAzimuth endAzimuth ";

    String quote_these = " src_airport src time ";

    // ------------------------------- start

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {

        // remove "ns2:"
        if (qName.startsWith("ns2:")) { qName = qName.substring(4); };

        processing_this_field = qName;  //.toLowerCase() ;
        xprint("+++start element>" + processing_this_field );

        // ------------------ no attributes in q02 or q03

        if ( (processing_this_field.equals("asdexMsg"))) {
             //(processing_this_field.equals("SurfaceMovementEventMessage")) ) <ab>
            xprint("\n");

        } else if ( (processing_this_field.equals("positionReport")) ||
                    (processing_this_field.equals("SurfaceMovementEventMessage")) ) {

            xprint("\n" );

            // start a new sql dictionary/map
            sql_insert  = new HashMap< String, String> ();

            sql_insert.put( "src", src_airport ); // everyone has one of these

            xml_tree = processing_this_field + ">";    // start building a new tree

        };  // else {

        // add this element to xml tree
        xml_tree += processing_this_field + ">" ;
        xprint("xml_tree +==" + xml_tree );

        //};
    }  // startElement

    // ------------------------------- value / contents

    public void characters(char ch[], int start, int length) throws SAXException {

        processing_this_value = new String(ch, start, length).trim();

        // this field is 'outside' of the actual trees that we ouput
        if (processing_this_field == "airport") {
            src_airport = processing_this_value;
        };

        // not for asdex, that xml only has src (above)
        //   || (processing_this_field == "airport") ) {   // status has this

            xprint("    put: " + processing_this_field + ":" + processing_this_value );

            if (good_ones.contains(" " + processing_this_field + " ")) {

                sql_insert.put( processing_this_field, processing_this_value );

            } else {
                    xprint("NOT using field:" + processing_this_field);
            };
        //};
    };  // characters

    // -------------------------------

    public void endElement(String uri, String localName,
                String qName) throws SAXException
    {
        // remove "ns2:"
        if (qName.startsWith("ns2:")) { qName = qName.substring(4); };

        String qn = qName;  //.toLowerCase();

        // this one didn't participate in tree structure (should it have?)
        if (qName.equals("asdexMsg"))                 { return ; }

//xprint("xml_tree qn remove:" + qn );
//xprint("xml_tree b4 remove:" + xml_tree );

        xml_tree = xml_pop( xml_tree );

        xprint("xml_tree -==" + xml_tree ); //dbg:

        if (qName.equals("positionReport")) {

            /* ************************************** */
            System.out.println( String.format( "%s  %-9s  %6s  %5s  %8s  %8s",
                            sql_insert.get("src"),
                            sql_insert.get("time"),
                            sql_insert.get("track"),
                            sql_insert.get("seqNum"),
                            sql_insert.get("latitude"),
                            sql_insert.get("longitude") ));
            /* ************************************** */

            String sql = prepare_sql_statement( "asdex", quote_these, sql_insert);

            actually_do_insert( sql );

            xml_tree = "mt>";
        } else if ( (qName.equals( "SurfaceMovementEventMessage")) ) {

                System.out.println("HELP: write SurfaceMovementEventMessage");
        };
    };
}; // handler

// =========================================================================
// =================== xml:     TATrackAndFlightPlan
// =================== config:  CSSI.STDDS.Q02.OUT
// =================== postgis: stdds, flightplan, enhanced
// =========================================================================

public class q02_Handler extends DefaultHandler {

    Map< String, String > sql_insert  = null;

    String quote_these = " src_airport src " +   // common
        // the text fields in track
        "status mrtTime acAddress reportedBeaconCode " +
            // the text fields in flightplan:
        " acid acType airport category cps dbi ECID entryFix exitFix " +
        " flightRules lld ocr ptdTime rnav runway " +
        "scratchPad1 scratchPad2 type " +
            // the text fields in enhancedData:
        "eramGufi sfdpsGufi departureAirport destinationAirport";
        //"eramGufi sfdpsGufi departureAirport arrivalAirport destinationAirport";

    String trackNum = "9999";   // save this from <track> to be put into <flightplan>

    // ------------------------------- start

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {

        // remove "ns2:"
        if (qName.startsWith("ns2:")) { qName = qName.substring(4); };

        processing_this_field = qName;  //.toLowerCase() ;

        //dbg: System.out.println("+++start element>" + processing_this_field ); //dbg:

        // ------------------ no attributes in q02

        // print nicely
        if ( (processing_this_field.equals("TATrackAndFlightPlan"))
          || (processing_this_field.equals("TAStatus")) ) {    // new 9/18

          xprint("\n");

        } else if ( (processing_this_field.equals("track")) ||
                    (processing_this_field.equals("flightPlan")) ||
                    (processing_this_field.equals("enhancedData")) ||
                    (processing_this_field.equals("mode"))) {   // try to ignore this!

            xprint("\n" );

            // new vals Map (to erase old values)
            sql_insert  = new HashMap< String, String> ();

            sql_insert.put( "src_airport", src_airport ); // everyone has one of these

            xml_tree = processing_this_field + ">";    // start building a new tree

        } else {
            // add this element to xml tree (if not 'message' or 'flight')
            xml_tree += processing_this_field + ">" ;

            xprint("xml_tree +==" + xml_tree ); //dbg:
        };
    }  // startElement

    // ------------------------------- value / contents

    public void characters(char ch[], int start, int length) throws SAXException {

        processing_this_value = new String(ch, start, length).trim();

        if (processing_this_field.equals("src")) {
                src_airport = processing_this_value;
        } else {

            // save tracknum for flight plan row
            if (processing_this_field.equals("trackNum")) {
                trackNum = processing_this_value;
            };

            xprint("    put: " + processing_this_field + ":"+processing_this_value );

            sql_insert.put( processing_this_field, processing_this_value );
        };
    };  // characters

    // -------------------------------

    public void endElement(String uri, String localName,
                String qName) throws SAXException
    {
        // remove "ns2:"
        if (qName.startsWith("ns2:")) { qName = qName.substring(4); };

        String qn = qName;  //.toLowerCase();
        String tablename = "unassigned";

        //q02:inside_element = "";

        // this one didn't participate in tree structure (should it have?)
        //q02: if (qName.equals("message")) { return ; }
        //q02: if (qName.equals("ns5:MessageCollection")) { return ; }
        //
        if (qName.equals("TATrackAndFlightPlan")) { return ; }
        if (qName.equals("TAStatus"))             { return ; }
        if (qName.equals("record"))               { return ; }

xprint("xml_tree qn remove:" + qn );
xprint("xml_tree b4 remove:" + xml_tree );

        xml_tree = xml_pop( xml_tree );

        xprint("xml_tree -==" + xml_tree ); //dbg:

        if ( (qName.equals("track"       )) ||
             (qName.equals("flightPlan"  )) ||
             (qName.equals("enhancedData")) ) {

//System.out.println("beginning insert:" + qName);

            //------------------------ filter begin
            String q02_filter = "PCT";    // todo: move this elsewhere...

//System.out.println("src_airport:" + sql_insert.get("src_airport") );

            if ( ! sql_insert.get("src_airport").equals( q02_filter ) ) {
                return;   // DISCARD this; uninteresting
            };

//System.out.println("enh:b");
            //------------------------ filter end

            if (qName.equals("track")) {   // special BAD hack for q02 trackdata
                tablename = "stdds";

                if ( (sql_insert.get("lat") == null) ||
                     (sql_insert.get("lon") == null)  )  {
                    return;   // DISCARD this; uninteresting
                };
                if ( (sql_insert.get("status")  == null) ||
                     (sql_insert.get("mrtTime") == null)  )  {
System.out.println("messed up: either status or mrttime are null");
                    return;   // DISCARD this; is messed up
                };

                String pt_str = make_point( sql_insert.get("lon"),
                                            sql_insert.get("lat") );

                sql_insert.put( "position", pt_str );

                System.out.println( String.format( "%s  %6s  %-9s  %-8s  %-8s",
                                sql_insert.get("src_airport"),
                                sql_insert.get("trackNum"),
                                sql_insert.get("status"),
                                sql_insert.get("lat"),
                                sql_insert.get("lon") ));

            } else {
//System.out.println("enh:c:" + qName);
                sql_insert.put( "trackNum", trackNum );  // saved from track msg
                //sql_insert.put( "trackNum", "8888" );  // saved from track msg
//System.out.println("enh:d:" + qName);

                switch (qName) {

                    case "flightPlan":
//System.out.println("enh:f");
                        tablename = "flightplan";
                        System.out.println( sql_insert.get("src_airport")+ " flightplan");
                        break;

                    case "enhancedData":
//System.out.println("enh:e");
                        tablename = "enhanced";
                        System.out.println( sql_insert.get("src_airport")+ " enhanced");
                        break;

                    default:
                        System.out.println("bad qName:" + qName);
                        System.exit(1);
                };
            };
//System.out.println( tablename );

            String sql = prepare_sql_statement( tablename, quote_these, sql_insert);

//System.out.println( sql );
            xprint( sql );

            actually_do_insert( sql );
        };
    } // endElement
}; // handler

// =========================================================================
// =================== xml:     enRoute
// =================== config:  CSSI.FDPS.Q01.OUT
// =================== postgis: fdps
// =========================================================================

public class q_f01_Handler extends DefaultHandler {

    Map< String, String > sql_insert  = null;

    String quote_these = "acid gufi_fdps gufi_hex dep_apt arr_apt "
        + "fltplan_id flt_status actype "
        + "msg_arrival dep_time arr_time ";

    String inside_element = "?";
    // ------------------------------- start

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {

          // attributes are only on the start tag, make a dictionary
  // (associative array?) of them for later use
          Map< String, String > attrs = new HashMap< String, String> ();

          processing_this_field = qName;  //.toLowerCase() ;
          xprint("start element>" + processing_this_field );

          // ------------------ collect attributes into map / dictionary

          try {
              if (attributes.getLength() > 0) {
                  for (int k = 0 ; k < attributes.getLength() ; k++ ) {

                    //xprint("  attr$" + attributes.getQName(k) +
                    //                      "!" + attributes.getType(k)  +
                    //                      ">" + attributes.getValue(k) );

                    attrs.put( attributes.getQName(k), attributes.getValue(k) ) ;
                  };
              };

         } catch (Exception e) {
             System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
             e.printStackTrace();
             System.err.println( "Attributes problem"  );
             System.err.println( e.getClass().getName()+"::" + e.getMessage() );
             System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
             System.exit(1);
         }

         // ------------------ end of start attributes

          // print nicely
          if (processing_this_field == "message") {
              xprint("\n" );
          } else if (processing_this_field == "flight") {
              xprint("\n" );
              xprint("    --------- " + processing_this_field);

              // ---------- start a new flight element

              // new vals Map (to erase old values)
              sql_insert  = new HashMap< String, String> ();

              sql_insert.put( "msg_arrival", attrs.get( "timestamp" ) );

              xml_tree = "flight>";    // start building a new tree

          } else {
              // add this element to xml tree (if not 'message' or 'flight')
              xml_tree += processing_this_field + ">" ;
              xprint("xml_tree +==" + xml_tree );
          };

          // estimated and actual times are buried inside these
          switch (processing_this_field) {
              case "arrival":
                  xprint("    arr apt>" + attrs.get( "arrivalPoint" ) );
                  sql_insert.put( "arr_apt", attrs.get( "arrivalPoint" ) );
                  inside_element = "arrival";
                  break;

              case "departure":
                  xprint("    dep apt>" + attrs.get( "departurePoint" ));
                  sql_insert.put( "dep_apt", attrs.get( "departurePoint" ) );
                  inside_element = "departure";
                  break;

              case "estimated": if (inside_element == "arrival") {
                    xprint("    arr_time>" + attrs.get( "time" ));
                    sql_insert.put( "arr_time", attrs.get( "time" ) );
                    }
                  break;

              case "actual": if (inside_element == "departure") {
                    xprint("    dep_time>" + attrs.get( "time" ));
                    sql_insert.put( "dep_time", attrs.get( "time" ) );
                    }
                  break;

              case "flightIdentification":
              case "flightidentification":
                  xprint("dbg: ptf=" + processing_this_field );
                  xprint("dbg: qName=" + qName );
                  xprint("    acid>"+attrs.get("aircraftIdentification"));
                  sql_insert.put( "acid", attrs.get("aircraftIdentification"));
                  break;

              case "flightPlan":
              case "flightplan":
                  xprint("    fltplan_id>" + attrs.get( "identifier" ));
                  sql_insert.put( "fltplan_id", attrs.get("identifier"));
                  break;

              case "flightStatus":
              case "flightstatus":
                  xprint("    flt_status>"+attrs.get("fdpsFlightStatus"));
                  sql_insert.put( "flt_status", attrs.get("fdpsFlightStatus"));
                  break;

              case "nameValue":
              case "namevalue":
                  if ( attrs.get("name").equals("FDPS_GUFI") ) {
                      xprint("    fdps_gufi>" + attrs.get( "value" ));
                      sql_insert.put( "gufi_fdps", attrs.get("value"));
                  };
                  break;

              // just tag these, data is in characters portion
              case "position":       inside_element = "position";  break;  // UNUSED
              case "location":       inside_element = "location";  break;  // UNUSED
              case "altitude":       inside_element = "altitude";  break;  // used
              case "gufi":           inside_element = "gufi";      break;  // used
              case "targetposition": inside_element = "tgtpos";    break;  // UNUSED
              case "trackVelocity":  inside_element = "trkvel";    break;  // UNUSED
              case "x":              inside_element = "trkvel_x";  break;  // used
              case "y":              inside_element = "trkvel_y";  break;  // used
              case "surveillance":   inside_element = "surveillance";  break;  // regex
              case "icaoModelIdentifier": inside_element="icaoModelIdentifier"; break; // used

              case "pos":  if (inside_element == "location") {     // TODO: regex on tree
                               inside_element =  "location_pos" ;
                           }
                           break;

              default:  // ignore
                  xprint("nope>" + processing_this_field);
          };  // switch
       // ------------------------------- value / contents
    };  // startElement

    // ---------------------------------------------------------------------
    //
    public void characters(char ch[], int start, int length) throws SAXException {

        processing_this_value = new String(ch, start, length).trim();

          switch (inside_element) {
                case "location_pos":    // TODO: regex
                xprint("    position>" + processing_this_value);
                // Q: is lat / long in the correct order????
                try {
                    String[] lnglat = processing_this_value.split(" ");

                    sql_insert.put("lat", lnglat[0] );
                    sql_insert.put("lng", lnglat[1] );

                    String pt = make_point( lnglat[1],  lnglat[0] );
                    sql_insert.put("position", pt );

                } catch (Exception e) {
                    // saw once: position>35.077778-92.939167
                    System.out.println("    position> BAD FORMAT f01" );
                }
                break;

              case "altitude":
                xprint("    altitude>" + processing_this_value);
                sql_insert.put( "altitude", processing_this_value );
                break;

              case "surveillance":  // possible speed value here...
                // consider this:
                // the 'surveillance' tag is used in multiple places
                // the airspeed should be (the last one) under this:
                //

// good: enroute > position > actualspeed > surveillance >
// bad: aircraftdescription > capabilities > surveillance >

                // so, to guard against that, here is a regex to get the right one:
                // note: matcher does not begin at start of string
                String pattern = "enRoute.*actualSpeed";

                // Create a Pattern object
                Pattern r = Pattern.compile(pattern);

                // Now create matcher object.
                Matcher m = r.matcher(xml_tree);

                if (m.find( )) {
                    xprint("    actualspeed>" + processing_this_value);
                    sql_insert.put( "actualspeed", processing_this_value );
                } else {
                    xprint("mtc:pattern is NOT in xml_tree:" + xml_tree);
                };
                break;

              case "trkvel_x":
                xprint("    trkvel_x>" + processing_this_value);
                sql_insert.put( "trkvel_x", processing_this_value );
                break;

              case "trkvel_y":
                xprint("    trkvel_y>" + processing_this_value);
                sql_insert.put( "trkvel_y", processing_this_value );
                break;

              case "icaoModelIdentifier":
                xprint("    actype>" + processing_this_value);
                sql_insert.put( "actype", processing_this_value );
                break;

              case "gufi":  // hex one (not FDPS one)
                xprint("    gufi>" + processing_this_value);
                sql_insert.put( "gufi_hex", processing_this_value );
                break;

              default:
                xprint("unused element :" + processing_this_value);
          }
    };  // characters

    // -------------------------------

    public void endElement(String uri, String localName,
                String qName) throws SAXException
    {
        String qn = qName;  //.toLowerCase();

        inside_element = "";

        // this one didn't participate in tree structure (should it have?)
        if (qName.equals("message")) { return ; }
        if (qName.equals("ns5:MessageCollection")) { return ; }

//System.out.println("xml_tree qn remove:" + qn );
//System.out.println("xml_tree b4 remove:" + xml_tree );

        xml_tree = xml_pop( xml_tree );

        xprint("xml_tree -==" + xml_tree );

        // HELP if (qName.equals("positionReport"))
        if (qName.equals("flight")) {

            xprint("---------- sql insert" );
            //xprint( values );

            System.out.println( String.format( "%s  %-9s  %8s  %8s",
                                sql_insert.get("msg_arrival"),
                                sql_insert.get("acid"),
                                sql_insert.get("lat"),
                                sql_insert.get("lng") ));

            // ----------------------------------------

            String sql = prepare_sql_statement( "fdps", quote_these, sql_insert);

            xprint( sql );

            actually_do_insert( sql );
        };
    };
}; // handler


// =========================================================================
// =================== xml:     runwayData
// =================== config:  CSSI.STDDS.Q01.OUT
// =================== postgis: rvr
// =========================================================================

public class q01_Handler extends DefaultHandler {

// WARNING: this one was the very FIRST attempt at xml parsing
// (it was successful), but the above routines are MUCH cleaner than this one

    Map< String, String > sql_insert  = null;

      //  the OLD way of forming sql insert statements...
      //  (because I found a web page that showed how to use the java SAX
      //  xml parser, but it was an AWFUL, STUPID, BRAIN DEAD way of parsing)

      // see next nested if stmts for tag
      boolean apt     = false;
      boolean rwyid   = false;
      boolean rwysid  = false;
      boolean tdvr    = false;
      boolean tdt     = false;
      boolean rwyedge = false;
      boolean rwyctr  = false;
      String v_apt     = "n/a"; // when present, will be set to ""
      String v_rwyid   = "n/a";
      String v_rwysid  = "n/a";
      String v_tdvr    = "n/a";
      String v_tdt     = "n/a";
      String v_rwyedge = "n/a";
      String v_rwyctr  = "n/a";

    // ------------------------------- start

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {

          //wt: System.out.println("Start Element :" + qName);

          if (qName.equalsIgnoreCase("airport"))                { apt     = true; }
          if (qName.equalsIgnoreCase("runwayid"))               { rwyid   = true; }
          if (qName.equalsIgnoreCase("runwaysubid"))            { rwysid  = true; }
          if (qName.equalsIgnoreCase("touchdownvisualrange"))   { tdvr    = true; }
          if (qName.equalsIgnoreCase("touchdowntrend"))         { tdt     = true; }
          if (qName.equalsIgnoreCase("runwayedgelightsetting")) { rwyedge = true; }
          if (qName.equalsIgnoreCase(
                             "runwaycenterlinelightsetting"))   { rwyctr = true; }
    }  // startElement

    // -------------------------------

    public void characters(char ch[], int start, int length) throws SAXException {

        String val = new String(ch, start, length);

        if (apt)     { v_apt     = val.trim(); apt     = false; }
        if (rwyid)   { v_rwyid   = val.trim(); rwyid   = false; }
        if (rwysid)  { v_rwysid  = val.trim(); rwysid  = false; }
        if (tdvr)    { v_tdvr    = val.trim(); tdvr    = false; }
        if (tdt)     { v_tdt     = val.trim(); tdt     = false; }
        if (rwyedge) { v_rwyedge = val.trim(); rwyedge = false; }
        if (rwyctr)  { v_rwyctr  = val.trim(); rwyctr  = false; }

    };  // characters

    // -------------------------------

    public void endElement(String uri, String localName,
                String qName) throws SAXException
    {
        String qn = qName;  //.toLowerCase();

        Statement pgstmt = null;

        // System.out.println("End Element :" + qName);
        if (qName.equals("runwayData")) {
            //System.out.println( "------ new record:"   );
            //System.out.println( "apt    :" + v_apt.trim()    + "<" );
            //System.out.println( "rwyid  :" + v_rwyid + v_rwysid.trim() + "<"  );
            //System.out.println( "rwysid :" + v_rwysid  );
            //System.out.println( "tdvr   :" + v_tdvr.trim()    + "<");
            //System.out.println( "tdt    :" + v_tdt.trim()     + "<");
            //System.out.println( "rwyedge:" + v_rwyedge.trim() + "<");
            //System.out.println( "rwyctr :" + v_rwyctr.trim()  + "<");

            //System.out.println( "------ new sql:"   );

//----------------------------------------

String sql = "INSERT INTO rvr( "
    + "airport, "
    + "runway, "
    + "rvrtime, "
    + "touchdownvisualrange, "
    + "touchdowntrend, "
    //+ "midpointvisualrange, "    TODO: add these to xml parsing
    //+ "midpointtrend, "
    //+ "rolloutvisualrange, "
    //+ "rollouttrend, "
    + "runwayedgelightsetting, "
    + "runwaycenterlinelightsetting) ";

String values = "VALUES( "
        + "'" + v_apt                      + "', "
        + "'" + v_rwyid + v_rwysid         + "', "
        + " now(), "
        + "'" + v_tdvr                     + "', "
        + "'" + v_tdt                      + "', "
        + "'" + v_rwyedge                  + "', "
        + "'" + v_rwyctr                   + "'); ";

            System.out.println( values  );

            sql += values;

            try {
                pgstmt = pgconn.createStatement();
                pgstmt.executeUpdate(sql);
                pgstmt.close();
                pgconn.commit();
            } catch (Exception e) {
                System.err.println( e.getClass().getName()+"::" + e.getMessage() );
                System.exit(0);
            }
        //System.out.println( "Record inserted successfully!!!" );

    };  // qName = </runwayData>
  };  // endElement
}; // handler

// =========================================================================
// =========================================================================
// =========================================================================
// =========================================================================
// =========================================================================

    public String prepare_sql_statement( String tablename,
                 String quote_these,
                 Map< String, String > sql_insert ) {

        xprint("---------- sql insert" );

        // ------------------- build INSERT sql statement

        Set<String>      valCodes = sql_insert.keySet();
        Iterator<String> iterator = valCodes.iterator();

        String comma = "";   // put comma _between_ all fields
        String sq;           // bout single quotes around text/varchar fields

        insert = "INSERT INTO " + tablename + " ( " ;
        values = " VALUES( ";

        // finally, we are ready to actually start to build the INSERT statement

        while ( iterator.hasNext()) {
            String code  = iterator.next();
            String value = sql_insert.get(code);

            if (quote_these.contains(code)) { sq = "'"; } else { sq = ""; };

            insert += comma + code ;
            values += comma + sq + value + sq ;
            comma = ", ";
        };

        // and the full INSERT statement is...
        insert += ") \n";
        values += ");";
        String sql = insert + values ;

        return sql;
    };

    //=============================================================================

    // TODO: make this success/fail instead of void...

    public void actually_do_insert( String sql ) {

        // ----------------------------------------
        Statement pgstmt = null;
        boolean successful_insert = true;  // DEBUG value

        /* ************************************ */
        try {
            // actually do the insert
            pgstmt = pgconn.createStatement();
            pgstmt.executeUpdate(sql);
            pgstmt.close();
            pgconn.commit();
            successful_insert = true ;
        } catch (Exception e) {
            System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
            System.err.println( "NOT insert into whatever table ..."  );
            System.err.println( e.getClass().getName()+"::" + e.getMessage() );
            System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
            successful_insert = false ;
            System.exit(1);     // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        }
        /* ************************************ */

// PSQLException::ERROR: current transaction is aborted,
// commands ignored until end of transaction block

// https://laurenthinoul.com/how-to-fix-postgres-error-current-transaction-
// is-aborted-commands-ignored-until-end-of-transaction-block/

        if (successful_insert == false) {
            try {
                pgstmt = pgconn.createStatement();
                pgstmt.executeUpdate("rollback;");
                pgstmt.close();
                pgconn.commit();
            } catch (Exception e) {
                System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
                System.err.println( "bad rollback"  );
                System.err.println( e.getClass().getName()+"::" + e.getMessage() );
                System.err.println( "+++++++++++++++++++++++++++++++++++++++++"  );
                System.exit(1);      // stop on error...
            }
        }
    };

    // end of cssi part
    //=============================================================================

}  // class CssiConsumer

