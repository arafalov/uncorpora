package org.uncorpora.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

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
    private static final QName QNAME_TUV = new QName("tuv");
    private static final QName QNAME_PROP = new QName("prop");
    private static final QName QNAME_SUB = new QName("sub");

    private static final QName QNAME_LANG = new QName(XMLConstants.XML_NS_URI, "lang");
    private static final QName QNAME_TYPE = new QName("type");
    private static final QName QNAME_HI = new QName("hi");

    /**
     * Collect events into a list until the end of until start of TU element is met.
     * @param eventReader - source of events
     * @param events - list to collect events into
     * @return Saw
     * @throws XMLStreamException
     */
    private boolean collectUntilTU(final XMLEventReader eventReader, LinkedList<XMLEvent> events, boolean collectTU) throws XMLStreamException, XMLStreamException
    {
        while (eventReader.hasNext())
        {
            XMLEvent nextEvent = eventReader.peek();
            boolean done = false;

            if (  (nextEvent instanceof StartElement && QNAME_TU.equals(nextEvent.asStartElement().getName()))
                ||(nextEvent instanceof EndElement && QNAME_TU.equals(nextEvent.asEndElement().getName())))
            {
                if (collectTU)
                {
                    events.add(eventReader.nextEvent());
                }
                return true;
            }

            //not a TU element
            events.add(eventReader.nextEvent());
        }

        //hit the end of the stream before seeing the start of TU
        return false;
    }


    private void run() throws XMLStreamException, UnsupportedEncodingException, FileNotFoundException, IOException
    {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        BufferedReader fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), CHARSET_UTF8), BUFFER_SIZE);
        final XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(fileReader);

        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outFile == null ? System.out : new FileOutputStream(outFile), CHARSET_UTF8), BUFFER_SIZE);
        final XMLEventWriter eventWriter = xmlOutputFactory.createXMLEventWriter(writer);

        LinkedList<XMLEvent> buffer = new LinkedList<XMLEvent>();

        //copy the head
        boolean tuSeen = collectUntilTU(eventReader, buffer, false);
        copyOutEvents(eventWriter, buffer);


        while (tuSeen)
        {
            tuSeen = collectUntilTU(eventReader, buffer, true); //got TU Start
            assert tuSeen; //has to be true, as we just checked we have seen it
            tuSeen = collectUntilTU(eventReader, buffer, true); //to TU End
            assert tuSeen; //will fail only if tu does not close or we have a bug in matching

            rewriteTU(buffer);
            copyOutEvents(eventWriter, buffer); // copy out rewritten TU

            tuSeen = collectUntilTU(eventReader, buffer, false); //collect whatever is between TUs
            trimWhiteSpaces(buffer, 0);
            copyOutEvents(eventWriter, buffer);
        }

        // just in case we haven't seen any TUs at all, do the final cleanup
        tuSeen = collectUntilTU(eventReader, buffer, false);
        assert !tuSeen; //if we see a TU now, something is very wrong

        eventWriter.flush();
        writer.close();
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
            System.err.println("\nUsage: java -jar uncorpora.jar [options...] <inputFile>");
            cmdLineParser.printUsage(System.err);
            return;
        }

        //To skip language, look for TUV/@xml:lang
        //To skip vote, we need to hold TU until we see PROP/@type='vote' or first TUV
        //To remove footnote, look for SUB/@type='fnote'
        //To inline symbol, look for HI/@type='symbol' and then let only text through
        //To extract specific session, hold TU until we see PROP/@type='session' and its text

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

    private TreeSet<String> splitEnum(String valueList, TreeSet<String> validChoices, String name)
            throws CmdLineException
    {
        TreeSet<String> result = new TreeSet<String>();
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
    private static final TreeSet<String> VALID_LANGS = new TreeSet<String>(Arrays.asList("EN", "FR", "ES", "AR", "ZH", "RU"));

    /**
     * Flag: List of languages to keep
     */
    private TreeSet<String> keptLangs = VALID_LANGS;

    @Option(name = "-langs",
    metaVar = "<langlist>",
    usage = "list of language codes to keep, coma-separated.\nValid choice: EN,FR,ES,AR,ZH,RU")
    private void setLangs(String langsList) throws CmdLineException
    {
        keptLangs = splitEnum(langsList, VALID_LANGS, "language");
    }

    /**
     * Flag: Remove vote paragraphs
     */
    @Option(name = "-novote", usage = "Remove paragraphs that contain voting information")
    private boolean noVote = false;

    /**
     * Flag: remove all non-text markers from the TUVs. For footnotes, remove content as well.
     */
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

    private void copyOutEvents(XMLEventWriter eventWriter, LinkedList<XMLEvent> buffer) throws XMLStreamException
    {
        int size = buffer.size();
        for (int i=0; i<size; i++)
        {
            eventWriter.add(buffer.removeFirst());
        }
    }

    private void rewriteTU(LinkedList<XMLEvent> buffer)
    {
        if (this.noVote && foundVote(buffer))
        {
            buffer.clear();
        }

        for (String lang: VALID_LANGS)
        {
            if (!keptLangs.contains(lang))
            {
//                System.out.println("Removing lang: " + lang);
                removeElements(buffer, QNAME_TUV, QNAME_LANG, lang, 1, buffer.size()-1);
            }
        }
        if (this.plaintext)
        {
            removeElements(buffer, QNAME_SUB, null, null, 1, buffer.size()-1);
            flattenElements(buffer, QNAME_HI, 1, buffer.size()-1);
        }
    }

    private boolean foundVote(LinkedList<XMLEvent> buffer)
    {
        for (int i=0; i<buffer.size(); i++)
        {
            XMLEvent event = buffer.get(i);
            if (!(event instanceof StartElement))
            {
                continue;
            }

            StartElement startElement = event.asStartElement();
            QName name = startElement.getName();

            if (QNAME_PROP.equals(name))
            {
                Attribute attribType = startElement.getAttributeByName(QNAME_TYPE);
                if (attribType != null && "vote".equals(attribType.getValue())) //any value, as long as it is present
                {
                    return true;
                }
                //else keep looking
            }
            else if (QNAME_TUV.equals(name))
            {
                return false; // we hit start of TUV, no more properties to be found in this TU
            }
        }
        throw new IllegalArgumentException("Looking for vote did not found either vote or start of TUV");
    }

    /**
     * Remove any elements with their content
     * @param buffer list of events
     * @param elementName name of the container element to remove with its content
     * @param propName name of the property to check for presense of (NULL - do not care)
     * @param propValue value of the property
     * @param rangeStart index to start processing buffer from, inclusive
     * @param rangeEnd index to finish processing buffer at, exclusive. Assumes no elements crossing the rangeEnd
     */
    private void removeElements(LinkedList<XMLEvent> buffer, QName removeElementName, QName attribName, String attribValue, int rangeStart, int rangeEnd)
    {
        for (int i=rangeStart; i<rangeEnd; i++)
        {
            XMLEvent event = buffer.get(i);
            if (!(event instanceof StartElement))
            {
                continue;
            }

            StartElement startElement = event.asStartElement();
            if (!removeElementName.equals(startElement.getName()))
            {
                continue;
            }

            if (attribName != null) //if attribute is NULL, proceed to deletion
            {
// Show what attributes we have with full QNames
//                System.out.println("Enumerating attributes for: " + startElement);
//                Iterator atts = startElement.getAttributes();
//                while (atts.hasNext())
//                {
//                    Attribute att = (Attribute)atts.next();
//                    QName attName = att.getName();
//                    System.out.printf("%s(%s)::%s=%s\n", attName.getPrefix(), attName.getNamespaceURI(), attName.getLocalPart(), att.getValue());
//                }

                Attribute attrib = startElement.getAttributeByName(attribName);
                if (attrib != null && !attribValue.equals(attrib.getValue()))
                {
                    //we found the attribute, but it not the one we want to delete
                    continue;
                }
            }

            //we hit element start. Delete until we hit the end of the same element. Assume not-nested
            buffer.remove(i);
            int tokensRemoved = 1;
            do
            {
                event = buffer.get(i);
                tokensRemoved++;
                buffer.remove(i);
            } while (!((event instanceof EndElement) && removeElementName.equals(event.asEndElement().getName())));

            rangeEnd -= tokensRemoved;
            rangeEnd -= trimWhiteSpaces(buffer, i);
            i--; //so that the next cycle starts again at the 'new' next position
        }
    }


     /**
     * Remove given element's start/end elements, but leave content in place
     * @param buffer list of events
     * @param elementName name of the container element to remove
     * @param rangeStart index to start processing buffer from, inclusive
     * @param rangeEnd index to finish processing buffer at, exclusive. Assumes no elements crossing the rangeEnd
     */
    private void flattenElements(LinkedList<XMLEvent> buffer, QName flattenElementName, int rangeStart, int rangeEnd)
    {
        int i=rangeStart;
        while (i<rangeEnd)
        {
            XMLEvent event = buffer.get(i);
            if (  (event instanceof StartElement && flattenElementName.equals(event.asStartElement().getName()))
                ||(event instanceof EndElement && flattenElementName.equals(event.asEndElement().getName())))
            {
                buffer.remove(i);
                rangeEnd--;
            }
            else
            {
                i++;
            }
        }

    }

    private int trimWhiteSpaces(LinkedList<XMLEvent> buffer, int startIdx)
    {
        if (startIdx >= buffer.size())
        {
            return 0;
        }

        int count = 0;
        XMLEvent firstEvent = buffer.get(startIdx);
        while ((firstEvent instanceof Characters) && firstEvent.asCharacters().isWhiteSpace())
        {
            count++;
            buffer.remove(startIdx);
            if (startIdx == buffer.size()) // we got them all
            {
                break;
            }
            firstEvent = buffer.get(startIdx);
        }
        return count;
    }



}