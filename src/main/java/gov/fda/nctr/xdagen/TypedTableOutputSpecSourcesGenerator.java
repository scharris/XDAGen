package gov.fda.nctr.xdagen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

import static gov.fda.nctr.util.Freemarker.applyTemplate;
import static gov.fda.nctr.util.StringFuns.camelCase;
import static gov.fda.nctr.util.StringFuns.camelCaseInitialLower;
import static gov.fda.nctr.util.StringFuns.lc;
import static gov.fda.nctr.util.StringFuns.stringFrom;
import static gov.fda.nctr.util.Files.writeStringToFile;
import gov.fda.nctr.util.Freemarker;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.RelMetaData;
import gov.fda.nctr.dbmd.DBMD.ForeignKeyScope;


public class TypedTableOutputSpecSourcesGenerator
{
    private final DBMD dbmd;

    private final String targetPackage; // Target package for generated classes.

    private final ChildCollectionsStyle childCollectionsStyle;

    private final TypedTableOutputSpecNamer typedTableOutputSpecNamer; // Controls naming of the generated classes and their parent/child addition methods.

    private final Template classSourceFileTemplate;
    private final Template prototypesSourceFileTemplate;

    private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
    private static final String JAVA_SOURCE_FILE_TEMPLATE =  "TypedTableOutputSpecJavaSource.ftl";
    private static final String PROTOTYPES_SOURCE_FILE_TEMPLATE =  "AllTypedTableOutputSpecPrototypesJavaSource.ftl";

    private static final String XDAGEN_OUTPUT_NAMESPACE = "http://nctr.fda.gov/xdagen";

    // Primary constructor.
    public TypedTableOutputSpecSourcesGenerator
    (
        DBMD dbmd,
        String targetJavaPackage,
        ChildCollectionsStyle childCollectionsStyle,
        Optional<TypedTableOutputSpecNamer> typedTableOutputSpecNamer
    )
        throws IOException
    {
        this.dbmd = dbmd;
        this.targetPackage = targetJavaPackage;
        this.childCollectionsStyle = childCollectionsStyle;
        this.typedTableOutputSpecNamer = typedTableOutputSpecNamer.orElseGet(() -> new DefaultTypedTableOutputSpecNamer(dbmd));

        // Configure template engine.
        Configuration templateConfig = new Configuration(Freemarker.compatibilityVersion);
        templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
        templateConfig.setObjectWrapper(new DefaultObjectWrapper(Freemarker.compatibilityVersion));

        // Load templates.
        this.classSourceFileTemplate = templateConfig.getTemplate(JAVA_SOURCE_FILE_TEMPLATE);
        this.prototypesSourceFileTemplate = templateConfig.getTemplate(PROTOTYPES_SOURCE_FILE_TEMPLATE);
    }


    public TypedTableOutputSpecSourcesGenerator
    (
        DBMD dbmd,
        String targetJavaPackage,
        ChildCollectionsStyle childCollectionsStyle
    )
        throws IOException
    {
        this(dbmd, targetJavaPackage, childCollectionsStyle, Optional.of(new DefaultTypedTableOutputSpecNamer(dbmd)));
    }


    public DBMD getDatabaseMetaData()
    {
        return dbmd;
    }

    public String getTargetPackage()
    {
        return targetPackage;
    }

    public TypedTableOutputSpecNamer getTypedTableOutputSpecNamer()
    {
        return typedTableOutputSpecNamer;
    }

    private String getJavaSource(RelId relId)
    {
        Map<String,Object> templateModel = new HashMap<>();

        templateModel.put("targetPackage", targetPackage);
        templateModel.put("namer", typedTableOutputSpecNamer);
        templateModel.put("relId", relId);
        templateModel.put("fksFromChildTables", dbmd.getForeignKeysFromTo(null, relId, DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY));
        templateModel.put("fksToParentTables",  dbmd.getForeignKeysFromTo(relId, null, DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY));

        return applyTemplate(classSourceFileTemplate, templateModel);
    }

    private String getPrototypesJavaSource()
    {
        Map<String,Object> templateModel = new HashMap<>();

        templateModel.put("targetPackage", targetPackage);
        templateModel.put("xdagenOutputXmlNamespace", XDAGEN_OUTPUT_NAMESPACE);
        templateModel.put("childCollectionsStyle", childCollectionsStyle);
        templateModel.put("namer", typedTableOutputSpecNamer);
        templateModel.put("relIds", dbmd.getRelationIds());

        return applyTemplate(prototypesSourceFileTemplate, templateModel);
    }

    public void writeSourceFiles(File outputRootDir) throws IOException
    {
        Objects.requireNonNull(outputRootDir);

        if ( !outputRootDir.isDirectory() )
            throw new IllegalArgumentException("Expected output directory, got <" + outputRootDir + ">.");

        String packageAsPath = targetPackage.replace('.', File.separatorChar);

        File outputLeafDir = new File(outputRootDir, packageAsPath);
        outputLeafDir.mkdirs();

        for ( RelMetaData rmd: dbmd.getRelationMetaDatas() )
        {
            RelId relId = rmd.getRelationId();

            String javaSrc = getJavaSource(relId);

            String fileName = typedTableOutputSpecNamer.getTypedTableOutputSpecClassName(relId) + ".java";

            writeStringToFile(javaSrc, new File(outputLeafDir, fileName));
        }

        writeStringToFile(
            getPrototypesJavaSource(),
            new File(outputLeafDir, typedTableOutputSpecNamer.getPrototypesClassName() + ".java")
        );
    }

    ///////////////////////////////////////////////////////////////
    // Naming interface and default implementation.

    public interface TypedTableOutputSpecNamer
    {
        String getTypedTableOutputSpecClassName(RelId relid);

        String getChildAdditionMethodName(ForeignKey fkFromChild);

        String getParentAdditionMethodName(ForeignKey fkToParent);

        String getPrototypesClassName();

        String getPrototypeMemberName(RelId relId);
    }

    public static class DefaultTypedTableOutputSpecNamer implements TypedTableOutputSpecNamer
    {
        private final DBMD dbmd;

        public DefaultTypedTableOutputSpecNamer(DBMD dbmd)
        {
            this.dbmd = dbmd;
        }

        @Override
        public String getTypedTableOutputSpecClassName(RelId relId)
        {
            return camelCase(relId.getName()) + "TableOutputSpec";
        }

        @Override
        public String getChildAdditionMethodName(ForeignKey fkFromChild)
        {
            List<ForeignKey> fks =
                dbmd.getForeignKeysFromTo(
                    fkFromChild.getSourceRelationId(),
                    fkFromChild.getTargetRelationId(),
                    ForeignKeyScope.REGISTERED_TABLES_ONLY
                );

            RelId relToAdd = fkFromChild.getSourceRelationId();

            if ( fks.size() <= 1 ) // Only one fk from this child to this parent exists: use simple name.
            {
                return getSimpleChildAdditionMethodName(relToAdd);
            }
            else // Multiple fks from this child to this parent exist: use properly qualified name to avoid name collision.
            {
                return getSimpleChildAdditionMethodName(relToAdd) +
                       "ReferencingVia" + camelCase(stringFrom(lc(fkFromChild.getSourceFieldNames()),"_and_"));
            }
        }

        @Override
        public String getParentAdditionMethodName(ForeignKey fkToParent)
        {
            List<ForeignKey> fks =
                dbmd.getForeignKeysFromTo(
                    fkToParent.getSourceRelationId(),
                    fkToParent.getTargetRelationId(),
                    ForeignKeyScope.REGISTERED_TABLES_ONLY
                );

            RelId relToAdd = fkToParent.getTargetRelationId();

            if ( fks.size() <= 1 ) // Only one fk from this child to this parent exists: use simple name.
            {
                return getSimpleParentAdditionMethodName(relToAdd);
            }
            else // Multiple fks from this child to this parent exist: use properly qualified name to avoid name collision.
            {
                return
                    getSimpleParentAdditionMethodName(relToAdd) +
                    "ReferencedVia" +
                    camelCase(stringFrom(lc(fkToParent.getSourceFieldNames()),"_and_"));
            }
        }

        @Override
        public String getPrototypesClassName()
        {
            return "Prototypes";
        }

        @Override
        public String getPrototypeMemberName(RelId relId)
        {
            return camelCaseInitialLower(relId.getName());
        }


        private String getSimpleChildAdditionMethodName(RelId childRelId)
        {
            return "with" + camelCase(childRelId.getName()) + "List";
        }

        private String getSimpleParentAdditionMethodName(RelId parentRelId)
        {
            return "with" + camelCase(parentRelId.getName());
        }
    }

    // Naming interface and default implementation.
    ///////////////////////////////////////////////////////////////


    public static void main(String[] args) throws Exception
    {
        if ( args.length != 4 )
            throw new IllegalArgumentException("Expected arguments: <db-metadata-file> <target-java-package> <child-collections-style> <output-dir>");

        String dbmdXmlInfilePath = args[0];
        String targetJavaPackage = args[1];
        ChildCollectionsStyle childCollectionsStyle = ChildCollectionsStyle.valueOf(args[2]);
        File outputDir = new File(args[3]);

        if ( !outputDir.isDirectory() )
            throw new IllegalArgumentException("Output directory not found.");

        try ( InputStream dbmdIs = new FileInputStream(dbmdXmlInfilePath) )
        {
            DBMD dbmd = DBMD.readXML(dbmdIs);

            TypedTableOutputSpecSourcesGenerator g =
                new TypedTableOutputSpecSourcesGenerator(dbmd, targetJavaPackage, childCollectionsStyle);

            g.writeSourceFiles(outputDir);
        }
    }
}
