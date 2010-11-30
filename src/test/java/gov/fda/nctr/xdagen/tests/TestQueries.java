package gov.fda.nctr.xdagen.tests;

import static gov.fda.nctr.xdagen.TableOutputSpec.RowOrdering.byFields;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.util.StringFuns;
import gov.fda.nctr.xdagen.QueryGenerator;
import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.XmlElementCollectionStyle;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.xml.bind.JAXBException;

import oracle.jdbc.pool.OracleDataSource;

import junit.framework.TestCase;


public class TestQueries extends TestCase {

	DBMD dbmd;
	
	Connection conn;
	
	QueryGenerator inlineCollElsQryGen;
	QueryGenerator wrappedCollElsQryGen;
	
	TableOutputSpec drugInlineColls;
	TableOutputSpec drugWrappedColls;

	/* When true, expected data will be *written* from the data to be tested, so all tests should pass.  Used to setup initial expected data.
	 * Use with care and verify written "expected" data is actually as expected, obviously.
	 */
	private boolean onlyWriteExpectedData = false; 
	
    
	protected void setUp() throws JAXBException, IOException, SQLException
    {
		InputStream dbmd_xml_is = getClass().getClassLoader().getResourceAsStream("dbmd.xml");

		this.dbmd = DBMD.readXML(dbmd_xml_is);
		dbmd_xml_is.close();

		assertNotNull("Could not load metadata from file dbmd.xml file, DBMD.readXML() returned null.", dbmd);
		assertTrue("Multiple relation metadatas expected.", dbmd.getRelationMetaDatas().size() > 1);
		
		this.conn = createConnection(Utils.loadProperties("jdbc.connect.properties"));

		
		
			
		inlineCollElsQryGen = new QueryGenerator(dbmd,
		                                         "http://example/namespace");
		                            		
		this.drugInlineColls = inlineCollElsQryGen.table("drug").withAllChildTables()
		                                                        .withAllParentTables();
			                            		
		wrappedCollElsQryGen = new QueryGenerator(dbmd,
		                                          "http://example/namespace",
		                                          XmlElementCollectionStyle.WRAPPED);
		                            		
		this.drugWrappedColls = wrappedCollElsQryGen.table("drug").withAllChildTables()
	    			         	                                  .withAllParentTables();
		
		insertData();
    }
	
	protected void tearDown() throws SQLException, IOException
	{
		conn.rollback();
	}
	
	private void insertData() throws SQLException, IOException
	{
		String sql = StringFuns.resourceAsString("insert_test_data.sql").replaceAll("\r", " ");
		
		PreparedStatement pstmt = conn.prepareStatement(sql);
		
		pstmt.executeUpdate();
	}
	

	public void testOneRowElementsQueryRowXmlWithInlineCollections() throws IOException, SQLException
	{
		String sql = inlineCollElsQryGen.getRowElementsQuery(drugInlineColls,
		                                                     "d",
		                                                     "d.id = 1");

		Clob row_xml_clob = (Clob)getOneResult("ROW_XML", sql);
		assertNotNull(row_xml_clob);
		
		String row_xml = StringFuns.readStreamAsString(row_xml_clob.getCharacterStream());
		

		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(row_xml, "src/test/resources/expected_results/drug_1_rowxml_inline_el_colls.xml");
			return;
		}
		
		String expected_rowxml = StringFuns.resourceAsString("expected_results/drug_1_rowxml_inline_el_colls.xml");
		
		assertEquals(expected_rowxml.trim(), row_xml.trim());
	}
	
	public void testOneRowElementsQueryRowXmlWithWrappedCollections() throws IOException, SQLException
	{
		String sql = wrappedCollElsQryGen.getRowElementsQuery(drugWrappedColls,
		                                                      "d",
		                                                      "d.id = 1");

		Clob row_xml_clob = (Clob)getOneResult("ROW_XML", sql);
		assertNotNull(row_xml_clob);
		
		String row_xml = StringFuns.readStreamAsString(row_xml_clob.getCharacterStream());
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(row_xml, "src/test/resources/expected_results/drug_1_rowxml_wrapped_el_colls.xml");
			return;
		}
		
		String expected_rowxml = StringFuns.resourceAsString("expected_results/drug_1_rowxml_wrapped_el_colls.xml");
		
		assertEquals(expected_rowxml.trim(), row_xml.trim());
	}
	
	
	public void testRowElementsOrderingAndFilteringWithInlineCollections() throws IOException, SQLException
	{
		TableOutputSpec drug_desc_id_ospec = drugInlineColls.withRowOrdering(byFields("id desc","name")); // name superfluous here but just checking multiple order-by expressions
		
		String sql = inlineCollElsQryGen.getRowElementsQuery(drug_desc_id_ospec,
		                                                     "d",
		                                                     "d.id >= 1 and d.id <= 5");

		Clob row_xml_clob = (Clob)getNthResult(2, "ROW_XML", sql); // 2nd row with this sorting and filtering should be drug with id = 4.
		assertNotNull(row_xml_clob);
		
		String row_xml = StringFuns.readStreamAsString(row_xml_clob.getCharacterStream());
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(row_xml, "src/test/resources/expected_results/drug4_rowxml_inline_el_colls.xml");
			return;
		}
		
		String expected_rowxml = StringFuns.resourceAsString("expected_results/drug4_rowxml_inline_el_colls.xml");
		
		assertEquals(expected_rowxml.trim(), row_xml.trim());
	}

	
	public void testRowElementsOrderingAndFilteringWithWrappedCollections() throws IOException, SQLException
	{
		TableOutputSpec drug_desc_id_ospec = drugWrappedColls.withRowOrdering(byFields("id desc"));
		
		String sql = wrappedCollElsQryGen.getRowElementsQuery(drug_desc_id_ospec,
		                                                      "d",
		                                                      "d.id >= 1 and d.id <= 5");

		Clob row_xml_clob = (Clob)getNthResult(2, "ROW_XML", sql); // 2nd row with this sorting and filtering should be drug with id = 4.
		assertNotNull(row_xml_clob);
		
		String row_xml = StringFuns.readStreamAsString(row_xml_clob.getCharacterStream());
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(row_xml, "src/test/resources/expected_results/drug4_rowxml_wrapped_el_colls.xml");
			return;
		}
		
		String expected_rowxml = StringFuns.resourceAsString("expected_results/drug4_rowxml_wrapped_el_colls.xml");
		
		assertEquals(expected_rowxml.trim(), row_xml.trim());
	}
	
	
	public void testRowCollectionElementQueryWithInlineCollections() throws SQLException, IOException 
	{
		TableOutputSpec drug_id_ordered_ospec = drugInlineColls.withRowOrdering(byFields("id"));
		
		String sql = inlineCollElsQryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);
		
		Clob rowcoll_xml_clob = (Clob)getOneResult("ROWCOLL_XML", sql);
		
		String rowcoll_xml = StringFuns.readStreamAsString(rowcoll_xml_clob.getCharacterStream());
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(rowcoll_xml, "src/test/resources/expected_results/drugs_listing_inline_el_colls.xml");
			return;
		}
		
		String expected_rowcoll_xml = StringFuns.resourceAsString("expected_results/drugs_listing_inline_el_colls.xml");
		
		assertEquals(rowcoll_xml, expected_rowcoll_xml);
	}
	
	public void testRowCollectionElementQueryWithWrappedCollections() throws SQLException, IOException 
	{
		TableOutputSpec drug_id_ordered_ospec = drugWrappedColls.withRowOrdering(byFields("id"));
		
		String sql = wrappedCollElsQryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);
		
		Clob rowcoll_xml_clob = (Clob)getOneResult("ROWCOLL_XML", sql);
		
		String rowcoll_xml = StringFuns.readStreamAsString(rowcoll_xml_clob.getCharacterStream());
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(rowcoll_xml, "src/test/resources/expected_results/drugs_listing_wrapped_el_colls.xml");
			return;
		}
		
		String expected_rowcoll_xml = StringFuns.resourceAsString("expected_results/drugs_listing_wrapped_el_colls.xml");
		
		assertEquals(rowcoll_xml, expected_rowcoll_xml);
	}
	

	protected Object getOneResult(String col_name, String sql) throws SQLException
	{
		return getNthResult(1, col_name, sql);
	}
	
	
	protected Object getNthResult(int n, String col_name, String sql) throws SQLException
	{
		PreparedStatement stmt = conn.prepareStatement(sql);
		ResultSet rs = null;
		
		try
		{
			rs = stmt.executeQuery();
		
			while(n > 0)
			{
				assertTrue(rs.next());
				--n;
			}
		
			return rs.getObject(col_name);
		}
		finally
		{
			if ( rs != null )
				rs.close();
			if ( stmt != null )
				stmt.close();
		}
	}
	

	public void testInlineCollectionsDrugRowElementsQueryText() throws IOException
	{
		String sql = inlineCollElsQryGen.getRowElementsQuery(drugInlineColls, "d");

		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(sql, "src/test/resources/expected_results/drugs_query_inline_el_colls.sql");
			return;
		}
		
		String expected_sql = StringFuns.resourceAsString("expected_results/drugs_query_inline_el_colls.sql");

		assertEquals("Inline collections drugs query not as expected.", expected_sql, sql);
	}
	
	
	public void testWrappedCollectionsDrugRowElementsQueryText() throws IOException
	{
		String sql = wrappedCollElsQryGen.getRowElementsQuery(drugWrappedColls, "d");
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(sql, "src/test/resources/expected_results/drugs_query_wrapped_el_colls.sql");
			return;
		}
		
		String expected_sql = StringFuns.resourceAsString("expected_results/drugs_query_wrapped_el_colls.sql");

		assertEquals("Wrapped collections drugs query not as expected.", expected_sql, sql);
	}
	
	
	public void testInlineCollectionsDrugRowCollectionElementQueryText() throws IOException
	{
		String sql = inlineCollElsQryGen.getRowCollectionElementQuery(drugInlineColls, null, null).replaceAll("\r","");
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(sql, "src/test/resources/expected_results/drugs_collection_query_inline_el_colls.sql");
			return;
		}
		
		String expected_sql = StringFuns.resourceAsString("expected_results/drugs_collection_query_inline_el_colls.sql").replaceAll("\r","");

		 assertEquals("Inline collections drugs collection query not as expected.", expected_sql, sql);
	}
	
	
	public void testWrappedCollectionsDrugRowCollectionElementQueryText() throws IOException
	{
		String sql = wrappedCollElsQryGen.getRowCollectionElementQuery(drugWrappedColls, null, null).replaceAll("\r","");
		
		if ( onlyWriteExpectedData )
		{
			StringFuns.writeStringToFile(sql, "src/test/resources/expected_results/drugs_collection_query_wrapped_el_colls.sql");
			return;
		}
		
		String expected_sql = StringFuns.resourceAsString("expected_results/drugs_collection_query_wrapped_el_colls.sql").replaceAll("\r","");

		 assertEquals("Wrapped collections drugs collection query not as expected.", expected_sql, sql);
	}
	
	
	private Connection createConnection(Properties p) throws SQLException, IOException
	{
	    OracleDataSource ds = new OracleDataSource();
	    ds.setURL(p.getProperty("jdbc-connect-url"));
	    ds.setDriverType("thin");
	    ds.setUser(p.getProperty("user"));
	    ds.setPassword(p.getProperty("password"));
	    
	    Connection conn = ds.getConnection();
	    conn.setAutoCommit(false);
	    
	    return conn;
	}
	
}
