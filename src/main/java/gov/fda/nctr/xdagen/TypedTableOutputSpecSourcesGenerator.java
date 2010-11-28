package gov.fda.nctr.xdagen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.RelMetaData;
import gov.fda.nctr.util.StringFuns;

// TODO: should allow setting a factory function to be called to create an ElementNamer in generated source, so DefaultElementNamer isn't the only possibility for the 1-arg constructor in the generated classes.

public class TypedTableOutputSpecSourcesGenerator {

	protected DBMD dbmd;
	
	protected String targetPackage; // Target package for generated classes.
	
	protected Namer namer; // Controls naming of the generated classes and their parent/child addition methods.
	
	protected File outputDir;
	
	protected Configuration templateConfig;
	protected Template classSourceFileTemplate;
	
	protected static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
	protected static final String JAVA_SOURCE_FILE_TEMPLATE =  "TypedTableOutputSpecJavaSource.ftl";

	// Primary constructor.
	public TypedTableOutputSpecSourcesGenerator(DBMD dbmd,
	                                            String target_java_package,
	                                            Namer namer)  // optional
		throws IOException
	{
		this.dbmd = dbmd;
		this.targetPackage = target_java_package;
		this.namer = namer != null ? namer : new DefaultNamer(dbmd);
		
		// Configure template engine.
		this.templateConfig = new Configuration();
		this.templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
		this.templateConfig.setObjectWrapper(new DefaultObjectWrapper());
		
		// Load templates.
		this.classSourceFileTemplate = templateConfig.getTemplate(JAVA_SOURCE_FILE_TEMPLATE);
	}
	
	
	public TypedTableOutputSpecSourcesGenerator(DBMD dbmd,
	                                            String target_java_package)
		throws IOException
	{
		this(dbmd,
		     target_java_package,
		     new DefaultNamer(dbmd));
	}
	
	
	public DBMD getDatabaseMetaData()
	{
		return dbmd;
	}
	
	public String getTargetPackage()
	{
		return targetPackage;
	}

	public Namer getNamer()
	{
		return namer;
	}
	
	public void setNamer(Namer namer)
	{
		this.namer = namer;
	}
	
	public void setOutputDirectory(File output_dir)
	{
		this.outputDir = output_dir;
	}
	
	public File getOutputDirectory()
	{
		return outputDir;
	}
	
	public String getJavaSource(RelId rel_id)
	{
		Map<String,Object> template_model = new HashMap<String,Object>();
		
		template_model.put("target_package", targetPackage);
		template_model.put("namer", namer);
		template_model.put("relid", rel_id);
		template_model.put("fks_from_child_tables", dbmd.getForeignKeysFromTo(null, rel_id, DBMD.ForeignKeyInclusion.REGISTERED_TABLES_ONLY));
		template_model.put("fks_to_parent_tables",  dbmd.getForeignKeysFromTo(rel_id, null, DBMD.ForeignKeyInclusion.REGISTERED_TABLES_ONLY));
		
		try
		{
			Writer sw = new StringWriter();
			
			classSourceFileTemplate.process(template_model, sw);
		
			return sw.toString();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException("Failed to produce typed table output spec source file from template: " + e.getMessage());
		}	
	}
	
	public void writeSourceFilesToOutputDir() throws IOException
	{
		writeSourceFilesTo(outputDir);
	}
	
	
	public void writeSourceFilesTo(File output_root_dir) throws IOException
	{
		if ( output_root_dir == null || !output_root_dir.isDirectory() )
			throw new IllegalArgumentException("Expected output directory, got <" + output_root_dir + ">.");
		
		String package_as_path = targetPackage.replace('.', File.separatorChar);

		File output_leaf_dir = new File(output_root_dir, package_as_path);
		output_leaf_dir.mkdirs();
		
		for(RelMetaData rmd: dbmd.getRelationMetaDatas())
		{
			RelId relid = rmd.getRelationId();
			
			String java_src = getJavaSource(relid);
			
			String file_name = namer.getGeneratedClassName(relid) + ".java";
			
			StringFuns.writeStringToFile(java_src,
			                             new File(output_leaf_dir, file_name));
		}
	}
	
	
	///////////////////////////////////////////////////////////////
	// Naming interface and default implementation.
	
	public static interface Namer {
		
		public String getGeneratedClassName(RelId rel_id);
		
		public String getChildAdditionMethodName(ForeignKey fk_from_child);
		
		public String getParentAdditionMethodName(ForeignKey fk_to_parent);
		
	}
	
	public static class DefaultNamer implements Namer {
		
		DBMD dbmd;
		
		public DefaultNamer(DBMD dbmd)
		{
			this.dbmd = dbmd;
		}
		
		@Override
		public String getGeneratedClassName(RelId rel_id)
		{
			return StringFuns.camelCase(rel_id.getName()) + "TableOutputSpec";
		}

		@Override
		public String getChildAdditionMethodName(ForeignKey fk_from_child)
		{
			// TODO: need to disambiguate if there are more than one fk from this child to this table.
			return "with" + StringFuns.camelCase(fk_from_child.getSourceRelationId().getName()) + "ChildTable";
		}
		
		@Override
		public String getParentAdditionMethodName(ForeignKey fk_to_parent)
		{
			// TODO: need to disambiguate if there are more than one fk from this child to this table.
			return "with" + StringFuns.camelCase(fk_to_parent.getTargetRelationId().getName()) + "ParentTable";
		}


	}
	
	// Naming interface and default implementation.
	///////////////////////////////////////////////////////////////
	
	
	
    public static void main(String[] args) throws Exception
    {
        int arg_ix = 0;
        String dbmd_xml_infile_path;
        String target_java_package;
        File output_dir;
    	
        if ( args.length == 3 )
        {
        	dbmd_xml_infile_path = args[arg_ix++];
        	target_java_package = args[arg_ix++];
        	output_dir = new File(args[arg_ix++]);
        	if ( !output_dir.isDirectory() )
        		throw new IllegalArgumentException("Output directory not found.");
        }
        else
        	throw new IllegalArgumentException("Expected arguments: <db-metadata-file> <target-java-package> <output-dir>");

        InputStream dbmd_is = new FileInputStream(dbmd_xml_infile_path);
        
        DBMD dbmd = DBMD.readXML(dbmd_is);
        dbmd_is.close();
        
        TypedTableOutputSpecSourcesGenerator g = new TypedTableOutputSpecSourcesGenerator(dbmd,
                                                                                          target_java_package);
        
        g.writeSourceFilesTo(output_dir);
        
        System.exit(0);
    }

}
