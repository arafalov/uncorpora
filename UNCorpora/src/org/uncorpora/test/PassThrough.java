package org.uncorpora.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Date;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Test streaming API and whether it can handle full sized file.
 */
public class PassThrough
{

    private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
    private static final int BUFFER_SIZE = 10 ^ 6;

    public static void main(String[] args) throws XMLStreamException, FileNotFoundException
    {
        if (args.length < 2)
        {
            System.err.println("Missing input, output files");
            System.exit(1);
        }

        final String fileIn = args[0];
        final String fileOut = args[1];

        System.out.println("START! " + (new Date()));
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        final XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(new BufferedReader(new InputStreamReader(new FileInputStream(fileIn), CHARSET_UTF8), BUFFER_SIZE));

        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        final XMLEventWriter eventWriter = xmlOutputFactory.createXMLEventWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOut), CHARSET_UTF8), BUFFER_SIZE));

        ArrayDeque<XMLEvent> eventQueue = new ArrayDeque<XMLEvent>(20);
        while (eventReader.hasNext())
        {
            XMLEvent event = eventReader.nextEvent();
            eventQueue.add(event);
            if (event instanceof StartElement)
            {
                StartElement startElement = (StartElement) event;
                QName startElementName = startElement.getName();
                if (QNAME_TU.equals(startElementName))
                {
                    checkForVote(eventQueue, eventReader);
                }
                else if (QNAME_HI.equals(startElementName))
                {
                    inline(eventQueue, eventReader, QNAME_HI);
                }
            }
            while ((event = eventQueue.poll()) != null)
            {
                eventWriter.add(event);
            }
        }

        eventReader.close();
        eventWriter.close();
        System.out.println("DONE! " + (new Date()));
    }

    private static final QName QNAME_TYPE = new QName("type");
    private static final QName QNAME_TU = new QName("tu");
    private static final QName QNAME_HI = new QName("hi");

    private static void checkForVote(ArrayDeque<XMLEvent> eventQueue, XMLEventReader eventReader) throws XMLStreamException
    {
        boolean clearAndSkip = false;
        while (eventReader.hasNext())
        {
            XMLEvent event = eventReader.nextEvent();
            eventQueue.add(event);
            if (!(event instanceof StartElement))
            {
                continue;
            }

            StartElement startElement = (StartElement) event;
            final String elementName = startElement.getName().getLocalPart();
            if ("tuv".equals(elementName))
            {
                break; //did not find vote. Just emit the queue and continue;
            }

            if (!("prop".equals(elementName)))
            {
                continue;
            }

            final String typeValue = startElement.getAttributeByName(QNAME_TYPE).getValue();
            if ("vote".equals(typeValue))
            {
                //we do not want TUs that contain vote
                //delete collected and skip until the end of TU
                clearAndSkip = true;
                break;
            }
        }

        if (clearAndSkip)
        {
            eventQueue.clear();
            do
            {
                XMLEvent event = eventReader.nextEvent();
                if (!(event instanceof EndElement))
                {
                    continue; //keep looking
                }
                if (QNAME_TU.equals(((EndElement) event).getName()))
                {
                    break;
                }
            } while (true);
        }
    }

    private static void inline(ArrayDeque<XMLEvent> eventQueue, XMLEventReader eventReader, QName qnameEnd) throws XMLStreamException
    {
        eventQueue.clear(); //remove parent element's start
        XMLEvent event;
        do
        {
            event = eventReader.nextEvent();
            if (event instanceof Characters)
            {
                eventQueue.add(event);
                continue;
            }

            if (!(event instanceof EndElement))
            {
                continue;
            }

            if (qnameEnd.equals(event.asEndElement().getName()))
            {
                break;
                
            }
        } while (true);
    }
}
