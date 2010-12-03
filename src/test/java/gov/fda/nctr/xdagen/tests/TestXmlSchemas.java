package gov.fda.nctr.xdagen.tests;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.util.StringFuns;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.xdagen.DatabaseXmlSchemaGenerator;

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
		String xsd = getXmlSchema(ChildCollectionsStyle.INLINE);
		
		String expected_xsd = StringFuns.resourceAsString("expected_results/xmlschema_inline_el_colls.xsd");

		assertEquals("Inline collections XML Schema not as expected.", expected_xsd, xsd);
	}
	
	public void testWrappedCollectionsXmlSchema() throws IOException
	{
		String xsd = getXmlSchema(ChildCollectionsStyle.WRAPPED);
		
		String expected_xsd = StringFuns.resourceAsString("expected_results/xmlschema_wrapped_el_colls.xsd");

		assertEquals("Wrapped collections XML Schema not as expected.", expected_xsd, xsd);
	}
	
	
	private String getXmlSchema(ChildCollectionsStyle child_colls_style) throws IOException
	{
        DatabaseXmlSchemaGenerator g = new DatabaseXmlSchemaGenerator(dbmd);
        
        g.setIncludeGenerationTimestamp(false);
        
        Set<RelId> toplevel_el_relids = null;
        Set<RelId> toplevel_el_list_relids = null;
        
        String xsd = g.getStandardXMLSchema(toplevel_el_relids,
                                            toplevel_el_list_relids,
                                            "http://example/namespace",
                                            child_colls_style);
        
        return xsd;
	}
	
}
