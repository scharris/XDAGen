package gov.fda.nctr.xdagen.tests;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.util.StringFunctions;
import gov.fda.nctr.xdagen.DefaultElementNamer;
import gov.fda.nctr.xdagen.QueryGenerator;
import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.XmlElementCollectionStyle;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.JAXBException;

import junit.framework.TestCase;


public class TestQueries extends TestCase {

	DBMD dbmd;
	
    
	protected void setUp() throws JAXBException, IOException
    {
		InputStream dbmd_xml_is = getClass().getClassLoader().getResourceAsStream("dbmd.xml");

		dbmd = DBMD.readXML(dbmd_xml_is);
		
		assertNotNull("Could not load metadata from file dbmd.xml file, DBMD.readXML() returned null.", dbmd);
		
		assertTrue("Multiple relation metadatas expected.", dbmd.getRelationMetaDatas().size() > 1);
    }
    
	
	public void testInlineCollectionsDrugRowElementsQuery() throws IOException
	{
		String qry = getDrugRowElementsQuery(XmlElementCollectionStyle.INLINE);
		
		String expected_qry = StringFunctions.resourceAsString("drugs_query_inline_el_colls.sql");

		assertEquals("Inline collections drugs query not as expected.", expected_qry, qry);
	}
	
	
	public void testWrappedCollectionsDrugRowElementsQuery() throws IOException
	{
		String qry = getDrugRowElementsQuery(XmlElementCollectionStyle.WRAPPED);
		
		String expected_qry = StringFunctions.resourceAsString("drugs_query_wrapped_el_colls.sql");

		assertEquals("Wrapped collections drugs query not as expected.", expected_qry, qry);
	}
	
	
	private String getDrugRowElementsQuery(XmlElementCollectionStyle el_coll_style) throws IOException 
	{
		QueryGenerator g = new QueryGenerator(dbmd,
		                                      "http://example/namespace",
		                                      new DefaultElementNamer(dbmd, el_coll_style),
		                                      el_coll_style);
		
		TableOutputSpec ospec =
	        	g.table("drug")
	        	    .addAllChildTables()
	        		.addAllParentTables();
	        
		return g.getRowElementsQuery(ospec);
	}
	
}
