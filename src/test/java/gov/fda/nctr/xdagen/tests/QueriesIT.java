package gov.fda.nctr.xdagen.tests;

import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.StringFuns.readStreamAsString;
import static gov.fda.nctr.util.StringFuns.resourceAsString;
import static gov.fda.nctr.util.StringFuns.writeStringToFile;
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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.custommonkey.xmlunit.Diff;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;


public class QueriesIT  {

	DBMD dbmd;
	
	DataSource dataSource;
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
	
	
	@BeforeClass
	protected void setUp() throws Exception
    {
		InputStream dbmd_xml_is = getClass().getClassLoader().getResourceAsStream("dbmd.xml");

		this.dbmd = DBMD.readXML(dbmd_xml_is);
		dbmd_xml_is.close();

		assert dbmd != null : "Could not load metadata from file dbmd.xml file, DBMD.readXML() returned null.";
		assert dbmd.getRelationMetaDatas().size() > 1 : "Multiple relation metadatas expected.";
	
		// TODO: decide which database to connect to here based on some environment variable or testng parameter.
		Properties connect_props = loadProperties("testdbs/pg.jdbc.properties");
		this.conn = createConnection(connect_props);
		
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


	private void insertData() throws SQLException, IOException
	{
		String sql = StringFuns.resourceAsString("insert_test_data.sql").replaceAll("\r", " ");
		
		PreparedStatement pstmt = conn.prepareStatement(sql);
		
		pstmt.executeUpdate();
	}

	
    @AfterClass
	protected void tearDown() throws Exception
	{
		conn.rollback();
		conn.close();
	}
	

	@Test
	public void testOneRowElementsQueryRowXmlWithInlineCollections() throws Exception
	{
		String sql = qryGen.getRowElementsQuery(drugInlineColls,
		                                        "d",
		                                        "d.id = 1");

		String row_xml = getOneClobResultAsString("ROW_XML", sql);
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(row_xml, "src/test/resources/expected_results/drug_1_rowxml_inline_el_colls.xml");
			return;
		}
		
		Diff xml_diff = new Diff(resourceAsString("expected_results/drug_1_rowxml_inline_el_colls.xml"),
								 row_xml);
		
		assert xml_diff.identical() : "Row elements query result differed from expected value: " + xml_diff;		
	}
	
	@Test
	public void testOneRowElementsQueryRowXmlWithWrappedCollections() throws Exception
	{
		String sql = qryGen.getRowElementsQuery(drugWrappedColls,
		                                        "d",
		                                        "d.id = 1");

		String row_xml = getOneClobResultAsString("ROW_XML", sql);
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(row_xml, "src/test/resources/expected_results/drug_1_rowxml_wrapped_el_colls.xml");
			return;
		}
		
		Diff xml_diff = new Diff(resourceAsString("expected_results/drug_1_rowxml_wrapped_el_colls.xml"),
								 row_xml);
		
		assert xml_diff.identical() : "Row elements query result differed from expected value: " + xml_diff;		
	}
	
	@Test
	public void testRowElementsOrderingAndFilteringWithInlineCollections() throws Exception
	{
		TableOutputSpec drug_desc_id_ospec = drugInlineColls.orderedBy(fields("id desc","name")); // name superfluous here but just checking multiple order-by expressions
		
		String sql = qryGen.getRowElementsQuery(drug_desc_id_ospec,
		                                        "d",
		                                        "d.id >= 1 and d.id <= 5");

		String row_xml = getNthClobResultAsString(2, "ROW_XML", sql);
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(row_xml, "src/test/resources/expected_results/drug4_rowxml_inline_el_colls.xml");
			return;
		}
		
		// 2nd row of rows 1 - 5 in reverse order should be row 4.
		Diff xml_diff = new Diff(resourceAsString("expected_results/drug4_rowxml_inline_el_colls.xml"),
								 row_xml);
		
		assert xml_diff.identical() : "Row elements query result differed from expected value: " + xml_diff;		
	}

	@Test
	public void testRowElementsOrderingAndFilteringWithWrappedCollections() throws Exception
	{
		TableOutputSpec drug_desc_id_ospec = drugWrappedColls.orderedBy(fields("id desc"));
		
		String sql = qryGen.getRowElementsQuery(drug_desc_id_ospec,
		                                        "d",
		                                        "d.id >= 1 and d.id <= 5");

		String row_xml = getNthClobResultAsString(2, "ROW_XML", sql);
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(row_xml, "src/test/resources/expected_results/drug4_rowxml_wrapped_el_colls.xml");
			return;
		}
		
		// 2nd row of rows 1 - 5 in reverse order should be row 4.
		Diff xml_diff = new Diff(resourceAsString("expected_results/drug4_rowxml_wrapped_el_colls.xml"),
								 row_xml);
		
		assert xml_diff.identical() : "Row elements query result differed from expected value: " + xml_diff;		
	}
	
	@Test
	public void testRowCollectionElementQueryWithInlineCollections() throws Exception 
	{
		TableOutputSpec drug_id_ordered_ospec = drugInlineColls.orderedBy(fields("id"));
		
		String sql = qryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);
		
		String rowcoll_xml = getOneClobResultAsString("ROWCOLL_XML", sql);
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(rowcoll_xml, "src/test/resources/expected_results/drugs_listing_inline_el_colls.xml");
			return;
		}
		
		Diff xml_diff = new Diff(resourceAsString("expected_results/drugs_listing_inline_el_colls.xml"),
								 rowcoll_xml);
		
		assert xml_diff.identical() : "Row collection element query result differed from expected value: " + xml_diff;		
	}
	
	@Test
	public void testRowCollectionElementQueryWithWrappedCollections() throws Exception
	{
		TableOutputSpec drug_id_ordered_ospec = drugWrappedColls.orderedBy(fields("id"));
		
		String sql = qryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);

		String rowcoll_xml = getOneClobResultAsString("ROWCOLL_XML", sql);
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(rowcoll_xml, "src/test/resources/expected_results/drugs_listing_wrapped_el_colls.xml");
			return;
		}
		
		Diff xml_diff = new Diff(resourceAsString("expected_results/drugs_listing_wrapped_el_colls.xml"),
								 rowcoll_xml);
		
		assert xml_diff.identical() : "Row collection element query result differed from expected value: " + xml_diff;		
	}
	
	@Test
	public void testInlineCollectionsDrugRowElementsQueryText() throws IOException
	{
		String sql = qryGen.getRowElementsQuery(drugInlineColls, "d").replaceAll("\r","");;

		if ( onlyWriteExpectedData )
		{
			writeStringToFile(sql, "src/test/resources/expected_results/drugs_query_inline_el_colls.sql");
			return;
		}
		
		String expected_sql = resourceAsString("expected_results/drugs_query_inline_el_colls.sql").replaceAll("\r","");;

		assert expected_sql.equals(sql) : "Inline collections drugs query not as expected.";
	}
	
	@Test
	public void testWrappedCollectionsDrugRowElementsQueryText() throws IOException
	{
		String sql = qryGen.getRowElementsQuery(drugWrappedColls, "d").replaceAll("\r","");;
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(sql, "src/test/resources/expected_results/drugs_query_wrapped_el_colls.sql");
			return;
		}
		
		String expected_sql = resourceAsString("expected_results/drugs_query_wrapped_el_colls.sql").replaceAll("\r","");

		assert expected_sql.equals(sql) : "Wrapped collections drugs query not as expected.";
	}
	
	@Test
	public void testInlineCollectionsDrugRowCollectionElementQueryText() throws IOException
	{
		String sql = qryGen.getRowCollectionElementQuery(drugInlineColls, null, null).replaceAll("\r","");
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(sql, "src/test/resources/expected_results/drugs_collection_query_inline_el_colls.sql");
			return;
		}
		
		String expected_sql = resourceAsString("expected_results/drugs_collection_query_inline_el_colls.sql").replaceAll("\r","");

		assert expected_sql.equals(sql): "Inline collections drugs collection query not as expected.";
	}
	
	@Test
	public void testWrappedCollectionsDrugRowCollectionElementQueryText() throws IOException
	{
		String sql = qryGen.getRowCollectionElementQuery(drugWrappedColls, null, null).replaceAll("\r","");
		
		if ( onlyWriteExpectedData )
		{
			writeStringToFile(sql, "src/test/resources/expected_results/drugs_collection_query_wrapped_el_colls.sql");
			return;
		}
		
		String expected_sql = resourceAsString("expected_results/drugs_collection_query_wrapped_el_colls.sql").replaceAll("\r","");

		assert expected_sql.equals(sql) : "Wrapped collections drugs collection query not as expected.";
	}
	
	
	private Connection createConnection(Properties p) throws SQLException, IOException, ClassNotFoundException
	{
		requireArg(p, "jdbc connection properties");
		
		Class.forName(p.getProperty("jdbc-driver-class"));
		
		Connection conn = DriverManager.getConnection(p.getProperty("jdbc-connect-url"),
													  p.getProperty("user"),
													  p.getProperty("password"));
	    conn.setAutoCommit(false);
	    
	    return conn;
	}
	
	protected String getOneClobResultAsString(String col_name, String sql) throws SQLException, IOException
	{
		return getNthClobResultAsString(1, col_name, sql);
	}
	
	protected String getNthClobResultAsString(int n, String col_name, String sql) throws SQLException, IOException
	{
		Clob clob = (Clob)getNthResult(n, col_name, sql);
		
		String str_val = readStreamAsString(clob.getCharacterStream());

		clob.free();
		
		return str_val;
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
				assert rs.next() : "Expected more results.";
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
	
	public static Properties loadProperties(String props_resource_path) throws IOException
	{
		InputStream is = null;
		try
		{
			is = QueriesIT.class.getClassLoader().getResourceAsStream(props_resource_path);
		
			Properties p = new Properties();
			p.load(is);
			
			return p;
		}
		finally
		{
			is.close();
		}
	}
}
