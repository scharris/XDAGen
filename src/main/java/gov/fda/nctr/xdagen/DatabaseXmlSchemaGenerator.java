package gov.fda.nctr.xdagen;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Types;
import java.util.*;
import static java.util.Objects.requireNonNull;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

import static gov.fda.nctr.util.Freemarker.applyTemplate;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.RelMetaData;
import gov.fda.nctr.util.Freemarker;


public class DatabaseXmlSchemaGenerator
{
    private final DBMD dbmd;

    private final TypeNamer typeNamer;

    private final Template xsdTemplate;

    private final boolean includeGenerationTimestamp;

    private static final String xmlns = "http://nctr.fda.gov/xdagen";
    private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
    private static final String XMLSCHEMA_TEMPLATE =  "XMLSchema.ftl";


    public DatabaseXmlSchemaGenerator(DBMD dbmd)
        throws IOException
    {
        this(dbmd, Optional.of(new DefaultTypeNamer(dbmd)), false);
    }

    // Primary constructor.
    public DatabaseXmlSchemaGenerator
    (
        DBMD dbmd,
        Optional<TypeNamer> typeNamer,
        boolean includeGenerationTimestamp
    )
        throws IOException
    {
        requireNonNull(dbmd);
        requireNonNull(typeNamer);

        this.dbmd = dbmd;
        this.typeNamer = typeNamer.orElseGet(() -> new DefaultTypeNamer(dbmd));
        this.includeGenerationTimestamp = includeGenerationTimestamp;

        Configuration templateConfig = getTemplateConfiguration();
        this.xsdTemplate = templateConfig.getTemplate(XMLSCHEMA_TEMPLATE);
    }

    public String getStandardXMLSchema
    (
        String outputXmlNamespace,
        ChildCollectionsStyle childCollectionsStyle,
        Optional<Set<RelId>> topLevelElementRels,
        Optional<Set<RelId>> topLevelListElementRels
    )
    {
        DefaultTableOutputSpecFactory tosFactory =
            new DefaultTableOutputSpecFactory(
                dbmd,
                childCollectionsStyle,
                outputXmlNamespace
            );

        return getXMLSchema(
            tosFactory,
            topLevelElementRels,
            topLevelListElementRels
        );
    }


    public String getXMLSchema
    (
        TableOutputSpec.Factory outputSpecFactory,
        Optional<Set<RelId>> topLevelElementRels,
        Optional<Set<RelId>> topLevelListElementRels
    )
    {
        requireNonNull(outputSpecFactory);

        List<TableOutputSpec> ospecs = new ArrayList<>();

        for ( RelMetaData relmd: dbmd.getRelationMetaDatas() )
        {
            TableOutputSpec ospec =
                outputSpecFactory.table(relmd.getRelationId())
                .withAllChildTables()
                .withAllParentTables();

            ospecs.add(ospec);
        }

        return getXMLSchema(
            ospecs,
            topLevelElementRels,
            topLevelListElementRels,
            true,  // Part of being the "standard" XMLSchema for this relation is that each parent and child element is optional in the XMLSchema,
            true    // so the schema will match regardless of which parents/children are included in a particular query.
        );
    }


    public String getXMLSchema
    (
        List<TableOutputSpec> ospecs,
        Optional<Set<RelId>> topLevelElRels,
        Optional<Set<RelId>> topLevelListElRels,
        boolean childListElsOptional,  // whether child list elements should be optional (for schema reusability)
        boolean parentElsOptional      // whether parent elements     "
    )
    {
        requireNonNull(ospecs);

        Map<String,Object> templateModel = new HashMap<>();

        templateModel.put("qgen", this);
        templateModel.put("typeNamer", typeNamer);
        templateModel.put("targetNamespace", xmlns);
        templateModel.put("ospecs", ospecs);
        templateModel.put("toplevelElRels", topLevelElRels);
        templateModel.put("toplevelListElRels", topLevelListElRels);
        templateModel.put("childElsOpt", childListElsOptional);
        templateModel.put("parentElsOpt", parentElsOptional);
        templateModel.put("generatingProgram", getClass().getName());
        templateModel.put("generatedDate", includeGenerationTimestamp ? new java.util.Date() : null);

        return applyTemplate(xsdTemplate, templateModel);
    }

    public DBMD getDatabaseMetaData()
    {
        return dbmd;
    }

    // Returns the XML Schema simple type if any for the passed jdbc type code.  Will return null for complex types such as SQLXML (XMLTYPE) fields.
    public String getXmlSchemaSimpleTypeForJdbcTypeCode(int jdbcType)
    {
        switch (jdbcType)
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
            return null;
        }
    }

    private List<RelId> parseRelIds(String relIdsString)
    {
        requireNonNull(relIdsString);

        if (relIdsString.trim().isEmpty() )
            return Collections.emptyList();
        else
        {
            List<RelId> relIds = new ArrayList<>();

            String[] relIdStrs = relIdsString.split("\\s*,\\s*");

            for ( String relIdStr: relIdStrs )
            {
                RelId relId = dbmd.toRelId(relIdStr);

                relIds.add(relId);
            }

            return relIds;
        }
    }

    ///////////////////////////////////////////////////////////////
    // Type naming interface and default implementation.

    public interface TypeNamer
    {
        String getRowElementTypeName(RelId relId);

        String getRowCollectionElementTypeName(RelId relId);
    }

    public static class DefaultTypeNamer implements TypeNamer
    {
        private final DBMD dbmd;

        public DefaultTypeNamer(DBMD dbmd)
        {
            this.dbmd = dbmd;
        }

        @Override
        public String getRowElementTypeName(RelId relId)
        {
            boolean foundDupRelname = false;
            for ( RelMetaData rmd: dbmd.getRelationMetaDatas() )
            {
                if ( (!rmd.getRelationId().equals(relId)) &&
                     rmd.getRelationId().getName().equals(relId.getName()) )
                {
                    foundDupRelname = true;
                    break;
                }
            }

            if ( foundDupRelname )
                return relId.getIdString().toLowerCase();
            else
                return relId.getName().toLowerCase();
        }

        @Override
        public String getRowCollectionElementTypeName(RelId relId)
        {
            return getRowElementTypeName(relId) + "-listing";
        }
    }

    // Type naming interface and default implementation.
    ///////////////////////////////////////////////////////////////

    private static Configuration getTemplateConfiguration()
    {
        // Configure template engine.
        Configuration templateConfig = new Configuration(Freemarker.compatibilityVersion);
        templateConfig.setTemplateLoader(new ClassTemplateLoader(DatabaseXmlSchemaGenerator.class, CLASSPATH_TEMPLATES_DIR_PATH));
        templateConfig.setObjectWrapper(new DefaultObjectWrapper(Freemarker.compatibilityVersion));
        return templateConfig;
    }

    public static void main(String[] args) throws Exception
    {
        if ( args.length != 4 )
            throw new IllegalArgumentException("Expected arguments: <db-metadata-file> <target-namespace> <xml-collection-style:inline|wrapped> <toplevel-el-relids|*all*|*none*> <toplevel-el-list-relids|*all*|*none*> <xmlschema-output-file>");

        String dbmdXmlInfilePath = args[0];
        String outputXmlNamespace = args[1];
        ChildCollectionsStyle childCollectionsStyle = ChildCollectionsStyle.valueOf(args[2].toUpperCase());
        String toplevelElRelIdsStrList = args[3];
        String toplevelElListRelIdsStrList = args[4];
        String xmlSchemaOutfilePath = args[5];


        try ( InputStream dbmdIs = new FileInputStream(dbmdXmlInfilePath);
              OutputStream os = new FileOutputStream(xmlSchemaOutfilePath) )
        {
            DBMD dbmd = DBMD.readXML(dbmdIs);

            DatabaseXmlSchemaGenerator g = new DatabaseXmlSchemaGenerator(dbmd);

            Optional<Set<RelId>> toplevelElRelIds =
                toplevelElRelIdsStrList.equals("*all*") ? Optional.empty()
                : toplevelElRelIdsStrList.equals("*none*") ? Optional.of(new HashSet<>())
                : Optional.of(new HashSet<>(g.parseRelIds(toplevelElRelIdsStrList)));

            Optional<Set<RelId>> toplevelElListRelIds =
                toplevelElListRelIdsStrList.equals("*all*") ? Optional.empty()
                : toplevelElListRelIdsStrList.equals("*none*") ? Optional.of(new HashSet<>())
                : Optional.of(new HashSet<>(g.parseRelIds(toplevelElListRelIdsStrList)));

            String xsd = g.getStandardXMLSchema(
                outputXmlNamespace,
                childCollectionsStyle,
                toplevelElRelIds,
                toplevelElListRelIds
            );

            os.write(xsd.getBytes());
        }
    }
}
