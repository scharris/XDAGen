package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.CoreFuns.eqOrNull;
import static gov.fda.nctr.util.CoreFuns.hashcode;
import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.StringFuns.indent;
import static gov.fda.nctr.util.StringFuns.lowercaseInitials;
import static gov.fda.nctr.util.StringFuns.makeNameNotInSet;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.ForeignKey.EquationStyle;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.util.Pair;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryGenerator {

	DBMD dbmd;
	
	// Output templates
	Configuration templateConfig;
	Template rowElementsQueryTemplate;
	Template rowCollectionElementQueryTemplate;
	Template rowForestQueryTemplate;

	
	private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
	private static final String ROWELEMENTSSQUERY_TEMPLATE_NAME = "RowElementsQuery.ftl";
	private static final String ROWCOLLECTIONELEMENT_QUERY_TEMPLATE = "RowCollectionElementQuery.ftl";
	private static final String ROWFOREST_QUERY_TEMPLATE = "RowForestQuery.ftl";

	public enum XmlOutputType { XMLTYPE, CLOB }

	public enum RowOutputType { XMLTYPE_ROW_XML_ONLY,
								CLOB_ROW_XML_ONLY,
		                        ALL_FIELDS_THEN_XMLTYPE_ROW_XML,
		                        ALL_FIELDS_THEN_CLOB_ROW_XML }
	
	
	public enum XmlNamespaceOption { INCLUDE_IF_SET, SUPPRESS }
	
	
	
	public QueryGenerator(DBMD dbmd) // Required
	  throws IOException
	{
		this.dbmd = requireArg(dbmd, "database metadata");
		
		// Configure template engine.
		templateConfig = new Configuration();
		templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
		templateConfig.setObjectWrapper(new DefaultObjectWrapper());
		
		// Load templates.
		rowElementsQueryTemplate = templateConfig.getTemplate(ROWELEMENTSSQUERY_TEMPLATE_NAME);
		rowCollectionElementQueryTemplate = templateConfig.getTemplate(ROWCOLLECTIONELEMENT_QUERY_TEMPLATE);
		rowForestQueryTemplate = templateConfig.getTemplate(ROWFOREST_QUERY_TEMPLATE);
	}
	
	public String getSql(XdaQuery xda_qry)
	{
		if ( xda_qry.getQueryStyle() == XdaQuery.QueryStyle.MULTIPLE_ROW_ELEMENT_RESULTS )
			return getRowElementsQuery(xda_qry.getTableOutputSpec(),
			                           xda_qry.getTableAlias(),
			                           xda_qry.getFilterCondition());
		else
			return getRowCollectionElementQuery(xda_qry.getTableOutputSpec(),
			                                    xda_qry.getTableAlias(),
			                                    xda_qry.getFilterCondition());
	}
	
	
	public String getRowElementsQuery(TableOutputSpec ospec)
	{
		return getRowElementsQuery(ospec,
		                           lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                           null,  // no WHERE clause condition
		                           RowOutputType.CLOB_ROW_XML_ONLY,
		                           null); // no default xml namespace in effect yet
		
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,       // Required
	                                  String table_alias)          // Required
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           null, // no WHERE clause condition
		                           RowOutputType.CLOB_ROW_XML_ONLY,
		                           null); // no default xml namespace in effect yet
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,       // Required
	                                  String table_alias,          // Required
	                                  String filter_condition)     // Optional.  Any fields referenced should be qualified with table_alias.
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           filter_condition,
		                           RowOutputType.CLOB_ROW_XML_ONLY,
		                           null); // no default xml namespace in effect yet
	}


	
	public String getRowElementsQuery(TableOutputSpec ospec,          // Required
	                                  String table_alias,             // Required
	                                  String filter_condition,        // Optional. Any includedFields referenced should be qualified with table_alias.
	                                  RowOutputType row_output_type,  // Required
	                                  String default_xmlns_in_effect) // Optional, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
	{
		requireArg(ospec, "output spec");
		if ( table_alias == null || table_alias.trim().length() == 0 )
			throw new IllegalArgumentException("Table alias cannot be null or an empty string.");
		
		String xmlns = ospec.getOutputXmlNamespace();
		
		final RelId relid = ospec.getRelationId();
		
		boolean convert_to_clob = row_output_type == RowOutputType.CLOB_ROW_XML_ONLY ||
		                          row_output_type == RowOutputType.ALL_FIELDS_THEN_CLOB_ROW_XML;
		
		boolean include_leading_table_fields = row_output_type == RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML ||
                                    	       row_output_type == RowOutputType.ALL_FIELDS_THEN_CLOB_ROW_XML;
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("xmlns", xmlns);
		template_model.put("xmlns_is_default", eqOrNull(xmlns, default_xmlns_in_effect));
		template_model.put("relid", relid);
		template_model.put("include_table_field_columns", include_leading_table_fields);
		template_model.put("convert_to_clob", convert_to_clob);
		template_model.put("output_fields", ospec.getOutputFields());
		template_model.put("row_element_name", ospec.getRowElementName());
		template_model.put("child_subqueries", getChildTableSubqueries(ospec, table_alias, "     "));
		template_model.put("parent_subqueries", getParentTableSubqueries(ospec, table_alias, "     "));
		template_model.put("table_alias", table_alias);
		template_model.put("filter_condition", filter_condition);
		if ( ospec.getRowOrdering() != null )
			template_model.put("order_by_exprs", ospec.getRowOrdering().getOrderByExpressions(table_alias));

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
	
	public String getRowCollectionElementQuery(TableOutputSpec ospec,              // Required
	                                           String rows_query_alias,            // Optional
	                                           String filter_cond_over_rows_query) // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	{
		return getRowCollectionElementQuery(ospec,
		                                    rows_query_alias,
		                                    filter_cond_over_rows_query,
		                                    XmlOutputType.CLOB,
		                                    null); // no default xml namespace in effect yet
	}
	
	public String getRowCollectionElementQuery(TableOutputSpec ospec,              // Required
	                                           String rows_query_alias,            // Optional
	                                           String filter_cond_over_rows_query, // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	                                           XmlOutputType xml_output_type,      // Required
	                                           String default_xmlns_in_effect)     // Optional, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
	{
		String rows_query = getRowElementsQuery(ospec,
		                                        lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                                        null,  // no WHERE clause condition
		                                        RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML, // Export all includedFields for possible use in WHERE condition over the rows query.
		                                        ospec.getOutputXmlNamespace()); // Row elements will be embedded in the collection produced here in which the ospec's xmlns will be the default.
		
		boolean convert_to_clob = xml_output_type == XmlOutputType.CLOB;
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("row_collection_element_name", ospec.getRowCollectionElementName());
		template_model.put("xmlns", ospec.getOutputXmlNamespace());
		template_model.put("xmlns_is_default", eqOrNull(ospec.getOutputXmlNamespace(), default_xmlns_in_effect));
		template_model.put("convert_to_clob", convert_to_clob);
		template_model.put("rows_query", indent(rows_query, "   ", false));
		template_model.put("rows_query_alias", rows_query_alias);
		template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ") : "");
		if ( ospec.getRowOrdering() != null )
			template_model.put("order_by_exprs", ospec.getRowOrdering().getOrderByExpressions(rows_query_alias));

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

	/** Return a single row whose rowcoll_xml column contains a forest of xml elements representing the rows of the indicated table for
	 *  which ospec is the output specification.
	 */
	public String getRowForestQuery(TableOutputSpec ospec,
	                                String rows_query_alias,            // Optional.
	                                String filter_cond_over_rows_query, // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	                                String default_xmlns_in_effect)     // Optional, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
	{
		String rows_query = getRowElementsQuery(ospec, 
		                                        lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                                        null,  // no WHERE clause condition
		                                        RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML, // Export all includedFields for possible use in WHERE condition over the rows query.
		                                        default_xmlns_in_effect);  // Row elements will be embedded directly in the surrounding context, in which default_xmlns_in_effect is the default xml ns.
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("rows_query", indent(rows_query, "   ", false));
		template_model.put("rows_query_alias", rows_query_alias);
		template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ") : "");
		if ( ospec.getRowOrdering() != null )
			template_model.put("order_by_exprs", ospec.getRowOrdering().getOrderByExpressions(rows_query_alias));
		
		try
		{
			Writer sw = new StringWriter();
			
			rowForestQueryTemplate.process(template_model, sw);
		
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
			
			String child_coll_subqry;
			
			if ( parent_ospec.getChildCollectionsStyle() == ChildCollectionsStyle.INLINE )
			{
				child_coll_subqry = getRowForestQuery(child_ospec,
				                                      child_rowelemsquery_alias,
				                                      child_rowelemsquery_cond,
				                                      parent_ospec.getOutputXmlNamespace()); // Parent's xml namespace will be the default where this query output is embedded.
			}
			else // wrap element collections
			{
				child_coll_subqry = getRowCollectionElementQuery(child_ospec,
				                                                 child_rowelemsquery_alias,
				                                                 child_rowelemsquery_cond,
				                                                 XmlOutputType.XMLTYPE,
				                                                 parent_ospec.getOutputXmlNamespace()); // Parent's xml namespace will be the default where this query output is embedded.
			}
			
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
			
			String parent_rowels_query = getRowElementsQuery(parent_ospec,
			                                                 parent_table_alias,
			                                                 parent_rows_cond,
			                                                 RowOutputType.XMLTYPE_ROW_XML_ONLY,
			                                                 child_ospec.getOutputXmlNamespace()); // Child's xml namespace will be the default where this query output is embedded.
			
			if ( trailing_lines_prefix != null )
				parent_rowels_query = indent(parent_rowels_query, trailing_lines_prefix, false);

			parent_table_subqueries.add(parent_rowels_query);
		}
		
		return parent_table_subqueries;
	}
	
	public DBMD getDatabaseMetaData()
	{
		return dbmd;
	}
	

	public static class XdaQuery {
		
		public enum QueryStyle { SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
			                     MULTIPLE_ROW_ELEMENT_RESULTS }
		
		TableOutputSpec ospec;
		QueryStyle queryStyle;
		String tableAlias;
		String filterCondition;
		
		
		public XdaQuery(TableOutputSpec ospec,
		                QueryStyle query_style,
		                String table_alias)
		{
			this(ospec,
			     query_style,
			     table_alias,
			     null);
		}
		
		public XdaQuery(TableOutputSpec ospec,
		                QueryStyle query_style,
		                String table_alias,
		                String filter_condition)
		{
			super();
			
			this.ospec = requireArg(ospec, "table output spec");
			this.queryStyle = query_style;
			this.tableAlias = table_alias;
			this.filterCondition = filter_condition;
		}

		public TableOutputSpec getTableOutputSpec()
		{
			return ospec;
		}
		
		public QueryStyle getQueryStyle()
		{
			return queryStyle;
		}
		
		public String getTableAlias()
		{
			return tableAlias;
		}
		
		public String getFilterCondition()
		{
			return filterCondition;
		}

		
		public int hashCode()
		{
			return ospec.hashCode()
			     + hashcode(tableAlias)
			     + hashcode(filterCondition)
			     + queryStyle.hashCode();
		}
		
		public boolean equals(Object o)
		{
			if ( !(o instanceof XdaQuery) )
				return false;
			
			XdaQuery q = (XdaQuery)o;
			
			if ( this == o )
				return true;
			else
				return ospec.equals(q.ospec)
				    && eqOrNull(tableAlias, q.tableAlias)
				    && eqOrNull(filterCondition, q.filterCondition)
				    && queryStyle.equals(q.queryStyle);
		}
	}

	
	public static void main(String[] args) throws Exception
    {
        int arg_ix = 0;
        String table_name;
        String dbmd_xml_path = null;
        ChildCollectionsStyle child_collections_style;
        String query_outfile_path = null;
    	
        if ( args.length == 4 )
        {
        	table_name = args[arg_ix++];
        	dbmd_xml_path = args[arg_ix++];
        	child_collections_style = ChildCollectionsStyle.valueOf(args[arg_ix++].toUpperCase());
        	query_outfile_path = args[arg_ix++];
        }
        else
        	throw new IllegalArgumentException("Expected arguments: <table> <db-metadata-file> <el-collection-style:INLINE|WRAPPED> <query-output-file>");

        InputStream dbmd_is = new FileInputStream(dbmd_xml_path);
        
        DBMD dbmd = DBMD.readXML(dbmd_is);
        
        if ( dbmd == null )
        	throw new IllegalArgumentException("Could not load metadata from file " + dbmd_xml_path + ", DBMD.readXML() returned null.");

        System.out.println("Database metadata for " + dbmd.getRelationMetaDatas().size() + " relations read from file.");
        
        QueryGenerator g = new QueryGenerator(dbmd);
        
        TableOutputSpec.Factory tosf = new DefaultTableOutputSpecFactory(dbmd,
                                                                         child_collections_style,
                                                                         "http://example/namespace");
        
        TableOutputSpec ospec =
        	tosf.table(table_name)
        	    .withAllChildTables()
        		.withAllParentTables();
        
        String sqlxml_qry = g.getRowElementsQuery(ospec);
        
        OutputStream os = new FileOutputStream(query_outfile_path);
        os.write(sqlxml_qry.getBytes());
        os.close();
        
        System.exit(0);
    }
    
}