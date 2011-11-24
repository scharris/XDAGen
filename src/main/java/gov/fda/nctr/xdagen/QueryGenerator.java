package gov.fda.nctr.xdagen;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import static gov.fda.nctr.util.CoreFuns.eqOrNull;
import static gov.fda.nctr.util.CoreFuns.hashcode;
import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.StringFuns.indent;
import static gov.fda.nctr.util.StringFuns.lowercaseInitials;
import static gov.fda.nctr.util.StringFuns.makeNameNotInSet;

import gov.fda.nctr.xdagen.TableOutputSpec.RowOrdering;
import static gov.fda.nctr.xdagen.QueryGenerator.OrderByClauseInclusion.INCLUDE_ORDERBY_CLAUSE_IF_ORDERED;
import static gov.fda.nctr.xdagen.QueryGenerator.OrderByClauseInclusion.OMIT_ORDERBY_CLAUSE;
import static gov.fda.nctr.xdagen.QueryGenerator.XmlIndentation.INDENT_UNSPECIFIED;


public class QueryGenerator implements Serializable {

    private DBMD dbmd;

    // Output templates
    private transient Configuration templateConfig;
    private transient Template rowElementsQueryTemplate;
    private transient Template rowCollectionElementQueryTemplate;
    private transient Template rowForestQueryTemplate;

    private FieldElementContentExpressionGenerator fieldElementContentExpressionGenerator;

    private XmlOutputColumnType defaultXmlOutputColumnType;

    private XmlIndentation xmlIndentation;
    private Integer xmlIndentationSize;

    private String largeCharTypeName;

    protected boolean sortUnsortedRowElementCollectionsByPk;

    // SQL caching
    private boolean cacheGeneratedSqls;
    private final Map<XdaQuery,String> cachedSqlsByXdaQuery;


    private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
    private static final String ROWELEMENTSSQUERY_TEMPLATE_NAME = "RowElementsQuery.ftl";
    private static final String ROWCOLLECTIONELEMENT_QUERY_TEMPLATE = "RowCollectionElementQuery.ftl";
    private static final String ROWFOREST_QUERY_TEMPLATE = "RowForestQuery.ftl";


    public enum XmlOutputColumnType { XML_TYPE, LARGE_CHAR_TYPE }

    public enum XmlIndentation { INDENT, NO_INDENT, INDENT_UNSPECIFIED }  // controls indentation clause of xmlserialize for LARGE_CHAR_TYPE xml output column type

    public enum OutputColumnsInclusion { XML_COLUMN_ONLY, ALL_FIELDS_THEN_ROW_XML };

    public enum OrderByClauseInclusion { INCLUDE_ORDERBY_CLAUSE_IF_ORDERED, OMIT_ORDERBY_CLAUSE, NA }


    public QueryGenerator(DBMD dbmd) throws IOException
    {
        this(dbmd, XmlOutputColumnType.LARGE_CHAR_TYPE);
    }

    public QueryGenerator(DBMD dbmd,                                       // Req
                          XmlOutputColumnType default_xml_output_col_type) // Req
      throws IOException
    {
        this.dbmd = requireArg(dbmd, "database metadata");

        this.defaultXmlOutputColumnType = default_xml_output_col_type;

        cachedSqlsByXdaQuery = new HashMap<XdaQuery,String>();

        sortUnsortedRowElementCollectionsByPk = false;

        initTemplates();

        fieldElementContentExpressionGenerator = new DefaultFieldElementContentExpressionGenerator();

        // Database specific options

        String dbms_name = dbmd.getDbmsName();

        // Postgres does not have a clob type, use "text" type instead.
        largeCharTypeName = dbms_name != null && dbms_name.toUpperCase().contains("POSTGRES") ? "text" : "clob";

        // Oracle needs NO INDENT when serializing xml to avoid capricious indentation of xmltype fields mixed with unindented surroundings.
        xmlIndentation = dbms_name != null && dbms_name.toUpperCase().contains("ORACLE") ? XmlIndentation.NO_INDENT : XmlIndentation.INDENT_UNSPECIFIED;
    }

    public void setDefaultXmlOutputColumnType(XmlOutputColumnType t)
    {
        this.defaultXmlOutputColumnType = t;
    }

    public XmlOutputColumnType getDefaultXmlOutputColumnType()
    {
        return defaultXmlOutputColumnType;
    }

    public void setCacheGeneratedSql(boolean cache)
    {
        cacheGeneratedSqls = cache;
    }

    public boolean getCacheGeneratedSql()
    {
        return cacheGeneratedSqls;
    }

    public void clearGeneratedSqlCache()
    {
        cachedSqlsByXdaQuery.clear();
    }

    /** When enabled, all row element collections for table output specifications with no sort order defined are sorted by
     * the primary keys of their tables. This should be useful for testing or other situations where deterministic output
     * is wanted.
     */
    public void setSortUnsortedRowElementCollectionsByPrimaryKeys(boolean sort)
    {
        sortUnsortedRowElementCollectionsByPk = sort;
    }

    public boolean getSortUnsortedRowElementCollectionsByPrimaryKeys()
    {
        return sortUnsortedRowElementCollectionsByPk;
    }


    public void setXmlIndentation(XmlIndentation indent)
    {
        xmlIndentation = indent;
    }

    public XmlIndentation getXmlIndentation()
    {
        return xmlIndentation;
    }

    public void setXmlIndentationSize(Integer size)
    {
        xmlIndentationSize = size;
    }

    public Integer getXmlIndentationSize()
    {
        return xmlIndentationSize;
    }

    public String getSql(XdaQuery xda_qry)
    {
        if ( xda_qry.getQueryResultStyle() == XdaQuery.QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS )
            return getRowElementsQuery(xda_qry.getTableOutputSpec(),
                                       xda_qry.getTableAlias(),
                                       xda_qry.getFilterCondition(),
                                       xda_qry.getOrderByClauseInclusion(),
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
                                   INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
                                   defaultXmlOutputColumnType,
                                   OutputColumnsInclusion.XML_COLUMN_ONLY,
                                   null); // no default xml namespace in effect yet

    }

    public String getRowElementsQuery(TableOutputSpec ospec,       // Req
                                      String table_alias)          // Req
    {
        return getRowElementsQuery(ospec,
                                   table_alias,
                                   null, // no WHERE clause condition
                                   INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
                                   defaultXmlOutputColumnType,
                                   OutputColumnsInclusion.XML_COLUMN_ONLY,
                                   null); // no default xml namespace in effect yet
    }

    public String getRowElementsQuery(TableOutputSpec ospec,       // Req
                                      String table_alias,          // Req
                                      String filter_condition)     // Opt.  Any fields referenced should be qualified with table_alias.
    {
        return getRowElementsQuery(ospec,
                                   table_alias,
                                   filter_condition,
                                   INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
                                   defaultXmlOutputColumnType,
                                   OutputColumnsInclusion.XML_COLUMN_ONLY,
                                   null); // no default xml namespace in effect yet
    }


    /** Produces a sql/xml query which will return one row for each row of the passed relation satisfying the passed filter if any.  In the output, column ROW_XML will hold the xml
     * representation of individual relation rows.  The specific type of this xml column can be controlled with the xml_col_type parameter.  Whether the columns from the relation should
     * also be included is controlled with the output_cols_opt parameter.  If a filter is provided, referenced fields should be qualified with the (required) table_alias.
     * @para ospec  (Required) The output specification for the relation to be queried.
     * @param table_alias  (Required) The alias to be applied in the query for the relation.
     * @param filter_condition  (Optional) The sql condition to be used to filter the relation rows.  The table_alias argument should be used to qualify any relation fields referenced in the condition.
     * @param order_by_incl (Optional) Determines whether an order-by clause should be included in the query if the table output specification specifies an ordering.  This is necessary because the
     * order by clause must be omitted for some databases (and at best will be ignored for most databases) when the query will be used as a subquery FROM clause entry.
     * @param xml_col_type  (Required) Determines whether the output type of the xml column in the result should be a character type or xml type.
     * @param output_cols_opt  (Required) Determines whether the list of relation columns should also be included along with the xml result output column.
     * @param default_xmlns_in_effect (Optional) The xml namespace uri which can be assumed to be the default namespace in effect where this query will be included, if any.  If provided and equal to
     * the namespace for output elements, then a namespace attribute will be omitted in the output produced by the query.  It is always admissable to pass null for this argument, however
     * unnecessary namespace declarations in the output may result.
     * @return the sql/xml query
     */
    public String getRowElementsQuery(TableOutputSpec ospec,
                                      String table_alias,
                                      String filter_condition,
                                      // [ Note: The remaining parameters are typically only useful for those building their own queries which include this query as a subquery. ]
                                      OrderByClauseInclusion order_by_incl,
                                      XmlOutputColumnType xml_col_type,
                                      OutputColumnsInclusion output_cols_opt,
                                      String default_xmlns_in_effect)
    {
        XdaQuery xda_qry = new XdaQuery(ospec,
                                        XdaQuery.QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS,
                                        table_alias,
                                        filter_condition,
                                        order_by_incl,
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
            template_model.put("include_table_field_columns", output_cols_opt == OutputColumnsInclusion.ALL_FIELDS_THEN_ROW_XML);
            template_model.put("field_el_content_expr_gen", fieldElementContentExpressionGenerator);
            template_model.put("convert_to_large_char", xml_col_type == XmlOutputColumnType.LARGE_CHAR_TYPE);
            template_model.put("large_char_type", largeCharTypeName);
            template_model.put("xml_indentation", getXmlIndentationClause());
            template_model.put("output_fields", ospec.getOutputFields());
            template_model.put("row_element_name", ospec.getRowElementName());
            template_model.put("child_subqueries", getChildTableSubqueries(ospec, table_alias, "     "));
            template_model.put("parent_subqueries", getParentTableSubqueries(ospec, table_alias, "     "));
            template_model.put("table_alias", table_alias);
            template_model.put("filter_condition", filter_condition);

            if ( order_by_incl == INCLUDE_ORDERBY_CLAUSE_IF_ORDERED )
            {
                RowOrdering row_ordering = ospec.getRowOrdering() != null ? ospec.getRowOrdering()
                                                                          : sortUnsortedRowElementCollectionsByPk ? getPkRowOrdering(ospec) : null;
                if ( row_ordering != null )
                    template_model.put("order_by_exprs", row_ordering.getOrderByExpressions(table_alias));
            }

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

    public String getRowCollectionElementQuery(TableOutputSpec ospec) // Req
    {
        return getRowCollectionElementQuery(ospec, null, null, defaultXmlOutputColumnType, null);
    }

    public String getRowCollectionElementQuery(TableOutputSpec ospec,              // Req
                                               String rows_query_alias,            // Opt
                                               String filter_cond_over_rows_query) // Opt, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
    {
        return getRowCollectionElementQuery(ospec,
                                            rows_query_alias,
                                            filter_cond_over_rows_query,
                                            defaultXmlOutputColumnType,
                                            null); // no default xml namespace in effect yet
    }


    public String getRowCollectionElementQuery(TableOutputSpec ospec,              // Req
                                               String rows_query_alias,            // Opt
                                               String filter_cond_over_rows_query, // Opt, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
                                               XmlOutputColumnType xml_col_type,   // Req
                                               String default_xmlns_in_effect)     // Opt, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
    {
        String table_alias = lowercaseInitials(ospec.getRelationId().getName(),"_");

        // Provide an alias for the FROM-clause row-elements subquery, as some databases such as Postgres require an alias.
        if ( rows_query_alias == null )
            rows_query_alias = table_alias + "_row";

        XdaQuery xda_qry = new XdaQuery(ospec,
                                        XdaQuery.QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                                        rows_query_alias,
                                        filter_cond_over_rows_query,
                                        OrderByClauseInclusion.NA,
                                        xml_col_type,
                                        OutputColumnsInclusion.XML_COLUMN_ONLY,
                                        default_xmlns_in_effect);

        String sql = cachedSql(xda_qry);

        if ( sql != null )
            return sql;
        else
        {
            String rows_query = getRowElementsQuery(ospec,
                                                    table_alias,
                                                    null,  // no WHERE clause condition
                                                    OMIT_ORDERBY_CLAUSE,
                                                    XmlOutputColumnType.XML_TYPE,
                                                    OutputColumnsInclusion.ALL_FIELDS_THEN_ROW_XML, // Export all TOS-included fields for possible use in WHERE condition or ordering over the rows query.
                                                    ospec.getOutputXmlNamespace()); // Row elements will be embedded in the collection produced here in which the ospec's xmlns will be the default.

            Map<String,Object> template_model = new HashMap<String,Object>();
            template_model.put("row_collection_element_name", ospec.getRowCollectionElementName());
            template_model.put("xmlns", ospec.getOutputXmlNamespace());
            template_model.put("xmlns_is_default", eqOrNull(ospec.getOutputXmlNamespace(), default_xmlns_in_effect));
            template_model.put("convert_to_large_char", xml_col_type == XmlOutputColumnType.LARGE_CHAR_TYPE);
            template_model.put("large_char_type", largeCharTypeName);
            template_model.put("xml_indentation", getXmlIndentationClause());
            template_model.put("rows_query", indent(rows_query, "   ", false));
            template_model.put("rows_query_alias", rows_query_alias);
            template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ") : "");


            RowOrdering row_ordering = ospec.getRowOrdering() != null ? ospec.getRowOrdering()
                                                                      : sortUnsortedRowElementCollectionsByPk ? getPkRowOrdering(ospec) : null;
            if ( row_ordering != null )
                template_model.put("order_by_exprs", row_ordering.getOrderByExpressions(rows_query_alias));


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
                                    String rows_query_alias,            // Opt.
                                    String filter_cond_over_rows_query, // Opt, should use rows_query_alias on any table field references in this condition, if alias is also supplied.
                                    String default_xmlns_in_effect)     // Opt, the xmlns uri which can be assumed to be in effect where this query's output will be embedded, if any.
    {
        String table_alias = lowercaseInitials(ospec.getRelationId().getName(),"_");

        // Provide an alias for the FROM-clause row-elements subquery, as some databases such as Postgres require an alias.
        if ( rows_query_alias == null )
            rows_query_alias = table_alias + "_row";

        XdaQuery xda_qry = new XdaQuery(ospec,
                                        XdaQuery.QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT,
                                        rows_query_alias,
                                        filter_cond_over_rows_query,
                                        OrderByClauseInclusion.NA,
                                        XmlOutputColumnType.XML_TYPE,
                                        OutputColumnsInclusion.XML_COLUMN_ONLY,
                                        default_xmlns_in_effect);

        String sql = cachedSql(xda_qry);

        if ( sql != null )
            return sql;
        else
        {
            String rows_query = getRowElementsQuery(ospec,
                                                    table_alias,
                                                    null,  // no WHERE clause condition
                                                    OMIT_ORDERBY_CLAUSE,
                                                    XmlOutputColumnType.XML_TYPE,
                                                    OutputColumnsInclusion.ALL_FIELDS_THEN_ROW_XML, // Export all TOS-included fields for possible use in WHERE condition over the rows query.
                                                    default_xmlns_in_effect);  // Row elements will be embedded directly in the surrounding context, in which default_xmlns_in_effect is the default xml ns.

            Map<String,Object> template_model = new HashMap<String,Object>();
            template_model.put("rows_query", indent(rows_query, "   ", false));
            template_model.put("rows_query_alias", rows_query_alias);
            template_model.put("where_cond", filter_cond_over_rows_query != null ? "where\n" + indent(filter_cond_over_rows_query, "  ") : "");


            RowOrdering row_ordering = ospec.getRowOrdering() != null ? ospec.getRowOrdering()
                                                                      : sortUnsortedRowElementCollectionsByPk ? getPkRowOrdering(ospec) : null;
            if ( row_ordering != null )
                template_model.put("order_by_exprs", row_ordering.getOrderByExpressions(rows_query_alias));


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
                                                 String trailing_lines_prefix) // Opt prefix for all lines but the first.
    {
        List<String> child_table_subqueries = new ArrayList<String>();

        // Child tables
        for(Pair<ForeignKey,TableOutputSpec> p: parent_ospec.getChildOutputSpecsByFK())
        {
            ForeignKey fk = p.fst();
            TableOutputSpec child_ospec = p.snd();

            // Make sure the child's alias is chosen to be distinct from the parent's since they will be in the same namespace.
            String child_rowelemsquery_alias = makeNameNotInSet(lowercaseInitials(child_ospec.getRelationId().getName(),"_") + "_row",
                                                                Collections.singleton(parent_table_alias));
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
                                                                 XmlOutputColumnType.XML_TYPE,
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
                                                  String trailing_lines_prefix) // Opt prefix for all lines but the first.
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
                                                             OMIT_ORDERBY_CLAUSE,
                                                             XmlOutputColumnType.XML_TYPE,
                                                             OutputColumnsInclusion.XML_COLUMN_ONLY,
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

    private String getXmlIndentationClause()
    {
        return xmlIndentation == INDENT_UNSPECIFIED ? null
                : xmlIndentation == XmlIndentation.NO_INDENT ? "no indent"
                : "indent" + (xmlIndentationSize != null ? " " + xmlIndentationSize : "");
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


    private RowOrdering getPkRowOrdering(TableOutputSpec ospec)
    {
        List<String> pk_field_names = dbmd.getPrimaryKeyFieldNames(ospec.getRelationId());

        if ( pk_field_names.size() > 0 )
            return RowOrdering.fields(pk_field_names);
        else
            return null;
    }


    private void initTemplates() throws IOException
    {
        // Configure template engine.
        templateConfig = new Configuration();
        templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
        templateConfig.setObjectWrapper(new DefaultObjectWrapper());

        // Load templates.
        rowElementsQueryTemplate = templateConfig.getTemplate(ROWELEMENTSSQUERY_TEMPLATE_NAME);
        rowCollectionElementQueryTemplate = templateConfig.getTemplate(ROWCOLLECTIONELEMENT_QUERY_TEMPLATE);
        rowForestQueryTemplate = templateConfig.getTemplate(ROWFOREST_QUERY_TEMPLATE);
    }

    private Object readResolve()
    {
        try
        {
            initTemplates();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        return this;
    }



    public interface FieldElementContentExpressionGenerator {

        public String getFieldElementContentExpression(String table_alias, Field f);

    }

    public static class DefaultFieldElementContentExpressionGenerator implements FieldElementContentExpressionGenerator, Serializable {

        public String getFieldElementContentExpression(String table_alias, Field f)
        {
            String qfieldname = table_alias != null ? table_alias + "."  + f.getName() : f.getName();

            switch(f.getJdbcTypeCode())
            {
                case Types.DATE:
                    return "TO_CHAR(" + qfieldname + ",'YYYY-MM-DD')";
                case Types.TIMESTAMP:
                    return "TO_CHAR(" + qfieldname + ",'YYYY-MM-DD\"T\"HH24:MI:SS')";
                default:
                    return qfieldname;
            }
        }
        private static final long serialVersionUID = 1L;
    }

    /** A class used to store the complete set of information necessary to generate any of the SQL queries generated by the QueryGenerator.
        This class is mainly used internally by the QueryGenerator for caching and for argument checking, but could be useful to clients
        wanting to store query variations in some cases.
    */
    public static class XdaQuery implements Serializable {

        TableOutputSpec ospec;
        QueryResultStyle queryResultStyle;
        String tableAlias;
        String filterCondition;
        OrderByClauseInclusion orderByClauseInclusion;
        XmlOutputColumnType xmlOutputColumnType;
        OutputColumnsInclusion outputColumnsInclusion;
        String defaultXmlnsInEffect;

        public enum QueryResultStyle { SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                                       MULTIPLE_ROW_ELEMENT_RESULTS,
                                       SINGLE_ROW_ELEMENT_FOREST_RESULT }


        public XdaQuery(TableOutputSpec ospec)
        {
            this(ospec,
                 QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                 null, // table_alias
                 null, // filter_cond
                 OrderByClauseInclusion.NA,
                 XmlOutputColumnType.LARGE_CHAR_TYPE, // TODO: move this argument up (and in other constructors), since there's not a good default in general.
                 OutputColumnsInclusion.XML_COLUMN_ONLY,
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
                 OrderByClauseInclusion.NA,
                 XmlOutputColumnType.LARGE_CHAR_TYPE,
                 OutputColumnsInclusion.XML_COLUMN_ONLY,
                 null);
        }


        public XdaQuery(TableOutputSpec ospec,
                        QueryResultStyle query_result_style,
                        String table_alias,
                        String filter_cond)
        {
            this(ospec,
                 query_result_style,
                 table_alias,
                 filter_cond,
                 query_result_style != QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS ? OrderByClauseInclusion.NA : INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
                 XmlOutputColumnType.LARGE_CHAR_TYPE,
                 OutputColumnsInclusion.XML_COLUMN_ONLY,
                 null);
        }

        public XdaQuery(TableOutputSpec ospec,
                        QueryResultStyle query_result_style,
                        String table_alias,
                        String filter_condition,
                        OrderByClauseInclusion order_by_incl,
                        XmlOutputColumnType xml_output_col_type,
                        OutputColumnsInclusion output_cols_opt,
                        String default_xmlns_in_effect)
        {
            super();

            this.ospec = requireArg(ospec, "table output spec");
            this.queryResultStyle = requireArg(query_result_style, "query result style");
            this.tableAlias = table_alias;
            this.filterCondition = filter_condition;
            this.orderByClauseInclusion = requireArg(order_by_incl, "order by clause inclusion option");
            this.xmlOutputColumnType = requireArg(xml_output_col_type, "xml output column type");
            this.outputColumnsInclusion = requireArg(output_cols_opt, "output columns option");
            this.defaultXmlnsInEffect = default_xmlns_in_effect;

            // Check input arguments for compatibility.

            if ( query_result_style == QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS )
            {
                if ( table_alias == null || table_alias.trim().length() == 0 )
                    throw new IllegalArgumentException("Queries of result style MULTIPLE_ROW_ELEMENT_RESULTS require a table alias.");

                if ( orderByClauseInclusion == OrderByClauseInclusion.NA )
                    throw new IllegalArgumentException("For row elements queries the order_by_incl argument cannot be NA.");
            }
            else // single result collection or forest style query
            {
                if ( query_result_style == QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT && xmlOutputColumnType == XmlOutputColumnType.LARGE_CHAR_TYPE )
                    throw new IllegalArgumentException("Queries of SINGLE_ROW_ELEMENT_FOREST_RESULT style cannot have a must have an XMLTYPE output column type.");

                if ( outputColumnsInclusion != OutputColumnsInclusion.XML_COLUMN_ONLY )
                    throw new IllegalArgumentException("Single-row collection and forest style queries require an output columns option of OutputColumnsInclusion.XML_COLUMN_ONLY");

                if ( orderByClauseInclusion != OrderByClauseInclusion.NA )
                    throw new IllegalArgumentException("For row collection or forest queries the order_by_incl argument must be NA, as it only applies to top level row elements queries.");
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

        public OrderByClauseInclusion getOrderByClauseInclusion()
        {
            return orderByClauseInclusion;
        }

        public XmlOutputColumnType getXmlOutputColumnType()
        {
            return xmlOutputColumnType;
        }


        public OutputColumnsInclusion getOutputColumnsOption()
        {
            return outputColumnsInclusion;
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
                 + orderByClauseInclusion.hashCode()
                 + queryResultStyle.hashCode()
                 + xmlOutputColumnType.hashCode()
                 + outputColumnsInclusion.hashCode()
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
                    && orderByClauseInclusion == q.orderByClauseInclusion
                    && queryResultStyle == q.queryResultStyle
                    && xmlOutputColumnType == q.xmlOutputColumnType
                    && outputColumnsInclusion == q.outputColumnsInclusion
                    && eqOrNull(defaultXmlnsInEffect, q.defaultXmlnsInEffect);
        }

        private static final long serialVersionUID = 1L;
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

        QueryGenerator g = new QueryGenerator(dbmd, XmlOutputColumnType.LARGE_CHAR_TYPE);

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

    private static final long serialVersionUID = 1L;
}
