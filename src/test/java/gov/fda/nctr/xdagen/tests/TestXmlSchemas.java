package gov.fda.nctr.xdagen.tests;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.util.StringFunctions;
import gov.fda.nctr.xdagen.DatabaseXmlSchemaGenerator;
import gov.fda.nctr.xdagen.DefaultElementNamer;
import gov.fda.nctr.xdagen.ElementNamer;
import gov.fda.nctr.xdagen.XmlElementCollectionStyle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.xml.bind.JAXBException;

import junit.framework.TestCase;


public class TestXmlSchemas extends TestCase {

	DBMD dbmd;
	
    
	protected void setUp() throws JAXBException, IOException
    {
		InputStream dbmd_xml_is = getClass().getClassLoader().getResourceAsStream("dbmd.xml");

		dbmd = DBMD.readXML(dbmd_xml_is);
		
		assertNotNull("Could not load metadata from file dbmd.xml file, DBMD.readXML() returned null.", dbmd);
		
		assertTrue("Multiple relation metadatas expected.", dbmd.getRelationMetaDatas().size() > 1);
    }
    
	
	public void testInlineCollectionsXmlSchema() throws IOException
	{
		String xsd = getXmlSchema(XmlElementCollectionStyle.INLINE);
		
		String expected_xsd = StringFunctions.resourceAsString("xmlschema_inline_el_colls.xsd");

		assertEquals("Inline collections XML Schema not as expected.", expected_xsd, xsd);
	}
	
	public void testWrappedCollectionsXmlSchema() throws IOException
	{
		String xsd = getXmlSchema(XmlElementCollectionStyle.WRAPPED);
		
		String expected_xsd = StringFunctions.resourceAsString("xmlschema_wrapped_el_colls.xsd");

		assertEquals("Wrapped collections XML Schema not as expected.", expected_xsd, xsd);
	}
	
	
	private String getXmlSchema(XmlElementCollectionStyle el_coll_style) throws IOException
	{
        DatabaseXmlSchemaGenerator g = new DatabaseXmlSchemaGenerator(dbmd,
                                                                      "http://example/namespace",
                                                                      el_coll_style);
        
        g.setSuppressGenerationTimestamp(true);
        
        Set<RelId> toplevel_el_relids = null;
        Set<RelId> toplevel_el_list_relids = null;
        
        ElementNamer el_namer = new DefaultElementNamer(dbmd, el_coll_style);
        
        String xsd = g.getStandardXMLSchema(toplevel_el_relids,
                                            toplevel_el_list_relids,
                                            el_namer);
        
        return xsd;
	}
	
}
