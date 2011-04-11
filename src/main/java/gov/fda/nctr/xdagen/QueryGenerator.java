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
import gov.fda.nctr.dbmd.Field;
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
import java.sql.Types;
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

	FieldElementContentExpressionGenerator fieldElementContentExpressionGenerator;

	// SQL caching
	boolean cacheGeneratedSqls;
    final Map<XdaQuery,String> cachedSqlsByXdaQuery;
	
	private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
	private static final String ROWELEMENTSSQUERY_TEMPLATE_NAME = "RowElementsQuery.ftl";
	private static final String ROWCOLLECTIONELEMENT_QUERY_TEMPLATE = "RowCollectionElementQuery.ftl";
	private static final String ROWFOREST_QUERY_TEMPLATE = "RowForestQuery.ftl";

	
	public enum XmlOutputColumnType { XMLTYPE, CLOB }

	public enum OutputColumnsOption { XML_COLUMN_ONLY, ALL_FIELDS_THEN_ROW_XML };
	
	public enum XmlNamespaceOption { INCLUDE_IF_SET, SUPPRESS }
	
	

	public QueryGenerator(DBMD dbmd) // Required
	  throws IOException
	{
		this.dbmd = requireArg(dbmd, "database metadata");
		
    	cachedSqlsByXdaQuery = new HashMap<XdaQuery,String>();
    	
		// Configure template engine.
		templateConfig = new Configuration();
		templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
		templateConfig.setObjectWrapper(new DefaultObjectWrapper());
		
		// Load templates.
		rowElementsQueryTemplate = templateConfig.getTemplate(ROWELEMENTSSQUERY_TEMPLATE_NAME);
		rowCollectionElementQueryTemplate = templateConfig.getTemplate(ROWCOLLECTIONELEMENT_QUERY_TEMPLATE);
		rowForestQueryTemplate = templateConfig.getTemplate(ROWFOREST_QUERY_TEMPLATE);
		
		fieldElementContentExpressionGenerator = new DefaultFieldElementContentExpressionGenerator();
	}
	
	public void setCacheGeneratedSql(boolean cache)
	{
		cacheGeneratedSqls = cache;
	}
	
	public void clearGeneratedSqlCache()
	{
		cachedSqlsByXdaQuery.clear();
	}
	
	public String getSql(XdaQuery xda_qry)
	{
		if ( xda_qry.getQueryResultStyle() == XdaQuery.QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS )
			return getRowElementsQuery(xda_qry.getTableOutputSpec(),
			                           xda_qry.getTableAlias(),
			                           xda_qry.getFilterCondition(),
			                           xda_qry.getXmlOutputColumnType(),
			                           xda_qry.getOutputColumnsOption(),
			                           xda_qry.getDefaultXmlnsInEffect());
		
		else if ( xda_qry.getQueryResultStyle() == XdaQuery.QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT )
			return getRowCollectionElementQuery(xda_qry.getTableOutputSpec(),
			                                    xda_qry.getTableAlias(),
			                                    xda_qry.getFilterCondition(),
			                                    xda_qry.getXmlOutputColumnType(),
			                                    xda_qry.getDefaultXmlnsInEffect());
		
		else if ( xda_qry.getQueryResultStyle() == XdaQuery.QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT )
			return getRowForestQuery(xda_qry.getTableOutputSpec(),
			                         xda_qry.getTableAlias(),
			                         xda_qry.getFilterCondition(),
			                         xda_qry.getDefaultXmlnsInEffect());
		
		else 
			throw new IllegalArgumentException("Invalid or unsupported query style in XdaQuery:" + xda_qry.getQueryResultStyle());
	}
	
	
	public String getRowElementsQuery(TableOutputSpec ospec)
	{
		return getRowElementsQuery(ospec,
		                           lowercaseInitials(ospec.getRelationId().getName(),"_"),
		                           null,  // no WHERE clause condition
		                           XmlOutputColumnType.CLOB,
		                           OutputColumnsOption.XML_COLUMN_ONLY,
		                           null); // no default xml namespace in effect yet
		
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,       // Required
	                                  String table_alias)          // Required
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           null, // no WHERE clause condition
		                           XmlOutputColumnType.CLOB,
		                           OutputColumnsOption.XML_COLUMN_ONLY,
		                           null); // no default xml namespace in effect yet
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,       // Required
	                                  String table_alias,          // Required
	                                  String filter_condition)     // Optional.  Any fields referenced should be qualified with table_alias.
	{
		return getRowElementsQuery(ospec,
		                           table_alias,
		                           filter_condition,
		                           XmlOutputColumnType.CLOB,
		                           OutputColumnsOption.XML_COLUMN_ONLY,
		                           null); // no default xml namespace in effect yet
	}


	// MULTIPLE_ROW_ELEMENT_RESULTS implementation
	public String getRowElementsQuery(TableOutputSpec ospec,               // Required
	                                  String table_alias,                  // Required
	                                  String filter_condition,             // Optional. Any fields referenced should be qualified with table_alias.
	                                  XmlOutputColumnType xml_col_type,    // Required
	                                  OutputColumnsOption output_cols_opt, // Required
	                                  String default_xmlns_in_effect)      // Optional, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
	{
		XdaQuery xda_qry = new XdaQuery(ospec,
                                        XdaQuery.QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS,
                                        table_alias,
                                        filter_condition,
                                        xml_col_type,
                                        output_cols_opt,
                                        default_xmlns_in_effect);
		
		String sql = cachedSql(xda_qry);
		
		if ( sql != null )
			return sql;
		else
    	{
			String xmlns = ospec.getOutputXmlNamespace();
		
			final RelId relid = ospec.getRelationId();
		
			Map<String,Object> template_model = new HashMap<String,Object>();
			template_model.put("xmlns", xmlns);
			template_model.put("xmlns_is_default", eqOrNull(xmlns, default_xmlns_in_effect));
			template_model.put("relid", relid);
			template_model.put("include_table_field_columns", output_cols_opt == OutputColumnsOption.ALL_FIELDS_THEN_ROW_XML);
			template_model.put("field_el_content_expr_gen", fieldElementContentExpressionGenerator);
			template_model.put("convert_to_clob", xml_col_type == XmlOutputColumnType.CLOB);
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
			
				sql = sw.toString();
	
				if ( cacheGeneratedSqls )
					cachedSqlsByXdaQuery.put(xda_qry, sql);
				
				return sql;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				throw new RuntimeException("Failed to produce row collection query from template: " + e.getMessage());
			}
    	}
	}
	
	public String getRowCollectionElementQuery(TableOutputSpec ospec) // Required
	{
		return getRowCollectionElementQuery(ospec, null, null, XmlOutputColumnType.CLOB, null);
	}
	
	public String getRowCollectionElementQuery(TableOutputSpec ospec,              // Required
	                                           String rows_query_alias,            // Optional
	                                           String filter_cond_over_rows_query) // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	{
		return getRowCollectionElementQuery(ospec,
		                                    rows_query_alias,
		                                    filter_cond_over_rows_query,
		                                    XmlOutputColumnType.CLOB,
		                                    null); // no default xml namespace in effect yet
	}
	

	public String getRowCollectionElementQuery(TableOutputSpec ospec,              // Required
	                                           String rows_query_alias,            // Optional
	                                           String filter_cond_over_rows_query, // Optional, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
	                                           XmlOutputColumnType xml_col_type,   // Required
	                                           String default_xmlns_in_effect)     // Optional, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
	{
		String table_alias = lowercaseInitials(ospec.getRelationId().getName(),"_");
		
		// Provide an alias for the FROM-clause row-elements subquery, as some databases such as Postgres require an alias.
		if ( rows_query_alias == null )
			rows_query_alias = table_alias + "_rows";
			
		XdaQuery xda_qry = new XdaQuery(ospec,
                                        XdaQuery.QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                                        rows_query_alias,
                                        filter_cond_over_rows_query,
                                        xml_col_type,
                                        OutputColumnsOption.XML_COLUMN_ONLY,
                                        default_xmlns_in_effect);
		
		String sql = cachedSql(xda_qry);
		
		if ( sql != null )
			return sql;
		else
    	{
			String rows_query = getRowElementsQuery(ospec,
			                                        table_alias,
			                                        null,  // no WHERE clause condition
			                                        XmlOutputColumnType.XMLTYPE,
			                                        OutputColumnsOption.ALL_FIELDS_THEN_ROW_XML, // Export all TOS-included fields for possible use in WHERE condition over the rows query.
			                                        ospec.getOutputXmlNamespace()); // Row elements will be embedded in the collection produced here in which the ospec's xmlns will be the default.
			
			Map<String,Object> template_model = new HashMap<String,Object>();
			template_model.put("row_collection_element_name", ospec.getRowCollectionElementName());
			template_model.put("xmlns", ospec.getOutputXmlNamespace());
			template_model.put("xmlns_is_default", eqOrNull(ospec.getOutputXmlNamespace(), default_xmlns_in_effect));
			template_model.put("convert_to_clob", xml_col_type == XmlOutputColumnType.CLOB);
			template_model.put("rows_query", indent(rows_query, "   ", false));
			template_model.put("rows_query_alias", rows_query_alias);
			template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ") : "");
			if ( ospec.getRowOrdering() != null )
				template_model.put("order_by_exprs", ospec.getRowOrdering().getOrderByExpressions(rows_query_alias));
	
			try
			{
				Writer sw = new StringWriter();
				
				rowCollectionElementQueryTemplate.process(template_model, sw);
				
				sql = sw.toString();
				
				if ( cacheGeneratedSqls )
					cachedSqlsByXdaQuery.put(xda_qry, sql);
				
				return sql;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				throw new RuntimeException("Failed to produce row collection query from template: " + e.getMessage());
			}
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
		String table_alias = lowercaseInitials(ospec.getRelationId().getName(),"_");
		
		// Provide an alias for the FROM-clause row-elements subquery, as some databases such as Postgres require an alias.
		if ( rows_query_alias == null )
			rows_query_alias = table_alias + "_rows";
		
		XdaQuery xda_qry = new XdaQuery(ospec,
                                        XdaQuery.QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT,
                                        rows_query_alias,
                                        filter_cond_over_rows_query,
                                        XmlOutputColumnType.XMLTYPE,
                                        OutputColumnsOption.XML_COLUMN_ONLY,
                                        default_xmlns_in_effect);
		
		String sql = cachedSql(xda_qry);
		
		if ( sql != null )
			return sql;
		else
    	{
			String rows_query = getRowElementsQuery(ospec, 
			                                        table_alias,
			                                        null,  // no WHERE clause condition
			                                        XmlOutputColumnType.XMLTYPE,
			                                        OutputColumnsOption.ALL_FIELDS_THEN_ROW_XML, // Export all TOS-included fields for possible use in WHERE condition over the rows query.
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
			
				sql = sw.toString();
				
				if ( cacheGeneratedSqls )
					cachedSqlsByXdaQuery.put(xda_qry, sql);
				
				return sql;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				throw new RuntimeException("Failed to produce row collection query from template: " + e.getMessage());
			}
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
			
			String child_rowelemsquery_alias = makeNameNotInSet(lowercaseInitials(child_ospec.getRelationId().getName(),"_") + "_rows", Collections.singleton(parent_table_alias));
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
				                                                 XmlOutputColumnType.XMLTYPE,
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
			                                                 XmlOutputColumnType.XMLTYPE,
			                                                 OutputColumnsOption.XML_COLUMN_ONLY,
			                                                 child_ospec.getOutputXmlNamespace()); // Child's xml namespace will be the default where this query output is embedded.
			
			if ( trailing_lines_prefix != null )
				parent_rowels_query = indent(parent_rowels_query, trailing_lines_prefix, false);

			parent_table_subqueries.add(parent_rowels_query);
		}
		
		return parent_table_subqueries;
	}
	
	private final String cachedSql(XdaQuery xda_qry)
	{
		if ( cachedSqlsByXdaQuery.size() == 0 ) // Avoid potentially expensive hash code generation (due to TableOutputSpec) when cache is empty.
			return null;
		else
			return cachedSqlsByXdaQuery.get(xda_qry);
	}
	
	public DBMD getDatabaseMetaData()
	{
		return dbmd;
	}
	
	public FieldElementContentExpressionGenerator getFieldElementContentExpressionGenerator()
	{
		return fieldElementContentExpressionGenerator;
	}
	
	public void setFieldElementContentExpressionGenerator(FieldElementContentExpressionGenerator g)
	{
		this.fieldElementContentExpressionGenerator = g;
	}
	
	
	public interface FieldElementContentExpressionGenerator {
		
		public String getFieldElementContentExpression(String table_alias, Field f);
		
	}
	
	public static class DefaultFieldElementContentExpressionGenerator implements FieldElementContentExpressionGenerator {
		
		public String getFieldElementContentExpression(String table_alias, Field f)
		{
			String qfieldname = table_alias != null ? table_alias + "."  + f.getName() : f.getName();

			switch(f.getJdbcTypeCode())
			{
				case Types.DATE:
					return "TO_CHAR(" + qfieldname + ",'YYYY-MM-DD')";
				case Types.TIMESTAMP:
					return "TO_CHAR(" + qfieldname + ",'YYYY-MM-DD\"T\"HH:MI:SS')";
				default:
					return qfieldname;
			}
		}
	}
	
	/** A class used to store the complete set of information necessary to generate any of the SQL queries generated by the QueryGenerator.
		This class is mainly used internally by the QueryGenerator for caching and for argument checking, but could be useful to clients 
		wanting to store query variations in some cases.
	*/ 
	public static class XdaQuery {
		
		public enum QueryResultStyle { SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
									   MULTIPLE_ROW_ELEMENT_RESULTS,
									   SINGLE_ROW_ELEMENT_FOREST_RESULT }
		
		TableOutputSpec ospec;
		QueryResultStyle queryResultStyle;
		String tableAlias;
		String filterCondition;
		XmlOutputColumnType xmlOutputColumnType;
		OutputColumnsOption outputColumnsOption;
        String defaultXmlnsInEffect;
		
        public XdaQuery(TableOutputSpec ospec)
		{
			this(ospec,
			     QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
			     null, // table_alias
			     null, // filter_cond
			     XmlOutputColumnType.CLOB,
			     OutputColumnsOption.XML_COLUMN_ONLY,
			     null);
		}
        
		public XdaQuery(TableOutputSpec ospec,
		                String table_alias,
		                String filter_cond)
		{
			this(ospec,
			     QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
			     table_alias,
			     filter_cond,
			     XmlOutputColumnType.CLOB,
			     OutputColumnsOption.XML_COLUMN_ONLY,
			     null);
		}

        
		public XdaQuery(TableOutputSpec ospec,
		                QueryResultStyle query_style,
		                String table_alias,
		                String filter_cond)
		{
			this(ospec,
			     query_style,
			     table_alias,
			     filter_cond,
			     XmlOutputColumnType.CLOB,
			     OutputColumnsOption.XML_COLUMN_ONLY,
			     null);
		}
		
		public XdaQuery(TableOutputSpec ospec,
		                QueryResultStyle query_style,
		                String table_alias,
		                String filter_condition,
		                XmlOutputColumnType xml_output_col_type,
		                OutputColumnsOption output_cols_opt,
                        String default_xmlns_in_effect)
		{
			super();
			
			this.ospec = requireArg(ospec, "table output spec");
			this.queryResultStyle = requireArg(query_style, "query result style");
			this.tableAlias = table_alias;
			this.filterCondition = filter_condition;
			this.xmlOutputColumnType = requireArg(xml_output_col_type, "xml output column type");
			this.outputColumnsOption = requireArg(output_cols_opt, "output columns option");
			this.defaultXmlnsInEffect = default_xmlns_in_effect;
			
			// Check input arguments for compatibility.
			
			if ( query_style == QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS )
			{
				if ( table_alias == null || table_alias.trim().length() == 0 )
					throw new IllegalArgumentException("Queries of result style MULTIPLE_ROW_ELEMENT_RESULTS require a table alias.");
			}
			else // single result collection or forest style query
			{
				if ( query_style == QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT && xmlOutputColumnType == XmlOutputColumnType.CLOB )
					throw new IllegalArgumentException("Queries of SINGLE_ROW_ELEMENT_FOREST_RESULT style cannot have a CLOB output column type.");

				if ( outputColumnsOption != OutputColumnsOption.XML_COLUMN_ONLY )
					throw new IllegalArgumentException("Single-row collection and forest style queries require an output columns option of OutputColumnsOption.XML_COLUMN_ONLY");
			}
		}

		public static XdaQuery xdaquery(TableOutputSpec tos)
		{
			return new XdaQuery(tos);
		}
		
		public static XdaQuery xdaquery(TableOutputSpec tos, String table_alias, String filter_cond)
		{
			return new XdaQuery(tos, table_alias, filter_cond);
		}
		
		
		public TableOutputSpec getTableOutputSpec()
		{
			return ospec;
		}
		
		public QueryResultStyle getQueryResultStyle()
		{
			return queryResultStyle;
		}
		
		public String getTableAlias()
		{
			return tableAlias;
		}
		
		public String getFilterCondition()
		{
			return filterCondition;
		}
		
		public XmlOutputColumnType getXmlOutputColumnType()
		{
			return xmlOutputColumnType;
		}

		
		public OutputColumnsOption getOutputColumnsOption()
		{
			return outputColumnsOption;
		}

		
		public String getDefaultXmlnsInEffect()
		{
			return defaultXmlnsInEffect;
		}

		
		public int hashCode()
		{
			return ospec.hashCode()
			     + hashcode(tableAlias)
			     + hashcode(filterCondition)
			     + queryResultStyle.hashCode()
			     + xmlOutputColumnType.hashCode()
			     + outputColumnsOption.hashCode()
			     + hashcode(defaultXmlnsInEffect);
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
				    && queryResultStyle == q.queryResultStyle
					&& xmlOutputColumnType == q.xmlOutputColumnType
					&& outputColumnsOption == q.outputColumnsOption
					&& eqOrNull(defaultXmlnsInEffect, q.defaultXmlnsInEffect);
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