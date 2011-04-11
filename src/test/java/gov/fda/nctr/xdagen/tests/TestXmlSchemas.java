package gov.fda.nctr.xdagen.tests;

import static gov.fda.nctr.util.StringFuns.resourceAsString;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.xdagen.DatabaseXmlSchemaGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import javax.xml.bind.JAXBException;

import org.custommonkey.xmlunit.Diff;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;


public class TestXmlSchemas {

	DBMD dbmd;

	@BeforeClass
	protected void setUp() throws JAXBException, IOException
    {
		InputStream dbmd_xml_is = getClass().getClassLoader().getResourceAsStream("dbmd.xml");

		dbmd = DBMD.readXML(dbmd_xml_is);
	
		assert dbmd != null : "Could not load metadata from file dbmd.xml file, DBMD.readXML() returned null."; 
		
		assert dbmd.getRelationMetaDatas().size() > 1 : "Multiple relation metadatas expected."; 
    }
    
	@Test
	public void testInlineCollectionsXmlSchema() throws Exception
	{
		Diff xml_diff = new Diff(resourceAsString("expected_results/xmlschema_inline_el_colls.xsd"),
								 getXmlSchema(ChildCollectionsStyle.INLINE));
		
		assert xml_diff.identical() : "Inline collections XML Schema not as expected: " + xml_diff;		
	}
	
	@Test
	public void testWrappedCollectionsXmlSchema() throws Exception
	{
		Diff xml_diff = new Diff(resourceAsString("expected_results/xmlschema_wrapped_el_colls.xsd"),
								 getXmlSchema(ChildCollectionsStyle.WRAPPED));
		
		assert xml_diff.identical() : "Wrapped collections XML Schema not as expected: " + xml_diff;		
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
