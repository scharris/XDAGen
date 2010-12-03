package gov.fda.nctr.xdagen.tests;

import static gov.fda.nctr.xdagen.TableOutputSpec.RowOrdering.fields;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.util.StringFuns;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.xdagen.DefaultTableOutputSpecFactory;
import gov.fda.nctr.xdagen.QueryGenerator;
import gov.fda.nctr.xdagen.TableOutputSpec;

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
	
	QueryGenerator qryGen;
	
	TableOutputSpec.Factory tosFactoryWithInlineColls;
	TableOutputSpec.Factory tosFactoryWithWrappedColls;
	
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

		
		tosFactoryWithInlineColls = new DefaultTableOutputSpecFactory(dbmd,
		                                                              ChildCollectionsStyle.INLINE,
			                                                          "http://example/namespace");
		tosFactoryWithWrappedColls = new DefaultTableOutputSpecFactory(dbmd,
		                                                               ChildCollectionsStyle.WRAPPED,
			                                                           "http://example/namespace");
			
		qryGen = new QueryGenerator(dbmd);
		                            		
		this.drugInlineColls = tosFactoryWithInlineColls.table("drug").withAllChildTables()
		                                                              .withAllParentTables();
		                            		
		this.drugWrappedColls = tosFactoryWithWrappedColls.table("drug").withAllChildTables()
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
		String sql = qryGen.getRowElementsQuery(drugInlineColls,
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
		String sql = qryGen.getRowElementsQuery(drugWrappedColls,
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
		TableOutputSpec drug_desc_id_ospec = drugInlineColls.orderedBy(fields("id desc","name")); // name superfluous here but just checking multiple order-by expressions
		
		String sql = qryGen.getRowElementsQuery(drug_desc_id_ospec,
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
		TableOutputSpec drug_desc_id_ospec = drugWrappedColls.orderedBy(fields("id desc"));
		
		String sql = qryGen.getRowElementsQuery(drug_desc_id_ospec,
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
		TableOutputSpec drug_id_ordered_ospec = drugInlineColls.orderedBy(fields("id"));
		
		String sql = qryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);
		
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
		TableOutputSpec drug_id_ordered_ospec = drugWrappedColls.orderedBy(fields("id"));
		
		String sql = qryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);
		
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
		String sql = qryGen.getRowElementsQuery(drugInlineColls, "d");

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
		String sql = qryGen.getRowElementsQuery(drugWrappedColls, "d");
		
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
		String sql = qryGen.getRowCollectionElementQuery(drugInlineColls, null, null).replaceAll("\r","");
		
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
		String sql = qryGen.getRowCollectionElementQuery(drugWrappedColls, null, null).replaceAll("\r","");
		
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
