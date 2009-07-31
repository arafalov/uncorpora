package org.uncorpora.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.XMLReader;

/**
 * Processor:
 * 1) Extract specific languages
 * 2) Delete footnotes
 * 3) Delete, Replace or Inline symbols
 * 4) Delete vote segments
 * 5) Extract specific sessions
 * @author arafalov
 */
public class Processor
{

    private static final QName QNAME_TU = new QName("tu");

    private void run() throws XMLStreamException, UnsupportedEncodingException, FileNotFoundException
    {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        final XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(new BufferedReader(new InputStreamReader(new FileInputStream(inFile), CHARSET_UTF8), BUFFER_SIZE));

        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        final XMLEventWriter eventWriter = xmlOutputFactory.createXMLEventWriter(new BufferedWriter(new OutputStreamWriter((outFile==null?System.out:new FileOutputStream(outFile)), CHARSET_UTF8), BUFFER_SIZE));

        //To skip vote, we need to hold TU until we see PROP/@type='vote' or first TUV
        //To extract specific session, hold TU until we see PROP/@type='session' and its text or first TUV
        //To skip language, look for TUV/@xml:lang
        //To remove footnote, look for SUB/@type='fnote'
        //To inline symbol, look for HI/@type='symbol' and then let only text through

        ArrayDeque<XMLEvent> eventQueue = new ArrayDeque<XMLEvent>(20);


        boolean holdTU = false;

        while (eventReader.hasNext())
        {
            XMLEvent event = eventReader.nextEvent();
            eventQueue.add(event);
            if (event instanceof StartElement)
            {
                if (holdTU)
                if (QNAME_TU.equals(event.asStartElement().getName()))
                {
                    holdTU=true;
                }
            }
        }


    }


    public static void main(String[] args)
    {

        Processor processor = new Processor();
        CmdLineParser cmdLineParser = new CmdLineParser(processor);

        try
        {
            cmdLineParser.parseArgument(args);
        } catch (CmdLineException e)
        {
            System.err.println(e.getMessage());
            System.err.println("\nUsage: java -jar uncorpora.jar [options...] arguments...");
            cmdLineParser.printUsage(System.err);
            return;
        }


        System.out.println("START: " + (new Date()));
        try
        {
            processor.run();
        } catch (Exception ex)
        {
            Logger.getLogger(Processor.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("END  : " + (new Date()));

    }

    private TreeSet splitEnum(String valueList, TreeSet validChoices, String name)
            throws CmdLineException
    {
        TreeSet result = new TreeSet();
        for (String val : valueList.split(","))
        {
            val = val.trim().toUpperCase();
            if (!validChoices.contains(val))
            {
                throw new CmdLineException(String.format("Not a valid %s choice: %s", name, val));
            } else
            {
                result.add(val);
            }
        }
        return result;
    }
    private static final TreeSet VALID_LANGS = new TreeSet(Arrays.asList("EN", "FR", "ES", "AR", "ZH", "RU"));
    private TreeSet langs;

    @Option(name = "-langs",
    metaVar = "<langlist>",
    usage = "list of language codes to keep, coma-separated.\nValid choice: EN,FR,ES,AR,ZH,RU")
    private void setLangs(String langsList) throws CmdLineException
    {
        langs = splitEnum(langsList, VALID_LANGS, "language");
    }

    @Option(name = "-novote", usage = "Remove paragraphs that contain voting information")
    private boolean noVote = false;

    @Option(name="-plaintext", usage="Remove footnotes, flatten symbols and leads, so each paragraph contains only text")
    private boolean plaintext = false;

    private static final TreeSet VALID_SESSIONS = new TreeSet(Arrays.asList("55", "56", "57", "58", "59", "60", "61", "62"));
    private TreeSet sessions;

    @Option(name = "-sessions",
        metaVar = "<sessionList>",
        usage = "list of session numbers to keep, coma-separated.\nValid choices: 55,56..62")
    private void setSessions(String sessionList) throws CmdLineException
    {
        sessions = splitEnum(sessionList, VALID_SESSIONS, "session");
    }

//    @Option(name = "-nofnote", usage = "Remove inlined footnotes")
//    private boolean noFnote = false;

    @Option(name = "-output", usage = "File to write results to.\nBy default results go to standard out")
    private File outFile;

    @Argument()
    private File inFile;


    private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
    private static final int BUFFER_SIZE = 10 ^ 6;

}
