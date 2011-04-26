package gov.fda.nctr.xdagen.tests;

import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.StringFuns.readStreamAsString;
import static gov.fda.nctr.util.StringFuns.writeStringToFile;
import static gov.fda.nctr.xdagen.TableOutputSpec.RowOrdering.fields;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.xdagen.DefaultTableOutputSpecFactory;
import gov.fda.nctr.xdagen.QueryGenerator;
import gov.fda.nctr.xdagen.QueryGenerator.XmlOutputColumnType;
import gov.fda.nctr.xdagen.QueryGenerator.XmlIndentation;
import gov.fda.nctr.xdagen.TableOutputSpec;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.custommonkey.xmlunit.Diff;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;


public class QueriesIT  {

    String db;
    ChildCollectionsStyle childCollectionsStyle;
    XmlIndentation xmlIndentation;

    DBMD dbmd;
    Connection conn;

    QueryGenerator qryGen;

    TableOutputSpec.Factory tosFactory;

    TableOutputSpec drugTOS;

    TestingResources res; // testing related resources accessor

    /* When true, expected data will be *written* from the data to be tested, so all tests should pass.  Used to setup initial expected data.
     * Use with care and verify written "expected" data is actually as expected, obviously.
     */
    private boolean onlyWriteExpectedData = false;


    public static class QueriesITFactory {
        @Factory
        public Object[] createInstances()
        {
            String[] dbs = {"pg", "ora"};
            ChildCollectionsStyle[] coll_styles = {ChildCollectionsStyle.INLINE, ChildCollectionsStyle.WRAPPED};

            List<QueriesIT> l = new ArrayList<QueriesIT>();

            for(String db: dbs)
                for(ChildCollectionsStyle style: coll_styles)
                {
                    if ( db.equals("pg") )
                        l.add(new QueriesIT(db, style, XmlIndentation.INDENT_UNSPECIFIED)); // postgres doesn't support the indentation clause at all
                    else
                    {
                        l.add(new QueriesIT(db, style, XmlIndentation.INDENT));
                        l.add(new QueriesIT(db, style, XmlIndentation.NO_INDENT));
                        // no IDENT_UNSPECIFIED for Oracle since it behaves strangely (only xmltype fields are indented in the output)
                    }
                }

            return l.toArray();
        }
    }

    public QueriesIT(String db, ChildCollectionsStyle child_coll_style, XmlIndentation xml_indentation)
    {
        this.db = db;
        this.childCollectionsStyle = child_coll_style;
        this.xmlIndentation = xml_indentation;
    }

    @BeforeClass
    protected void setUp() throws Exception
    {
        res = new TestingResources();

        InputStream dbmd_xml_is = res.metadataResourceAsStream(db,"dbmd.xml");
        this.dbmd = DBMD.readXML(dbmd_xml_is);
        dbmd_xml_is.close();

        assert dbmd != null : "Could not load metadata from file "+res.metadataResourcePath(db,"dbmd.xml")+", DBMD.readXML() returned null.";
        assert dbmd.getRelationMetaDatas().size() > 1 : "Multiple relation metadatas expected.";

        Properties connect_props = loadProperties(res.testdbsResPath(db,"jdbc.props"));
        this.conn = createConnection(connect_props);

        tosFactory = new DefaultTableOutputSpecFactory(dbmd, childCollectionsStyle,  "http://example/namespace");

        qryGen = new QueryGenerator(dbmd, XmlOutputColumnType.LARGE_CHAR_TYPE);

        qryGen.setSortUnsortedRowElementCollectionsByPrimaryKeys(true); // Sort each tables output by pk when no sort ordering is defined.

        qryGen.setXmlIndentation(xmlIndentation);

        this.drugTOS = tosFactory.table("drug").withAllChildTables().withAllParentTables();
    }

    @AfterClass
    protected void tearDown() throws Exception
    {
        conn.rollback();
        conn.close();
    }

    @Test
    public void testDrugRowElementResultDocumentsIndividually() throws Exception
    {
        for(int n=1; n <= 2; ++n)
        {
            String sql = qryGen.getRowElementsQuery(drugTOS,
                                                    "d",
                                                    "d.id = ?");

            String row_xml = getOneLargeTextResultAsString("ROW_XML", sql, n);

            String expected_res_name = "drug_"+n+"_rowxml_"+childCollectionsStyle+"_el_colls_" + xmlIndentation + ".xml";

            if ( onlyWriteExpectedData )
            {
                writeStringToFile(row_xml, res.testResourcesClasspathBaseDir() + res.expectedResultPath(expected_res_name));
            }
            else
            {
                Diff xml_diff = new Diff(res.expectedResultAsString(expected_res_name), row_xml);
                assert xml_diff.similar() : "Row elements query result differed from expected value: " + xml_diff;
            }
        }
    }


    @Test
    public void testRowElementsOrderingAndFiltering() throws Exception
    {
        if ( onlyWriteExpectedData )
            return; // drug 2 record is written by another test

        TableOutputSpec drug_desc_id_ospec = drugTOS.orderedBy(fields("id desc","name")); // name superfluous here but just checking multiple order-by expressions

        String sql = qryGen.getRowElementsQuery(drug_desc_id_ospec,
                                                "d",
                                                "d.id >= 1 and d.id <= 5");

        String row_xml = getNthLargeTextResultAsString(4, "ROW_XML", sql);

        String expected_res_name = "drug_2_rowxml_"+childCollectionsStyle+"_el_colls_" + xmlIndentation + ".xml";

        // 4th row of rows 1 - 5 in reverse order should be row 2.
        Diff xml_diff = new Diff(res.expectedResultAsString(expected_res_name), row_xml);

        assert xml_diff.similar() : "Row elements query result differed from expected value: " + xml_diff;
    }


    @Test
    public void testRowCollectionElementQueryResult() throws Exception
    {
        TableOutputSpec drug_id_ordered_ospec = drugTOS.orderedBy(fields("id"));

        String sql = qryGen.getRowCollectionElementQuery(drug_id_ordered_ospec, null, null);

        String rowcoll_xml = getOneLargeTextResultAsString("ROWCOLL_XML", sql);

        String expected_res_name = "drugs_listing_"+childCollectionsStyle+"_el_colls_" + xmlIndentation + ".xml";

        if ( onlyWriteExpectedData )
        {
            writeStringToFile(rowcoll_xml, res.testResourcesClasspathBaseDir() + res.expectedResultPath(expected_res_name));
        }
        else
        {
            Diff xml_diff = new Diff(res.expectedResultAsString(expected_res_name), rowcoll_xml);

            assert xml_diff.similar() : "Row collection element query result differed from expected value: " + xml_diff;
        }
    }

    @Test
    public void testReverseSortedRowCollectionElementQueryResult() throws Exception
    {
        TableOutputSpec drug_id_rev_ordered_ospec = drugTOS.orderedBy(fields("id desc"));

        String sql = qryGen.getRowCollectionElementQuery(drug_id_rev_ordered_ospec, null, null);

        String rowcoll_xml = getOneLargeTextResultAsString("ROWCOLL_XML", sql);

        String expected_res_name = "drugs_reverse_listing_"+childCollectionsStyle+"_el_colls_" + xmlIndentation + ".xml";

        if ( onlyWriteExpectedData )
        {
            writeStringToFile(rowcoll_xml, res.testResourcesClasspathBaseDir() + res.expectedResultPath(expected_res_name));
        }
        else
        {
            Diff xml_diff = new Diff(res.expectedResultAsString(expected_res_name), rowcoll_xml);

            assert xml_diff.similar() : "Reverse sorted row collection element query result differed from expected value: " + xml_diff;
        }
    }



    @Test
    public void testDrugRowElementsQueryText() throws IOException
    {
        String sql = qryGen.getRowElementsQuery(drugTOS, "d").replaceAll("\r","");;

        String expected_res_name = "drugs_query_"+childCollectionsStyle+"_el_colls_" + xmlIndentation + ".sql";

        if ( onlyWriteExpectedData )
        {
            writeStringToFile(sql, res.testResourcesClasspathBaseDir() + res.expectedResultPath(db, expected_res_name));
        }
        else
        {
            String expected_sql = res.expectedResultAsString(db, expected_res_name).replaceAll("\r","");

            assert expected_sql.equals(sql) : "Drugs row elements query not as expected.";
        }
    }


    @Test
    public void testDrugRowCollectionElementQueryText() throws IOException
    {
        String sql = qryGen.getRowCollectionElementQuery(drugTOS, null, null).replaceAll("\r","");

        String expected_res_name = "drugs_collection_query_"+childCollectionsStyle+"_el_colls_" + xmlIndentation + ".sql";

        if ( onlyWriteExpectedData )
        {
            writeStringToFile(sql, res.testResourcesClasspathBaseDir() + res.expectedResultPath(db, expected_res_name));
        }
        else
        {
            String expected_sql = res.expectedResultAsString(db, expected_res_name).replaceAll("\r","");

            assert expected_sql.equals(sql) : "Drugs row collection element query not as expected.";
        }
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

    protected String getOneLargeTextResultAsString(String col_name, String sql, Object... param_vals) throws SQLException, IOException
    {
        return getNthLargeTextResultAsString(1, col_name, sql, param_vals);
    }

    protected String getNthLargeTextResultAsString(int n, String col_name, String sql, Object... param_vals) throws SQLException, IOException
    {
        Object res = getNthResult(n, col_name, sql, param_vals);

        if ( res instanceof String )
            return (String)res;
        else
        {
            Clob clob = (Clob)res;

            String str_val = readStreamAsString(clob.getCharacterStream());

            clob.free();

            return str_val;
        }

    }


    protected Object getOneResult(String col_name, String sql, Object... param_vals) throws SQLException
    {
        return getNthResult(1, col_name, sql, param_vals);
    }


    protected Object getNthResult(int n, String col_name, String sql, Object... param_vals) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = null;

        for(int i=0; i<param_vals.length; ++i)
            stmt.setObject(i+1, param_vals[i]);

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
