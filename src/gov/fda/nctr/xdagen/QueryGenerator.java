package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.StringFunctions.dotQualify;
import static gov.fda.nctr.util.StringFunctions.indent;
import static gov.fda.nctr.util.StringFunctions.lowercaseInitials;
import static gov.fda.nctr.util.StringFunctions.makeNameNotInSet;
import static gov.fda.nctr.xdagen.TableOutputSpec.table;

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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class QueryGenerator {

	DBMD dbmd;
	
	Configuration templateConfig;
	
	Template rowElementsQueryTemplate;
	Template rowCollectionElementQueryTemplate;
	Template tableXSDTemplate;
	
	private static final String CLASSPATH_TEMPLATES_DIR_PATH = "templates";
	private static final String ROWELEMENTSSQUERY_TEMPLATE_NAME = "RowElementsQuery.ftl";
	private static final String ROWCOLLECTIONELEMENT_QUERY_TEMPLATE = "RowCollectionElementQuery.ftl";
	private static final String TABLE_XMLSCHEMA_TEMPLATE = "Tables_XMLSchema.ftl";

	
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
		tableXSDTemplate = templateConfig.getTemplate(TABLE_XMLSCHEMA_TEMPLATE);
	}
	
	public QueryGenerator(String schema, Connection conn) throws SQLException, IOException
	{
		this(new DatabaseMetaDataFetcher().fetchMetaData(conn, schema, true, true, true, true));
	}
	
	

	public String getRowElementsQuery(TableOutputSpec ospec)
	{
		return getRowElementsQuery(ospec, null, null);
	}

	
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  List<String> leading_fields) // Optional, unqualified field names to precede row_xml in select fields list.  Defaults to primary key fields.
	{
		return getRowElementsQuery(ospec, leading_fields, null);
		
	}
	
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  List<String> leading_fields, // Optional, unqualified field names to precede row_xml in select fields list.  Defaults to primary key fields.
	                                  String table_alias) // Optional, defaults to lowercase initials of underscore separated table words.  Use "" to force using no alias.
	{
		return getRowElementsQuery(ospec,
		                           leading_fields,
		                           table_alias,
		                           null); // no WHERE clause condition
	}
	
	/** Returns a query for rows of the indicated table, with optional leading fields followed by an XMLType column row_xml containing
	 *  the xml representation of the row.  
	 */
	public String getRowElementsQuery(TableOutputSpec ospec,
	                                  List<String> leading_fields, // Optional, unqualified field names to precede row_xml in select fields list.  Defaults to primary key fields.
	                                  String table_alias, // Optional, defaults to lowercase initials of underscore separated table words.  Use "" to force using no alias.
	                                  String filter_condition) // Optional, should qualify any fields referenced with table_alias.
	{
		if ( table_alias == null )
			table_alias = lowercaseInitials(ospec.getRelationId().getName(),"_");
		else if ( table_alias.length() == 0 )
			table_alias = null;
		
		final RelId relid = ospec.getRelationId();
		
		final RelMetaData relmd = dbmd.getRelationMetaData(relid);

		List<String> leading_select_fields = leading_fields == null ? dbmd.getPrimaryKeyFieldNames(ospec.getRelationId(), table_alias)
				                                                    : dotQualify(leading_fields, table_alias);
		
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("relid", relid);
		template_model.put("leading_fields", leading_select_fields);
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
	                                           RowElementsQueryFilter row_els_query_filter) // Optional.
	                                    
	{
		String rows_query = getRowElementsQuery(ospec, row_els_query_filter.getUnqualfiedTableFieldNamesSupportingCondition());
		
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("row_collection_element_name", ospec.getRowCollectionElementName());
		template_model.put("rows_query", indent(rows_query, "   ", false));
		template_model.put("rows_query_alias", row_els_query_filter.getRowElementsQueryAlias());
		template_model.put("where_cond", row_els_query_filter.getRowElementsQueryCondition() != null ? "where\n" + indent(row_els_query_filter.getRowElementsQueryCondition(), "  ")
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
			List<String> child_rowelemsquery_cond_uq_fieldnames = fk.getSourceFieldNames();
			
			RowElementsQueryFilter rowelemsquery_filter = new RowElementsQueryFilter(child_rowelemsquery_cond,
			                                                                         child_rowelemsquery_cond_uq_fieldnames,
			                                                                         child_rowelemsquery_alias);
			
			String child_coll_subqry = getRowCollectionElementQuery(child_ospec, rowelemsquery_filter);
			
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
			
			List<String> no_leading_fields = Collections.emptyList(); // no leading fields necessary since query isn't wrapped
			
			String parent_rowel_query = getRowElementsQuery(parent_ospec,
			                                                no_leading_fields,
			                                                parent_table_alias,
			                                                parent_rows_cond);
			
			if ( trailing_lines_prefix != null )
				parent_rowel_query = indent(parent_rowel_query, trailing_lines_prefix, false);

			parent_table_subqueries.add(parent_rowel_query);
		}
		
		return parent_table_subqueries;
	}
	

	public String getStandardXMLSchema(Set<RelId> rels_getting_toplevel_el,      // define top level elements for these
	                                   Set<RelId> rels_getting_toplevel_list_el,
	                                   String target_xml_namespace)
	{
		List<TableOutputSpec> ospecs = new ArrayList<TableOutputSpec>();
		
		for(RelMetaData relmd: dbmd.getRelationMetaDatas())
		{
			TableOutputSpec ospec = new TableOutputSpec(relmd.getRelationId(), dbmd);
		
			ospec.addAllChildTables();
			ospec.addAllParentTables();
			
			ospecs.add(ospec);
		}
		
		return getXMLSchema(ospecs,
		                    rels_getting_toplevel_el,
		                    rels_getting_toplevel_list_el,
		                    target_xml_namespace,
		                    true,  // Part of being the "standard" XMLSchema for this relation is that each parent and child element is optional in the XMLSchema, 
		                    true); // so the schema will match regardless of which parents/children are included in a particular query.
	}
	

	public String getXMLSchema(List<TableOutputSpec> ospecs,
	                           Set<RelId> toplevel_el_rels,      // define top level elements for these
	                           Set<RelId> toplevel_list_el_rels, // define top level list elements for these
	                           String target_xml_namespace,
	                           boolean child_list_els_optional,  // whether child list elements should be optional (for schema reusability)
	                           boolean parent_els_optional)      // whether parent elements     "
	{
		Map<String,Object> template_model = new HashMap<String,Object>();
		template_model.put("qgen", this);
		template_model.put("target_namespace", target_xml_namespace);
		template_model.put("ospecs", ospecs);
		template_model.put("toplevel_el_rels", toplevel_el_rels);
		template_model.put("toplevel_list_el_rels", toplevel_list_el_rels);
		template_model.put("child_els_opt", child_list_els_optional);
		template_model.put("parent_els_opt", parent_els_optional);
		template_model.put("generating_program", getClass().getName());
		template_model.put("generated_date", new java.util.Date());
		
		try
		{
			Writer sw = new StringWriter();
			
			tableXSDTemplate.process(template_model, sw);
		
			return sw.toString();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Failed to produce XML Schema from template: " + e.getMessage());
		}	
	}
	
	
	public DBMD getDatabaseMetaData()
	{
		return dbmd;
	}
	
	
    public String getXmlSchemaTypeForJdbcTypeCode(int jdbc_type)
	{
    	// http://sqltech.cl/doc/oas10gR31/integrate.1013/b28994/adptr_db.htm#CHDBBIEB
    	switch (jdbc_type)
    	{
    	case Types.TINYINT:
    		return "byte";

    	case Types.SMALLINT:
    		return "short";

    	case Types.INTEGER:
    	case Types.BIGINT:
    		return "integer";

    	case Types.FLOAT:
    	case Types.REAL:
    	case Types.DOUBLE:
    		return "double";

    	case Types.DECIMAL:
    	case Types.NUMERIC:
    		return "decimal";

    	case Types.CHAR:
    	case Types.VARCHAR:
    	case Types.LONGVARCHAR:
    	case Types.CLOB:
    	case Types.OTHER:
    		return "string";

    	case Types.BIT:
    	case Types.BOOLEAN:
    		return "boolean";
    		
    	case Types.DATE:
    		return "date";
    	case Types.TIME:
    		return "time";
    	case Types.TIMESTAMP:
    		return "dateTime";

    	case Types.BLOB:
    	case Types.BINARY:
    	case Types.VARBINARY:
    	case Types.LONGVARBINARY:
    		return "base64Binary";

    	default:
    		return "unknown[jdbctype=" + DatabaseMetaDataFetcher.jdbcTypeToString(jdbc_type) + "]";
    	}
	}
	
	
	public static<E> List<E> concat(List<E> l1, List<E> l2)
	{
		ArrayList<E> l = new ArrayList<E>(l1.size() + l2.size());
		
		l.addAll(l1);
		l.addAll(l2);
		
		return l;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	// Inner Classes
	
	public static class RowElementsQueryFilter {
    	
    	String rowElementsQueryCondition; // WHERE clause condition on a rows query (without the "where" keyword).
    	List<String> unqualfiedTableFieldNamesSupportingCondition;
    	String rowElementsQueryAlias;
		
		public RowElementsQueryFilter(String rowElementsQueryCondition, // Optional.
		                              List<String> unqualfiedTableFieldNamesSupportingCondition,  // Optional, but must contain any fields referenced in the condition, if any.
		                              String rowElementsQueryAlias) // Optional, if null no alias will be used.
		{
			super();
			this.rowElementsQueryCondition = rowElementsQueryCondition;
			this.unqualfiedTableFieldNamesSupportingCondition = unqualfiedTableFieldNamesSupportingCondition;
			this.rowElementsQueryAlias = rowElementsQueryAlias;
		}
    	
		public String getRowElementsQueryCondition()
		{
			return rowElementsQueryCondition;
		}
		
		public List<String> getUnqualfiedTableFieldNamesSupportingCondition()
		{
			return unqualfiedTableFieldNamesSupportingCondition;
		}
		
		public String getRowElementsQueryAlias()
		{
			return rowElementsQueryAlias;
		}
    }

	
	// Inner Classes
	////////////////////////////////////////////////////////////////////////////////////////


	
    public static void main(String[] args) throws Exception
    {
        int arg_ix = 0;
        String dbmd_xml_path = null;
        String query_outfile_path = null;
        String xmlschema_outfile_path = null;
    	
        if ( args.length == 3 )
        {
        	dbmd_xml_path = args[arg_ix++];
        	query_outfile_path = args[arg_ix++];
        	xmlschema_outfile_path = args[arg_ix++];;
        }
        else
        	throw new IllegalArgumentException("Expected arguments: <db-metadata-file> <query-output-file> <xmlschema-output-file>");

        InputStream dbmd_is = new FileInputStream(dbmd_xml_path);
        
        DBMD dbmd = DBMD.readXML(dbmd_is);
        
        QueryGenerator g = new QueryGenerator(dbmd);
            
        TableOutputSpec ospec =
        	table("drug",dbmd)
        		.addChild("drug_link")
        		.addParent("compound");
        
        
        String sqlxml_qry = g.getRowElementsQuery(ospec);
        
        OutputStream os = new FileOutputStream(query_outfile_path);
        os.write(sqlxml_qry.getBytes());
        os.close();
        
        
        Set<RelId> rels_getting_toplevel_els = new HashSet<RelId>();
        rels_getting_toplevel_els.add(dbmd.toRelId("ltkb.drug"));
        rels_getting_toplevel_els.add(dbmd.toRelId("ltkb.compound"));
        
        String xsd = g.getStandardXMLSchema(rels_getting_toplevel_els,
                                            rels_getting_toplevel_els,
                                            "http://nctr.fda.gov/just/an/example");
        
        os = new FileOutputStream(xmlschema_outfile_path);
        os.write(xsd.getBytes());
        os.close();
        
        System.exit(0);
    }
    
}