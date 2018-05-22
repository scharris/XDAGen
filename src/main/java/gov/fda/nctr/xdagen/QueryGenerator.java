package gov.fda.nctr.xdagen;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.sql.Types;
import java.util.*;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

import gov.fda.nctr.util.Freemarker;
import gov.fda.nctr.util.Pair;
import static gov.fda.nctr.util.CoreFuns.hashcode;
import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.Freemarker.applyTemplate;
import static gov.fda.nctr.util.StringFuns.indent;
import static gov.fda.nctr.util.StringFuns.lowercaseInitials;
import static gov.fda.nctr.util.StringFuns.makeNameNotInSet;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.Field;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.ForeignKey.EquationStyle;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.xdagen.TableOutputSpec.RowOrdering;
import static gov.fda.nctr.xdagen.QueryGenerator.OrderByClauseInclusion.INCLUDE_ORDERBY_CLAUSE_IF_ORDERED;
import static gov.fda.nctr.xdagen.QueryGenerator.OrderByClauseInclusion.OMIT_ORDERBY_CLAUSE;


public class QueryGenerator
{
    private final DBMD dbmd;

    private Template rowElementsQueryTemplate;
    private transient Template rowCollectionElementQueryTemplate;
    private transient Template rowForestQueryTemplate;

    private FieldElementContentExpressionGenerator fieldElementContentExpressionGenerator;

    private XmlOutputColumnType defaultXmlOutputColumnType;

    private XmlIndentation xmlIndentation;
    private Optional<Integer> xmlIndentationSize;

    private String largeCharTypeName;

    private boolean sortUnsortedRowElementCollectionsByPk;

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

    public QueryGenerator
    (
        DBMD dbmd,
        XmlOutputColumnType defaultXmlOutputColType
    )
        throws IOException
    {
        this.dbmd = requireArg(dbmd, "database metadata");

        this.defaultXmlOutputColumnType = defaultXmlOutputColType;

        this.cachedSqlsByXdaQuery = new HashMap<>();

        this.sortUnsortedRowElementCollectionsByPk = false;

        this.fieldElementContentExpressionGenerator = new DefaultFieldElementContentExpressionGenerator();

        String dbms = dbmd.getDbmsName();

        this.largeCharTypeName = dbms != null && dbms.toUpperCase().contains("POSTGRES") ? "text" : "clob";

        // Oracle needs NO INDENT when serializing xml to avoid capricious indentation of xmltype fields mixed with unindented surroundings.
        this.xmlIndentation = dbms != null && dbms.toUpperCase().contains("ORACLE") ? XmlIndentation.NO_INDENT : XmlIndentation.INDENT_UNSPECIFIED;

        initTemplates();
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
        xmlIndentationSize = Optional.ofNullable(size);
    }

    public Optional<Integer> getXmlIndentationSize()
    {
        return xmlIndentationSize;
    }

    public String getSql(XdaQuery xdaQry)
    {
        switch( xdaQry.getQueryResultStyle() )
        {
            case MULTIPLE_ROW_ELEMENT_RESULTS:
                return getRowElementsQuery(
                    xdaQry.getTableOutputSpec(),
                    xdaQry.getTableAlias().get(),
                    xdaQry.getFilterCondition(),
                    xdaQry.getOrderByClauseInclusion(),
                    xdaQry.getXmlOutputColumnType(),
                    xdaQry.getOutputColumnsOption()
                );
            case SINGLE_ROW_COLLECTION_ELEMENT_RESULT:
                return getRowCollectionElementQuery(
                    xdaQry.getTableOutputSpec(),
                    xdaQry.getTableAlias(),
                    xdaQry.getFilterCondition(),
                    xdaQry.getXmlOutputColumnType()
                );
            case SINGLE_ROW_ELEMENT_FOREST_RESULT:
                return getRowForestQuery(
                    xdaQry.getTableOutputSpec(),
                    xdaQry.getTableAlias(),
                    xdaQry.getFilterCondition()
                );
            default:
                throw new IllegalArgumentException("Invalid or unsupported query style in XdaQuery:" + xdaQry.getQueryResultStyle());
        }
    }

    public String getRowElementsQuery(TableOutputSpec ospec)
    {
        return getRowElementsQuery(
            ospec,
            lowercaseInitials(ospec.getRelationId().getName(),"_"),
            Optional.empty(),  // no WHERE clause condition
            INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
            defaultXmlOutputColumnType,
            OutputColumnsInclusion.XML_COLUMN_ONLY
        );

    }

    public String getRowElementsQuery
    (
        TableOutputSpec ospec,
        String tableAlias
    )
    {
        return getRowElementsQuery(
            ospec,
            tableAlias,
            Optional.empty(), // no WHERE clause condition
            INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
            defaultXmlOutputColumnType,
            OutputColumnsInclusion.XML_COLUMN_ONLY
        );
    }

    public String getRowElementsQuery
    (
        TableOutputSpec ospec,
        String tableAlias,
        Optional<String> filterCondition
    )
    {
        return getRowElementsQuery(
            ospec,
            tableAlias,
            filterCondition,
            INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
            defaultXmlOutputColumnType,
            OutputColumnsInclusion.XML_COLUMN_ONLY
        );
    }


    /** Produces a sql/xml query which will return one row for each row of the passed relation satisfying the passed filter if any.  In the output, column ROW_XML will hold the xml
     * representation of individual relation rows.  The specific type of this xml column can be controlled with the xmlColType parameter.  Whether the columns from the relation should
     * also be included is controlled with the outputColsOpt parameter.  If a filter is provided, referenced fields should be qualified with the (required) tableAlias.
     * @para ospec  The output specification for the relation to be queried.
     * @param tableAlias  The alias to be applied in the query for the relation.
     * @param filterCondition  The sql condition to be used to filter the relation rows, if any. The tableAlias argument should be used to qualify any relation fields referenced in the condition.
     * @param orderByIncl Determines whether an order-by clause should be included in the query if the table output specification specifies an ordering.  This is necessary because the
     * order by clause must be omitted for some databases (and at best will be ignored for most databases) when the query will be used as a subquery FROM clause entry.
     * @param xmlColType  Determines whether the output type of the xml column in the result should be a character type or xml type.
     * @param outputColsOpt  Determines whether the list of relation columns should also be included along with the xml result output column.
     * @return the sql/xml query
     */
    public String getRowElementsQuery
    (
        TableOutputSpec ospec,
        String tableAlias,
        Optional<String> filterCondition,
        // [ Note: The remaining parameters are typically only useful for those building their own queries which include this query as a subquery. ]
        OrderByClauseInclusion orderByIncl,
        XmlOutputColumnType xmlColType,
        OutputColumnsInclusion outputColsOpt
    )
    {
        XdaQuery xdaQry =
            new XdaQuery(
                ospec,
                XdaQuery.QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS,
                Optional.of(tableAlias),
                filterCondition,
                orderByIncl,
                xmlColType,
                outputColsOpt
            );

        Optional<String> cachedSql = cachedSql(xdaQry);

        if ( cachedSql.isPresent() )
            return cachedSql.get();
        else
        {
            final RelId relId = ospec.getRelationId();

            Map<String,Object> templateModel = new HashMap<>();
            templateModel.put("relId", relId);
            templateModel.put("includeTableFieldColumns", outputColsOpt == OutputColumnsInclusion.ALL_FIELDS_THEN_ROW_XML);
            templateModel.put("fieldElContentExprGen", fieldElementContentExpressionGenerator);
            templateModel.put("convertToLargeChar", xmlColType == XmlOutputColumnType.LARGE_CHAR_TYPE);
            templateModel.put("largeCharType", largeCharTypeName);
            templateModel.put("xmlIndentation", getXmlIndentationClause());
            templateModel.put("outputFields", ospec.getOutputFields());
            templateModel.put("rowElementName", ospec.getRowElementName());
            templateModel.put("childSubqueries", getChildTableSubqueries(ospec, tableAlias, Optional.of("     ")));
            templateModel.put("parentSubqueries", getParentTableSubqueries(ospec, tableAlias, Optional.of("     ")));
            templateModel.put("tableAlias", tableAlias);
            templateModel.put("filterCondition", filterCondition);

            if ( orderByIncl == INCLUDE_ORDERBY_CLAUSE_IF_ORDERED )
            {
                Optional<RowOrdering> rowOrdering =
                    ospec.getRowOrdering().isPresent() ? ospec.getRowOrdering()
                    : sortUnsortedRowElementCollectionsByPk ? getPkRowOrdering(ospec) : Optional.empty();

                rowOrdering.ifPresent(ordering ->
                    templateModel.put("orderByExprs", ordering.getOrderByExpressions(tableAlias))
                );
            }

            String sql = applyTemplate(rowElementsQueryTemplate, templateModel);

            if ( cacheGeneratedSqls )
                cachedSqlsByXdaQuery.put(xdaQry, sql);

            return sql;
        }
    }

    public String getRowCollectionElementQuery(TableOutputSpec ospec) // Req
    {
        return getRowCollectionElementQuery(ospec, Optional.empty(), Optional.empty(), defaultXmlOutputColumnType);
    }

    public String getRowCollectionElementQuery
    (
        TableOutputSpec ospec,
        Optional<String> rowsQueryAlias,
        Optional<String> filterCondOverRowsQuery
    )
    {
        return getRowCollectionElementQuery(
            ospec,
            rowsQueryAlias,
            filterCondOverRowsQuery,
            defaultXmlOutputColumnType
        );
    }


    public String getRowCollectionElementQuery
    (
        TableOutputSpec ospec,
        Optional<String> maybeRowsQueryAlias,
        Optional<String> filterCondOverRowsQuery,
        XmlOutputColumnType xmlColType
    )
    {
        Objects.requireNonNull(ospec);
        Objects.requireNonNull(maybeRowsQueryAlias);
        Objects.requireNonNull(filterCondOverRowsQuery);
        Objects.requireNonNull(xmlColType);

        String tableAlias = lowercaseInitials(ospec.getRelationId().getName(),"_");

        // Provide an alias for the FROM-clause row-elements subquery, as some databases such as Postgres require an alias.
        String rowsQueryAlias = maybeRowsQueryAlias.orElseGet(() -> tableAlias + "_row");

        XdaQuery xdaQry =
            new XdaQuery(
                ospec,
                XdaQuery.QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                Optional.of(rowsQueryAlias),
                filterCondOverRowsQuery,
                OrderByClauseInclusion.NA,
                xmlColType,
                OutputColumnsInclusion.XML_COLUMN_ONLY
            );

        Optional<String> cachedSql = cachedSql(xdaQry);

        if ( cachedSql.isPresent() )
            return cachedSql.get();
        else
        {
            String rowsQuery =
                getRowElementsQuery(
                    ospec,
                    tableAlias,
                    Optional.empty(),  // no WHERE clause condition
                    OMIT_ORDERBY_CLAUSE,
                    XmlOutputColumnType.XML_TYPE,
                    OutputColumnsInclusion.ALL_FIELDS_THEN_ROW_XML // Export all TOS-included fields for possible use in WHERE condition or ordering over the rows query.
                );

            Map<String,Object> templateModel = new HashMap<>();
            templateModel.put("rowCollectionElementName", ospec.getRowCollectionElementName());
            templateModel.put("convertToLargeChar", xmlColType == XmlOutputColumnType.LARGE_CHAR_TYPE);
            templateModel.put("largeCharType", largeCharTypeName);
            templateModel.put("xmlIndentation", getXmlIndentationClause());
            templateModel.put("rowsQuery", indent(rowsQuery, "   ", false));
            templateModel.put("rowsQueryAlias", rowsQueryAlias);
            templateModel.put("whereCond", filterCondOverRowsQuery.map(cond -> "where\n" + indent(cond, "  ")).orElse(""));

            Optional<RowOrdering> rowOrdering =
                ospec.getRowOrdering().isPresent() ? ospec.getRowOrdering()
                : sortUnsortedRowElementCollectionsByPk ? getPkRowOrdering(ospec) : Optional.empty();

            rowOrdering.ifPresent(ordering ->
                templateModel.put("orderByExprs", ordering.getOrderByExpressions(rowsQueryAlias))
            );

            String sql = applyTemplate(rowCollectionElementQueryTemplate, templateModel);

            if ( cacheGeneratedSqls )
                cachedSqlsByXdaQuery.put(xdaQry, sql);

            return sql;
        }
    }

    /** Return a single row whose rowcoll_xml column contains a forest of xml elements representing the rows of the indicated table for
     *  which ospec is the output specification.
     */
    public String getRowForestQuery
    (
        TableOutputSpec ospec,
        Optional<String> maybeRowsQueryAlias,
        Optional<String> filterCondOverRowsQuery
    )
    {
        String tableAlias = lowercaseInitials(ospec.getRelationId().getName(),"_");

        // Provide an alias for the FROM-clause row-elements subquery, as some databases such as Postgres require an alias.
        String rowsQueryAlias = maybeRowsQueryAlias.orElseGet(() -> tableAlias + "_row");

        XdaQuery xdaQry =
            new XdaQuery(
                ospec,
                XdaQuery.QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT,
                Optional.of(rowsQueryAlias),
                filterCondOverRowsQuery,
                OrderByClauseInclusion.NA,
                XmlOutputColumnType.XML_TYPE,
                OutputColumnsInclusion.XML_COLUMN_ONLY
            );

        Optional<String> cachedSql = cachedSql(xdaQry);

        if ( cachedSql.isPresent() )
            return cachedSql.get();
        else
        {
            String rowsQuery =
                getRowElementsQuery(
                    ospec,
                    tableAlias,
                    Optional.empty(),
                    OMIT_ORDERBY_CLAUSE,
                    XmlOutputColumnType.XML_TYPE,
                    OutputColumnsInclusion.ALL_FIELDS_THEN_ROW_XML // Export all TOS-included fields for possible use in WHERE condition over the rows query.
                );


            Map<String,Object> templateModel = new HashMap<>();
            templateModel.put("rowsQuery", indent(rowsQuery, "   ", false));
            templateModel.put("rowsQueryAlias", rowsQueryAlias);
            templateModel.put("whereCond", filterCondOverRowsQuery.map(cond -> "where\n" + indent(cond, "  ")).orElse(""));

            Optional<RowOrdering> rowOrdering =
                ospec.getRowOrdering().isPresent() ? ospec.getRowOrdering()
                : sortUnsortedRowElementCollectionsByPk ? getPkRowOrdering(ospec) : Optional.empty();

            rowOrdering.ifPresent(ordering ->
                templateModel.put("orderByExprs", ordering.getOrderByExpressions(rowsQueryAlias))
            );

            String sql = applyTemplate(rowForestQueryTemplate, templateModel);

            if ( cacheGeneratedSqls )
                cachedSqlsByXdaQuery.put(xdaQry, sql);

            return sql;
        }
    }


    private List<String> getChildTableSubqueries
    (
        TableOutputSpec parentOspec,
        String parentTableAlias,
        Optional<String> trailingLinesPrefix
    )
    {
        List<String> childTableSubqueries = new ArrayList<>();

        // Child tables
        for ( Pair<ForeignKey,TableOutputSpec> p: parentOspec.getChildOutputSpecsByFK() )
        {
            ForeignKey fk = p.fst();
            TableOutputSpec childOspec = p.snd();

            // Make sure the child's alias is chosen to be distinct from the parent's since they will be in the same namespace.
            String childRowElemsQueryAlias =
                makeNameNotInSet(
                    lowercaseInitials(childOspec.getRelationId().getName(),"_") + "_row",
                   Collections.singleton(parentTableAlias)
                );

            String childRowElemsQueryCond =
                fk.asEquation(
                    childRowElemsQueryAlias,
                    parentTableAlias,
                    EquationStyle.SOURCE_ON_LEFTHAND_SIDE
                );

            String childCollSubqry =
                getRowCollectionElementQuery(
                    childOspec,
                    Optional.of(childRowElemsQueryAlias),
                    Optional.of(childRowElemsQueryCond),
                    XmlOutputColumnType.XML_TYPE
                );


            if ( trailingLinesPrefix.isPresent() )
                childCollSubqry = indent(childCollSubqry, trailingLinesPrefix.get(), false);

            childTableSubqueries.add(childCollSubqry);
        }

        return childTableSubqueries;
    }


    private List<String> getParentTableSubqueries
    (
        TableOutputSpec childOspec,
        String childTableAlias,
        Optional<String> trailingLinesPrefix
    )
    {
        List<String> parentTableSubqueries = new ArrayList<>();

        // Parent tables
        for ( Pair<ForeignKey,TableOutputSpec> p: childOspec.getParentOutputSpecsByFK() )
        {
            ForeignKey fk = p.fst();
            TableOutputSpec parentOspec = p.snd();

            String parentTableAlias =
                makeNameNotInSet(
                    lowercaseInitials(parentOspec.getRelationId().getName(),"_"),
                    Collections.singleton(childTableAlias)
                );

            String parentRowsCond =
                fk.asEquation(
                    childTableAlias,
                    parentTableAlias,
                    EquationStyle.TARGET_ON_LEFTHAND_SIDE
                );

            String parentRowElsQuery =
                getRowElementsQuery(
                    parentOspec,
                    parentTableAlias,
                    Optional.of(parentRowsCond),
                    OMIT_ORDERBY_CLAUSE,
                    XmlOutputColumnType.XML_TYPE,
                    OutputColumnsInclusion.XML_COLUMN_ONLY
                );

            if ( trailingLinesPrefix.isPresent() )
                parentRowElsQuery = indent(parentRowElsQuery, trailingLinesPrefix.get(), false);

            parentTableSubqueries.add(parentRowElsQuery);
        }

        return parentTableSubqueries;
    }

    private Optional<String> cachedSql(XdaQuery xdaQry)
    {
        if ( cachedSqlsByXdaQuery.size() == 0 ) // Avoid potentially expensive hash code generation (due to TableOutputSpec) when cache is empty.
            return Optional.empty();
        else
            return Optional.ofNullable(cachedSqlsByXdaQuery.get(xdaQry));
    }

    private Optional<String> getXmlIndentationClause()
    {
        switch ( xmlIndentation )
        {
            case INDENT_UNSPECIFIED: return Optional.empty();
            case NO_INDENT: return Optional.of("no indent");
            default:
                return Optional.of("indent" + xmlIndentationSize.map(size -> " " + size).orElse(""));
        }
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

    private Optional<RowOrdering> getPkRowOrdering(TableOutputSpec ospec)
    {
        List<String> pkFieldNames = dbmd.getPrimaryKeyFieldNames(ospec.getRelationId());

        if ( pkFieldNames.size() > 0 )
            return Optional.of(RowOrdering.fields(pkFieldNames));
        else
            return Optional.empty();
    }

    private void initTemplates() throws IOException
    {
        Configuration conf = getTemplateConfig();

        // Load templates.
        this.rowElementsQueryTemplate = conf.getTemplate(ROWELEMENTSSQUERY_TEMPLATE_NAME);
        this.rowCollectionElementQueryTemplate = conf.getTemplate(ROWCOLLECTIONELEMENT_QUERY_TEMPLATE);
        this.rowForestQueryTemplate = conf.getTemplate(ROWFOREST_QUERY_TEMPLATE);
    }

    private static Configuration getTemplateConfig()
    {
        Configuration templateConfig = new Configuration(Freemarker.compatibilityVersion);
        templateConfig.setTemplateLoader(new ClassTemplateLoader(QueryGenerator.class, CLASSPATH_TEMPLATES_DIR_PATH));
        templateConfig.setObjectWrapper(new DefaultObjectWrapper(Freemarker.compatibilityVersion));
        return templateConfig;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException
    {
        ois.defaultReadObject();

        initTemplates();
    }


    public interface FieldElementContentExpressionGenerator
    {
        String getFieldElementContentExpression(Optional<String> tableAlias, Field f);
    }

    public static class DefaultFieldElementContentExpressionGenerator implements FieldElementContentExpressionGenerator
    {
        public String getFieldElementContentExpression(Optional<String> tableAlias, Field f)
        {

            String qFieldName = tableAlias.map(alias -> alias + ".").orElse("") + f.getName();

            switch ( f.getJdbcTypeCode() )
            {
                case Types.DATE:
                    return "TO_CHAR(" + qFieldName + ",'YYYY-MM-DD')";
                case Types.TIMESTAMP:
                    return "TO_CHAR(" + qFieldName + ",'YYYY-MM-DD\"T\"HH24:MI:SS')";
                default:
                    return qFieldName;
            }
        }
    }

    /** A class used to store the complete set of information necessary to generate any of the SQL queries generated by the QueryGenerator.
        This class is mainly used internally by the QueryGenerator for caching and for argument checking, but could be useful to clients
        wanting to store query variations in some cases.
    */
    public static class XdaQuery
    {
        private final TableOutputSpec ospec;
        private final QueryResultStyle queryResultStyle;
        private final Optional<String> tableAlias;
        private final Optional<String> filterCondition;
        private final OrderByClauseInclusion orderByClauseInclusion;
        private final XmlOutputColumnType xmlOutputColumnType;
        private final OutputColumnsInclusion outputColumnsInclusion;
        private final int hashCode;

        public enum QueryResultStyle
        {
            SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
            MULTIPLE_ROW_ELEMENT_RESULTS,
            SINGLE_ROW_ELEMENT_FOREST_RESULT
        }


        public XdaQuery(TableOutputSpec ospec)
        {
            this(ospec,
                 QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                 Optional.empty(),
                 Optional.empty(),
                 OrderByClauseInclusion.NA,
                 XmlOutputColumnType.LARGE_CHAR_TYPE, // TODO: move this argument up (and in other constructors), since there's not a good default in general.
                 OutputColumnsInclusion.XML_COLUMN_ONLY);
        }

        public XdaQuery
        (
            TableOutputSpec ospec,
            Optional<String> tableAlias,
            Optional<String> filterCond
        )
        {
            this(ospec,
                 QueryResultStyle.SINGLE_ROW_COLLECTION_ELEMENT_RESULT,
                 tableAlias,
                 filterCond,
                 OrderByClauseInclusion.NA,
                 XmlOutputColumnType.LARGE_CHAR_TYPE,
                 OutputColumnsInclusion.XML_COLUMN_ONLY);
        }


        public XdaQuery
        (
            TableOutputSpec ospec,
            QueryResultStyle queryResultStyle,
            Optional<String> tableAlias,
            Optional<String> filterCond
        )
        {
            this(ospec,
                 queryResultStyle,
                 tableAlias,
                 filterCond,
                 queryResultStyle != QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS ? OrderByClauseInclusion.NA : INCLUDE_ORDERBY_CLAUSE_IF_ORDERED,
                 XmlOutputColumnType.LARGE_CHAR_TYPE,
                 OutputColumnsInclusion.XML_COLUMN_ONLY);
        }

        public XdaQuery
        (
            TableOutputSpec ospec,
            QueryResultStyle queryResultStyle,
            Optional<String> tableAlias,
            Optional<String> filterCondition,
            OrderByClauseInclusion orderByIncl,
            XmlOutputColumnType xmlOutputColType,
            OutputColumnsInclusion outputColsOpt
        )
        {
            this.ospec = requireArg(ospec, "table output spec");
            this.queryResultStyle = requireArg(queryResultStyle, "query result style");
            this.tableAlias = tableAlias;
            this.filterCondition = filterCondition;
            this.orderByClauseInclusion = requireArg(orderByIncl, "order by clause inclusion option");
            this.xmlOutputColumnType = requireArg(xmlOutputColType, "xml output column type");
            this.outputColumnsInclusion = requireArg(outputColsOpt, "output columns option");
            this.hashCode = computeHashCode();

            // Check input arguments for compatibility.

            if ( queryResultStyle == QueryResultStyle.MULTIPLE_ROW_ELEMENT_RESULTS )
            {
                if ( tableAlias.map(String::isEmpty).orElse(true) )
                    throw new IllegalArgumentException("Queries of result style MULTIPLE_ROW_ELEMENT_RESULTS require a non-empty table alias.");

                if ( orderByClauseInclusion == OrderByClauseInclusion.NA )
                    throw new IllegalArgumentException("For row elements queries the orderByIncl argument cannot be NA.");
            }
            else // single result collection or forest style query
            {
                if ( queryResultStyle == QueryResultStyle.SINGLE_ROW_ELEMENT_FOREST_RESULT && xmlOutputColumnType == XmlOutputColumnType.LARGE_CHAR_TYPE )
                    throw new IllegalArgumentException("Queries of SINGLE_ROW_ELEMENT_FOREST_RESULT style cannot have a must have an XMLTYPE output column type.");

                if ( outputColumnsInclusion != OutputColumnsInclusion.XML_COLUMN_ONLY )
                    throw new IllegalArgumentException("Single-row collection and forest style queries require an output columns option of OutputColumnsInclusion.XML_COLUMN_ONLY");

                if ( orderByClauseInclusion != OrderByClauseInclusion.NA )
                    throw new IllegalArgumentException("For row collection or forest queries the orderByIncl argument must be NA, as it only applies to top level row elements queries.");
            }
        }

        public static XdaQuery xdaquery(TableOutputSpec tos)
        {
            return new XdaQuery(tos);
        }

        public static XdaQuery xdaquery
        (
            TableOutputSpec tos,
            Optional<String> tableAlias,
            Optional<String> filterCond
        )
        {
            return new XdaQuery(tos, tableAlias, filterCond);
        }


        public TableOutputSpec getTableOutputSpec()
        {
            return ospec;
        }

        public QueryResultStyle getQueryResultStyle()
        {
            return queryResultStyle;
        }

        public Optional<String> getTableAlias()
        {
            return tableAlias;
        }

        public Optional<String> getFilterCondition()
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

        @Override
        public int hashCode() { return hashCode; }

        private int computeHashCode()
        {
            return ospec.hashCode()
                 + hashcode(tableAlias)
                 + hashcode(filterCondition)
                 + orderByClauseInclusion.hashCode()
                 + queryResultStyle.hashCode()
                 + xmlOutputColumnType.hashCode()
                 + outputColumnsInclusion.hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            if ( !(o instanceof XdaQuery) )
                return false;

            XdaQuery q = (XdaQuery)o;

            if ( this == o )
                return true;
            else
                return ospec.equals(q.ospec)
                    && Objects.equals(tableAlias, q.tableAlias)
                    && Objects.equals(filterCondition, q.filterCondition)
                    && orderByClauseInclusion == q.orderByClauseInclusion
                    && queryResultStyle == q.queryResultStyle
                    && xmlOutputColumnType == q.xmlOutputColumnType
                    && outputColumnsInclusion == q.outputColumnsInclusion;
        }
    }


    public static void main(String[] args) throws Exception
    {
        if ( args.length != 4 )
            throw new IllegalArgumentException("Expected arguments: <table> <db-metadata-file> <el-collection-style:INLINE|WRAPPED> <query-output-file>");

        String tableName = args[0];
        String dbmdXmlPath = args[1];
        ChildCollectionsStyle childCollsStyle = ChildCollectionsStyle.valueOf(args[2].toUpperCase());
        String queryOutfilePath = args[3];

        try ( InputStream dbmdIs = new FileInputStream(dbmdXmlPath);
              OutputStream qryOs = new FileOutputStream(queryOutfilePath) )
        {
            DBMD dbmd = DBMD.readXML(dbmdIs);

            System.out.println("Database metadata for " + dbmd.getRelationMetaDatas().size() + " relations read from file.");

            QueryGenerator g = new QueryGenerator(dbmd, XmlOutputColumnType.LARGE_CHAR_TYPE);

            TableOutputSpec.Factory tosf =
                new DefaultTableOutputSpecFactory(
                    dbmd,
                    childCollsStyle,
                    "http://nctr.fda.gov/xdagen"
                );

            TableOutputSpec ospec =
                tosf.table(tableName)
                .withAllChildTables()
                .withAllParentTables();

            String sqlXmlQry = g.getRowElementsQuery(ospec);

            qryOs.write(sqlXmlQry.getBytes());
        }
    }
}
