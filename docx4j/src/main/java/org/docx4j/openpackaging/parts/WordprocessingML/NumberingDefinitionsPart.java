/*
 *  Copyright 2007-2008, Plutext Pty Ltd.
 *   
 *  This file is part of docx4j.

    docx4j is licensed under the Apache License, Version 2.0 (the "License"); 
    you may not use this file except in compliance with the License. 

    You may obtain a copy of the License at 

        http://www.apache.org/licenses/LICENSE-2.0 

    Unless required by applicable law or agreed to in writing, software 
    distributed under the License is distributed on an "AS IS" BASIS, 
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
    See the License for the specific language governing permissions and 
    limitations under the License.

 */

package org.docx4j.openpackaging.parts.WordprocessingML;


import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBResult;
import javax.xml.transform.Templates;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.jaxb.JaxbValidationEventHandler;
import org.docx4j.model.PropertyResolver;
import org.docx4j.model.listnumbering.AbstractListNumberingDefinition;
import org.docx4j.model.listnumbering.Emulator;
import org.docx4j.model.listnumbering.ListLevel;
import org.docx4j.model.listnumbering.ListNumberingDefinition;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.exceptions.InvalidOperationException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.JaxbXmlPart;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.wml.Lvl;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.Numbering.Num;
import org.docx4j.wml.Numbering.Num.AbstractNumId;
import org.docx4j.wml.Numbering.Num.LvlOverride;
import org.docx4j.wml.Numbering.Num.LvlOverride.StartOverride;
import org.docx4j.wml.PPrBase.Ind;
import org.docx4j.wml.PPrBase.NumPr;
import org.docx4j.wml.PPrBase.NumPr.Ilvl;
import org.docx4j.wml.PPrBase.NumPr.NumId;



public final class NumberingDefinitionsPart extends JaxbXmlPart<Numbering> {
	
	public NumberingDefinitionsPart(PartName partName) throws InvalidFormatException {
		super(partName);
		init();
	}
	
	public NumberingDefinitionsPart() throws InvalidFormatException {
		super(new PartName("/word/numbering.xml"));
		init();
	}

	public void init() {	
		// Used if this Part is added to [Content_Types].xml 
		setContentType(new  org.docx4j.openpackaging.contenttype.ContentType( 
				org.docx4j.openpackaging.contenttype.ContentTypes.WORDPROCESSINGML_NUMBERING));

		// Used when this Part is added to a rels 
		setRelationshipType(Namespaces.NUMBERING);
	}
	
    HashMap<String, AbstractListNumberingDefinition> abstractListDefinitions; 
	public HashMap<String, AbstractListNumberingDefinition> getAbstractListDefinitions() {
		if (abstractListDefinitions==null) initialiseMaps();
		return abstractListDefinitions;
	}

    HashMap<String, ListNumberingDefinition> instanceListDefinitions; 
	public HashMap<String, ListNumberingDefinition> getInstanceListDefinitions() {
		
		if (instanceListDefinitions==null) initialiseMaps();
		
		return instanceListDefinitions;
	}
	
    public void initialiseMaps()
    {
    	Numbering numbering = jaxbElement;
    	
        // count the number of different list numbering schemes
    	if (numbering.getNum().size() == 0)
        {
    		log.debug("No num defined");
            return;
        }
        
        // initialize the abstract number list
        abstractListDefinitions 
        	= new HashMap<String, AbstractListNumberingDefinition>(numbering.getAbstractNum().size() );
                
        // initialize the instance number list
        instanceListDefinitions 
        	= new HashMap<String, ListNumberingDefinition>( numbering.getNum().size() );

        // store the abstract list type definitions
        for (Numbering.AbstractNum abstractNumNode : numbering.getAbstractNum() )
        {
            AbstractListNumberingDefinition absNumDef 
            	= new AbstractListNumberingDefinition(abstractNumNode);

            abstractListDefinitions.put(absNumDef.getID(), absNumDef);

            // now go through the abstract list definitions and update those that are linked to other styles
            if (absNumDef.hasLinkedStyle() )
            {
//                String linkStyleXPath = "/w:document/w:numbering/w:abstractNum/w:styleLink[@w:val=\"" + absNumDef.Value.LinkedStyleId + "\"]";
//                XmlNode linkedStyleNode = mainDoc.SelectSingleNode(linkStyleXPath, nsm);
//
//                if (linkedStyleNode != null)
//                {
//                    absNumDef.Value.UpdateDefinitionFromLinkedStyle(linkedStyleNode.ParentNode, nsm);
//                }
                
                // find the linked style
                // TODO - review
                absNumDef.UpdateDefinitionFromLinkedStyle(abstractNumNode);
            }
        }

        // instantiate the list number definitions
        //foreach (XmlNode numNode in numberNodes)
        for( Numbering.Num numNode : numbering.getNum() )
        {
            ListNumberingDefinition listDef 
            	= new ListNumberingDefinition(numNode, abstractListDefinitions);

            instanceListDefinitions.put(listDef.getListNumberId(), listDef);
            log.debug("Added list: " + listDef.getListNumberId() );
        }

    }
    
    /**
     * For the given list numId, restart the numbering on the specified
     * level at value val.  This is done by creating a new list (ie <w:num>)
     * which uses the existing w:abstractNum.
     * @param numId
     * @param ilvl
     * @param val
     * @return 
     */
    public long restart(long numId, long ilvl, long val) 
    	throws InvalidOperationException {
    	
    	// Find the abstractNumId
    	
    	// (Ensure maps are initialised)
    	if (em == null ) { 
    		getEmulator();
    	}
    	ListNumberingDefinition existingLnd = instanceListDefinitions.get( Long.toString(numId) );
    	if (existingLnd==null) {
    		throw new InvalidOperationException("List " + numId + " does not exist");
    	}
    	BigInteger abstractNumIdVal = existingLnd.getNumNode().getAbstractNumId().getVal();
    	
    	// Generate the new <w:num
    	long newNumId = instanceListDefinitions.size() + 1;
    	
		org.docx4j.wml.ObjectFactory factory = Context.getWmlObjectFactory();
		
		Num newNum = factory.createNumberingNum();
		newNum.setNumId( BigInteger.valueOf(newNumId) );
		AbstractNumId abstractNumId = factory.createNumberingNumAbstractNumId();
		abstractNumId.setVal(abstractNumIdVal);
		newNum.setAbstractNumId(abstractNumId);
		
		LvlOverride lvlOverride = factory.createNumberingNumLvlOverride();
		lvlOverride.setIlvl(BigInteger.valueOf(ilvl));
		newNum.getLvlOverride().add(lvlOverride);
		
		StartOverride start = factory.createNumberingNumLvlOverrideStartOverride();
		start.setVal(BigInteger.valueOf(val));
		lvlOverride.setStartOverride(start);
    	
    	// Add it to the jaxb object and our hashmap
		((Numbering)jaxbElement).getNum().add(newNum);
        ListNumberingDefinition listDef 
    		= new ListNumberingDefinition(newNum, abstractListDefinitions);
        instanceListDefinitions.put(listDef.getListNumberId(), listDef);		
    	
    	// Return the new numId
    	return newNumId;
    	
    }
	
	
	private Emulator em;
//	public void setEmulator(Emulator em) {
//		this.em = em;
//	}
	public Emulator getEmulator() {
		
    	if (em == null ) { 
    		initialiseMaps();
    		em = new Emulator();    		
    	}
		
		return em;
	}

	public Ind getInd(NumPr numPr) {
		
		String ilvlString = "0";
		if (numPr.getIlvl()!=null) ilvlString = numPr.getIlvl().getVal().toString();
		
		return getInd(numPr.getNumId().getVal().toString(), ilvlString );
	}
	
	public Ind getInd(String numId, String ilvl) {

		// Operating on the docx4j.listnumbering plane,
		// not the JAXB plane..
		ListNumberingDefinition lnd = instanceListDefinitions.get(numId );
		if (lnd==null) {
			log.debug("couldn't find list for numId: " + numId);
			return null;
		}
		if (ilvl==null) ilvl = "0";
		ListLevel ll = lnd.getLevel(ilvl);
		
		// OK, now on the JAXB plane
		Lvl jaxbOverrideLvl = ll.getJaxbOverrideLvl();
		
		log.debug("Looking at override/instance definition..");
		if (jaxbOverrideLvl!=null) {
			
			Ind ind = getIndFromLvl(jaxbOverrideLvl);
			if (ind!=null) {
				log.debug("Got it..");
				return ind;
			}
		}
		
		// Now do the same for the abstract definition
		log.debug("Looking at abstract definition..");
		Lvl abstractLvl = ll.getJaxbAbstractLvl();
		Ind ind = getIndFromLvl(abstractLvl);
		
		return ind;
	}
	
	private Ind getIndFromLvl(Lvl lvl) {
		
		// If there is a style reference in the instance,
		// as a sibling of pPr,
		// use any w:ind in it (or TODO styles it is based on)
		if (lvl.getPStyle()!=null) {
			
			// Get the style
//			StyleDefinitionsPart stylesPart = ((WordprocessingMLPackage)this.getPackage()).
//				getMainDocumentPart().getStyleDefinitionsPart();
			PropertyResolver propertyResolver 
				= ((WordprocessingMLPackage)this.getPackage()).getMainDocumentPart().getPropertyResolver(); 
			
			log.debug("override level has linked style: " + lvl.getPStyle().getVal() );
			
			org.docx4j.wml.Style style = propertyResolver.getStyle( lvl.getPStyle().getVal() );
			
			// If the style has a w:ind, return it.
			// Otherwise, continue
			if (style.getPPr() != null
					&& style.getPPr().getInd()!=null ) {
				return style.getPPr().getInd();
			}
		}
		
		// If there is a style reference in pPr,
		// but not also one as a sibling of pPr,
		// then no number appears at all!
		
			// TODO: throw ShouldNotBeNumbered??
		
		// If there is a w:ind in the instance use that
		if ( lvl.getPPr()!=null
				&& lvl.getPPr().getInd() !=null ) {
			return lvl.getPPr().getInd();
		}
		
		return null;		
		
	}
	
	@Override
    public Numbering unmarshal( java.io.InputStream is ) throws JAXBException {
    	
		try {
					    		    
			Unmarshaller u = jc.createUnmarshaller();
			
			JaxbValidationEventHandler eventHandler = new JaxbValidationEventHandler();
			if (is.markSupported()) {
				// Only fail hard if we know we can restart
				eventHandler.setContinue(false);
			}
			u.setEventHandler(eventHandler);
			
			try {
				jaxbElement = (Numbering) XmlUtils.unwrap(
						u.unmarshal( is ));	
			} catch (UnmarshalException ue) {
				
				if (is.markSupported() ) {
					// When reading from zip, we use a ByteArrayInputStream,
					// which does support this.
				
					log.info("encountered unexpected content; pre-processing");
					eventHandler.setContinue(true);
										
					try {
						Templates mcPreprocessorXslt = JaxbValidationEventHandler.getMcPreprocessor();
						is.reset();
						JAXBResult result = XmlUtils.prepareJAXBResult(Context.jc);
						XmlUtils.transform(new StreamSource(is), 
								mcPreprocessorXslt, null, result);
						jaxbElement = (Numbering) XmlUtils.unwrap(
								result.getResult() );	
					} catch (Exception e) {
						throw new JAXBException("Preprocessing exception", e);
					}
											
				} else {
					log.error(ue);
					log.error(".. and mark not supported");
					throw ue;
				}
			}
			

		} catch (JAXBException e ) {
			log.error(e);
			throw e;
		}
    	
		return jaxbElement;
    	
    }	

	@Override
    public Numbering unmarshal(org.w3c.dom.Element el) throws JAXBException {

		try {

			Unmarshaller u = jc.createUnmarshaller();
			JaxbValidationEventHandler eventHandler = new JaxbValidationEventHandler();
			eventHandler.setContinue(false);
			u.setEventHandler(eventHandler);
			
			try {
				jaxbElement = (Numbering) XmlUtils.unwrap(
						u.unmarshal( el ) );
			} catch (UnmarshalException ue) {
				log.info("encountered unexpected content; pre-processing");
				try {
					org.w3c.dom.Document doc;
					if (el instanceof org.w3c.dom.Document) {
						doc = (org.w3c.dom.Document) el;
					} else {
						// Hope for the best. Dodgy though; what if this is
						// being used on something deep in the tree?
						// TODO: revisit
						doc = el.getOwnerDocument();
					}
					eventHandler.setContinue(true);
					JAXBResult result = XmlUtils.prepareJAXBResult(Context.jc);
					Templates mcPreprocessorXslt = JaxbValidationEventHandler
							.getMcPreprocessor();
					XmlUtils.transform(doc, mcPreprocessorXslt, null, result);
					jaxbElement = (Numbering) XmlUtils.unwrap(
							result.getResult() );	
				} catch (Exception e) {
					throw new JAXBException("Preprocessing exception", e);
				}
			}
			return jaxbElement;
			
		} catch (JAXBException e) {
			log.error(e);
			throw e;
		}
	}	
}
