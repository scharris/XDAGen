package gov.fda.nctr.xdagen.tests;

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

	String db;
	ChildCollectionsStyle childCollectionsStyle;
	
	DBMD dbmd;

	TestingResources res;
	
	@BeforeClass
	protected void setUp() throws JAXBException, IOException
    {
		db = "pg"; // TODO: This should be a testng parameter or fetched from an environment variable (or default to "pg").
		childCollectionsStyle = ChildCollectionsStyle.INLINE; // TODO: "
		
		res = new TestingResources();
		
		InputStream dbmd_xml_is = res.metadataResourceAsStream(db, "dbmd.xml");
		dbmd = DBMD.readXML(dbmd_xml_is);
		dbmd_xml_is.close();
	
		assert dbmd != null : "Could not load metadata from file " + res.metadataResourcePath(db,"dbmd.xml") + ", DBMD.readXML() returned null."; 
		
		assert dbmd.getRelationMetaDatas().size() > 1 : "Multiple relation metadatas expected."; 
    }
    
	@Test
	public void testXmlSchema() throws Exception
	{
		Diff xml_diff = new Diff(res.mdResStr(db,"xmlschema_"+childCollectionsStyle+"_el_colls.xsd"),
								 generateXmlSchemaAsString());
		
		assert xml_diff.identical() : "Inline collections XML Schema not as expected: " + xml_diff;		
	}
	
	
	private String generateXmlSchemaAsString() throws IOException
	{
        DatabaseXmlSchemaGenerator g = new DatabaseXmlSchemaGenerator(dbmd);
        
        g.setIncludeGenerationTimestamp(false);
        
        Set<RelId> toplevel_el_relids = null;
        Set<RelId> toplevel_el_list_relids = null;
        
        String xsd = g.getStandardXMLSchema(toplevel_el_relids,
                                            toplevel_el_list_relids,
                                            "http://example/namespace",
                                            childCollectionsStyle);
        
        return xsd;
	}
}