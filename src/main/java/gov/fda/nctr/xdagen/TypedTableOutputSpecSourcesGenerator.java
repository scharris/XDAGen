package gov.fda.nctr.xdagen;

import java.io.File;
import java.io.IOException;
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


public class TypedTableOutputSpecSourcesGenerator {

	DBMD dbmd;
	
	String targetPackage; // Target package for generated classes.
	
	Namer namer; // Controls naming of the generated classes and their parent/child addition methods.
	
	File outputDir;
	
	Configuration templateConfig;
	Template classSourceFileTemplate;
	
	private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
	private static final String JAVA_SOURCE_FILE_TEMPLATE =  "TypedTableOutputSpecJavaSource.ftl";

	
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
		template_model.put("fks_from_child_tables", dbmd.getForeignKeysFromChildrenTo(rel_id));
		template_model.put("fks_to_parent_tables",  dbmd.getForeignKeysToParentsFrom(rel_id));
		
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
	
	
	public void writeSourceFilesTo(File dir) throws IOException
	{
		if ( dir == null || !dir.isDirectory() )
			throw new IllegalArgumentException("Expected output directory, got <" + dir + ">.");
		
		for(RelMetaData rmd: dbmd.getRelationMetaDatas())
		{
			RelId relid = rmd.getRelationId();
			
			String java_src = getJavaSource(relid);
			
			String file_name = namer.getGeneratedClassName(relid) + ".java";
			
			StringFuns.writeStringToFile(java_src,
			                             new File(dir, file_name));
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
	
}
