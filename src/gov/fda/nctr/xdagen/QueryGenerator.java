package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.StringFunctions.indent;
import static gov.fda.nctr.util.StringFunctions.lowercaseInitials;
import static gov.fda.nctr.util.StringFunctions.makeNameNotInSet;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.DatabaseMetaDataFetcher;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.ForeignKey.EquationStyle;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.RelMetaData;
import gov.fda.nctr.util.Pair;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class QueryGenerator {

	DBMD dbmd;
	
	Configuration templateConfig;
	
	Template rowElementsQueryTemplate;
	Template rowCollectionElementQueryTemplate;
	Template tableXSDTemplate;
	
	private static final String CLASSPATH_TEMPLATES_DIR_PATH = "templates";
	private static final String ROWELEMENTSSQUERY_TEMPLATE_NAME = "RowElementsQuery.ftl";
	private static final String ROWCOLLECTIONELEMENT_QUERY_TEMPLATE = "RowCollectionElementQuery.ftl";


	public enum RowOutputType { XMLTYPE_ROW_XML_ONLY,
								CLOB_ROW_XML_ONLY,
		                        ALL_FIELDS_THEN_XMLTYPE_ROW_XML,
		                        ALL_FIELDS_THEN_CLOB_ROW_XML }
	
	// Primary constructor.
	public QueryGenerator(DBMD dbmd) throws IOException
	{
		this.dbmd = dbmd;
		
		// Configure template engine.
		templateConfig = new Configuration();
		templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
		templateConfig.setObjectWrapper(new DefaultObjectWrapper());
		
		// Load templates.
		rowElementsQueryTemplate = templateConfig.getTemplate(ROWELEMENTSSQUERY_TEMPLATE_NAME);
		rowCollectionElementQueryTemplate = templateConfig.getTemplate(ROWCOLLECTIONELEMENT_QUERY_TEMPLATE);
	}
	
	public QueryGenerator(String schema, Connection conn) throws SQLException, IOException
	{
		this(new DatabaseMetaDataFetcher().fetchMetaData(conn, schema, true, true, true, true));
	}
	

	
	public String getRowElementsQuery(TableOutputSpec ospec)
	{
		return getRowElementsQuery(ospec, 
		                           lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                           null,  // no WHERE clause condition
		                           RowOutputType.CLOB_ROW_XML_ONLY);
		
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  String table_alias) // Optional, defaults to lowercase initials of underscore separated table words.  Use "" to force using no alias.
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           null,  // no WHERE clause condition
		                           RowOutputType.CLOB_ROW_XML_ONLY);
	}
	

	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  String table_alias, // Required
	                                  String filter_condition) // Optional.  Any fields referenced should be qualified with table_alias.
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           filter_condition,
		                           RowOutputType.CLOB_ROW_XML_ONLY);
	}
	
	/** Returns a query for rows of the indicated table, with optional leading fields followed by an XMLType column row_xml containing
	 *  the xml representation of the row.  
	 */
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  String table_alias, // Required
	                                  String filter_condition, // Optional.  Any fields referenced should be qualified with table_alias.
	                                  RowOutputType row_output_type)
	{
		if ( table_alias == null || table_alias.trim().length() == 0 )
			throw new IllegalArgumentException("Table alias is required.");
		
		final RelId relid = ospec.getRelationId();
		
		final RelMetaData relmd = dbmd.getRelationMetaData(relid);
		
		boolean convert_to_clob = row_output_type == RowOutputType.CLOB_ROW_XML_ONLY ||
		                          row_output_type == RowOutputType.ALL_FIELDS_THEN_CLOB_ROW_XML;
		
		boolean include_leading_table_fields = row_output_type == RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML ||
                                    	       row_output_type == RowOutputType.ALL_FIELDS_THEN_CLOB_ROW_XML;
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("relid", relid);
		template_model.put("include_table_field_columns", include_leading_table_fields);
		template_model.put("convert_to_clob", convert_to_clob);
		template_model.put("all_fields", relmd.getFields());
		template_model.put("row_element_name", ospec.getRowElementName());
		template_model.put("child_subqueries", getChildTableSubqueries(ospec, table_alias, "     "));
		template_model.put("parent_subqueries", getParentTableSubqueries(ospec, table_alias, "     "));
		template_model.put("table_alias", table_alias);
		template_model.put("filter_condition", filter_condition);

		try
		{
			Writer sw = new StringWriter();
			
			rowElementsQueryTemplate.process(template_model, sw);
		
			return sw.toString();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Failed to produce row collection query from template: " + e.getMessage());
		}
	}
	
	
	public String getRowCollectionElementQuery(TableOutputSpec ospec,
	                                           String rows_query_alias, // Optional.
	                                           String filter_cond_over_rows_query) // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	{
		String rows_query = getRowElementsQuery(ospec, 
		                                        lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                                        null,  // no WHERE clause condition
		                                        RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML);
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("row_collection_element_name", ospec.getRowCollectionElementName());
		template_model.put("rows_query", indent(rows_query, "   ", false));
		template_model.put("rows_query_alias", rows_query_alias);
		template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ")
				                                                             : "");
		try
		{
			Writer sw = new StringWriter();
			
			rowCollectionElementQueryTemplate.process(template_model, sw);
		
			return sw.toString();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Failed to produce row collection query from template: " + e.getMessage());
		}	
	}


	
	private List<String> getChildTableSubqueries(TableOutputSpec parent_ospec,
	                                             String parent_table_alias,
	                                             String trailing_lines_prefix) // Optional prefix for all lines but the first.
	{
		List<String> child_table_subqueries = new ArrayList<String>();
		
		// Child tables
		for(Pair<ForeignKey,TableOutputSpec> p: parent_ospec.getChildOutputSpecsByFK())
		{
			ForeignKey fk = p.fst();
			TableOutputSpec child_ospec = p.snd();
			
			String child_rowelemsquery_alias = makeNameNotInSet("row_els_q", Collections.singleton(parent_table_alias));
			String child_rowelemsquery_cond = fk.asEquation(child_rowelemsquery_alias,
			                                                parent_table_alias,
			                                                EquationStyle.SOURCE_ON_LEFTHAND_SIDE);
			
			String child_coll_subqry = getRowCollectionElementQuery(child_ospec,
			                                                        child_rowelemsquery_alias,
			                                                        child_rowelemsquery_cond);
			
			if ( trailing_lines_prefix != null )
				child_coll_subqry = indent(child_coll_subqry, trailing_lines_prefix, false);

			child_table_subqueries.add(child_coll_subqry);
		}
		
		return child_table_subqueries;
	}
	
	
	private List<String> getParentTableSubqueries(TableOutputSpec child_ospec,
	                                              String child_table_alias,
	                                              String trailing_lines_prefix) // Optional prefix for all lines but the first.
	{
		List<String> parent_table_subqueries = new ArrayList<String>();
		
		// Parent tables
		for(Pair<ForeignKey,TableOutputSpec> p: child_ospec.getParentOutputSpecsByFK())
		{
			ForeignKey fk = p.fst();
			TableOutputSpec parent_ospec = p.snd();
			
			String parent_table_alias = makeNameNotInSet(lowercaseInitials(parent_ospec.getRelationId().getName(),"_"),
			                                             Collections.singleton(child_table_alias));

			String parent_rows_cond = fk.asEquation(child_table_alias,
			                                        parent_table_alias,
			                                        EquationStyle.TARGET_ON_LEFTHAND_SIDE);
			
			String parent_rowel_query = getRowElementsQuery(parent_ospec,
			                                                parent_table_alias,
			                                                parent_rows_cond,
			                                                RowOutputType.XMLTYPE_ROW_XML_ONLY);
			
			if ( trailing_lines_prefix != null )
				parent_rowel_query = indent(parent_rowel_query, trailing_lines_prefix, false);

			parent_table_subqueries.add(parent_rowel_query);
		}
		
		return parent_table_subqueries;
	}
	

	public DBMD getDatabaseMetaData()
	{
		return dbmd;
	}
	
	public TableOutputSpec table(String pq_rel_name)
	{
		return new TableOutputSpec(pq_rel_name, dbmd);
	}
	
	
	public static<E> List<E> concat(List<E> l1, List<E> l2)
	{
		ArrayList<E> l = new ArrayList<E>(l1.size() + l2.size());
		
		l.addAll(l1);
		l.addAll(l2);
		
		return l;
	}
	

	
	public static void main(String[] args) throws Exception
    {
        int arg_ix = 0;
        String dbmd_xml_path = null;
        String query_outfile_path = null;
    	
        if ( args.length == 2 )
        {
        	dbmd_xml_path = args[arg_ix++];
        	query_outfile_path = args[arg_ix++];
        }
        else
        	throw new IllegalArgumentException("Expected arguments: <db-metadata-file> <query-output-file>");

        InputStream dbmd_is = new FileInputStream(dbmd_xml_path);
        
        DBMD dbmd = DBMD.readXML(dbmd_is);
        
        QueryGenerator g = new QueryGenerator(dbmd);
            
        TableOutputSpec ospec =
        	g.table("drug")
        		.addChild("drug_link")
        		.addParent("compound");
        
        
        String sqlxml_qry = g.getRowElementsQuery(ospec);
        
        OutputStream os = new FileOutputStream(query_outfile_path);
        os.write(sqlxml_qry.getBytes());
        os.close();
        
        System.exit(0);
    }
    
}