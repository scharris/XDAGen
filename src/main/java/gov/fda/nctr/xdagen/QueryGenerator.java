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
import gov.fda.nctr.dbmd.RelMetaData;
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
	
	XmlElementCollectionStyle xmlElementCollectionStyle;
	

	
	Configuration templateConfig;
	Template rowElementsQueryTemplate;
	Template rowCollectionElementQueryTemplate;
	Template rowForestQueryTemplate;

	String outputXmlNamespace;

	
	
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
	
	
	
	public QueryGenerator(DBMD dbmd, String output_xml_namespace) throws IOException
	{
		this(dbmd, 
		     output_xml_namespace,
		     XmlElementCollectionStyle.INLINE);
	}
	
	
	// Primary constructor.
	public QueryGenerator(DBMD dbmd,
	                      String output_xml_namespace, // optional, xml namespace for generated xml elements
	                      XmlElementCollectionStyle xml_collection_style) 
	  throws IOException
	{
		this.dbmd = dbmd;
		this.outputXmlNamespace = output_xml_namespace;
		this.xmlElementCollectionStyle = xml_collection_style;

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
		                           XmlNamespaceOption.INCLUDE_IF_SET);
		
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  String table_alias) // Optional, defaults to lowercase initials of underscore separated table words.  Use "" to force using no alias.
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           null,  // no WHERE clause condition
		                           RowOutputType.CLOB_ROW_XML_ONLY,
		                           XmlNamespaceOption.INCLUDE_IF_SET);
	}
	

	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  String table_alias, // Required
	                                  String filter_condition) // Optional.  Any includedFields referenced should be qualified with table_alias.
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           filter_condition,
		                           RowOutputType.CLOB_ROW_XML_ONLY,
		                           XmlNamespaceOption.INCLUDE_IF_SET);
	}
	
	/** Returns a query for rows of the indicated table, with optional leading includedFields followed by an XMLType column row_xml containing
	 *  the xml representation of the row.  
	 */
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  String table_alias, // Required
	                                  String filter_condition, // Optional.  Any includedFields referenced should be qualified with table_alias.
	                                  RowOutputType row_output_type,
	                                  XmlNamespaceOption xmlns_opt)
	{
		if ( table_alias == null || table_alias.trim().length() == 0 )
			throw new IllegalArgumentException("Table alias is required.");
		
		String xml_namespace = xmlns_opt == XmlNamespaceOption.INCLUDE_IF_SET ? outputXmlNamespace : null;
		
		final RelId relid = ospec.getRelationId();
		
		final RelMetaData relmd = dbmd.getRelationMetaData(relid);
		
		boolean convert_to_clob = row_output_type == RowOutputType.CLOB_ROW_XML_ONLY ||
		                          row_output_type == RowOutputType.ALL_FIELDS_THEN_CLOB_ROW_XML;
		
		boolean include_leading_table_fields = row_output_type == RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML ||
                                    	       row_output_type == RowOutputType.ALL_FIELDS_THEN_CLOB_ROW_XML;
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("xmlns", xml_namespace);
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
		return getRowCollectionElementQuery(ospec,
		                                    rows_query_alias,
		                                    filter_cond_over_rows_query,
		                                    XmlOutputType.CLOB,
		                                    XmlNamespaceOption.INCLUDE_IF_SET);
	}
	
	public String getRowCollectionElementQuery(TableOutputSpec ospec,
	                                           String rows_query_alias, // Optional.
	                                           String filter_cond_over_rows_query, // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	                                           XmlOutputType xml_output_type,
	                                           XmlNamespaceOption xmlns_opt)
	{
		String rows_query = getRowElementsQuery(ospec, 
		                                        lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                                        null,  // no WHERE clause condition
		                                        RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML, // Export all includedFields for possible use in WHERE condition over the rows query.
		                                        XmlNamespaceOption.SUPPRESS); // Collection element above these will determine the namespace.
		
		boolean convert_to_clob = xml_output_type == XmlOutputType.CLOB;
		
		String xml_namespace = xmlns_opt == XmlNamespaceOption.INCLUDE_IF_SET ? outputXmlNamespace : null;
		
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("row_collection_element_name", ospec.getRowCollectionElementName());
		template_model.put("xmlns", xml_namespace);
		template_model.put("convert_to_clob", convert_to_clob);
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

	/** Return a single row whose rowcoll_xml column contains a forest of xml elements representing the rows of the indicated table for
	 * which ospec is the output specification.
	 */
	public String getRowForestQuery(TableOutputSpec ospec,
	                                String rows_query_alias, // Optional.
	                                String filter_cond_over_rows_query) // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	{
		String rows_query = getRowElementsQuery(ospec, 
		                                        lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                                        null,  // no WHERE clause condition
		                                        RowOutputType.ALL_FIELDS_THEN_XMLTYPE_ROW_XML, // Export all includedFields for possible use in WHERE condition over the rows query.
		                                        XmlNamespaceOption.SUPPRESS); // Collection element above these will determine the namespace.
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("rows_query", indent(rows_query, "   ", false));
		template_model.put("rows_query_alias", rows_query_alias);
		template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ")
				                                                             : "");
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
			
			if ( xmlElementCollectionStyle == XmlElementCollectionStyle.INLINE )
			{
				child_coll_subqry = getRowForestQuery(child_ospec,
				                                      child_rowelemsquery_alias,
				                                      child_rowelemsquery_cond); // Ancestor element will determine namespace.
			}
			else // wrap element collections
			{
				child_coll_subqry = getRowCollectionElementQuery(child_ospec,
				                                                 child_rowelemsquery_alias,
				                                                 child_rowelemsquery_cond,
				                                                 XmlOutputType.XMLTYPE,
				                                                 XmlNamespaceOption.SUPPRESS); // Ancestor element will determine namespace.
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
			
			String parent_rowel_query = getRowElementsQuery(parent_ospec,
			                                                parent_table_alias,
			                                                parent_rows_cond,
			                                                RowOutputType.XMLTYPE_ROW_XML_ONLY,
			                                                XmlNamespaceOption.SUPPRESS);  // Ancestor element will determine namespace.
			
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
	
	public String getDefaultOutputXmlNamespace()
	{
		return outputXmlNamespace;
	}

	
	public void setDefaultOutputXmlNamespace(String defaultOutputXmlNamespace)
	{
		this.outputXmlNamespace = defaultOutputXmlNamespace;
	}


	// Convenience method for creating an initial TableOutputSpec with dbmd and an element namer consistent with the element collection style in use in this query generator.
	public TableOutputSpec table(String pq_rel_name)
	{
		return new TableOutputSpec(pq_rel_name, dbmd, new DefaultElementNamer(dbmd, xmlElementCollectionStyle));
	}
	
	
	public static class XdaQuery {
		
		public enum QueryStyle { SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
			                     MULTIPLE_ROW_ELEMENT_RESULTS }
		
		TableOutputSpec ospec;
		String tableAlias;
		String filterCondition;
		QueryStyle queryStyle;
		
		public XdaQuery(TableOutputSpec ospec,
		                String tableAlias,
		                String filterCondition,
		                QueryStyle query_style)
		{
			super();
			
			this.ospec = requireArg(ospec, "table output spec");;
			this.tableAlias = tableAlias;
			this.filterCondition = filterCondition;
			this.queryStyle = query_style;
		}

		public QueryStyle getQueryStyle()
		{
			return queryStyle;
		}

		public TableOutputSpec getTableOutputSpec()
		{
			return ospec;
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
        XmlElementCollectionStyle xml_collection_style;
        String query_outfile_path = null;
    	
        if ( args.length == 4 )
        {
        	table_name = args[arg_ix++];
        	dbmd_xml_path = args[arg_ix++];
        	xml_collection_style = XmlElementCollectionStyle.valueOf(args[arg_ix++].toUpperCase());
        	query_outfile_path = args[arg_ix++];
        }
        else
        	throw new IllegalArgumentException("Expected arguments: <table> <db-metadata-file> <el-collection-style:INLINE|WRAPPED> <query-output-file>");

        InputStream dbmd_is = new FileInputStream(dbmd_xml_path);
        
        DBMD dbmd = DBMD.readXML(dbmd_is);
        
        if ( dbmd == null )
        	throw new IllegalArgumentException("Could not load metadata from file " + dbmd_xml_path + ", DBMD.readXML() returned null.");

        System.out.println("Database metadata for " + dbmd.getRelationMetaDatas().size() + " relations read from file.");
        
        QueryGenerator g = new QueryGenerator(dbmd,
                                              "http://example/namespace",
                                              xml_collection_style);
            
        TableOutputSpec ospec =
        	g.table(table_name)
        	    .withAllChildTables()
        		.withAllParentTables();
        
        String sqlxml_qry = g.getRowElementsQuery(ospec);
        
        OutputStream os = new FileOutputStream(query_outfile_path);
        os.write(sqlxml_qry.getBytes());
        os.close();
        
        System.exit(0);
    }
    
}