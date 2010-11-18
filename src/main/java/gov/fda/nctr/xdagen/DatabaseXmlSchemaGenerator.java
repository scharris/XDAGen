package gov.fda.nctr.xdagen;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.DatabaseMetaDataFetcher;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.RelMetaData;


public class DatabaseXmlSchemaGenerator {

	DBMD dbmd;
	
	String targetXmlNamespace;
	
	XmlElementCollectionStyle xmlElementCollectionStyle;
	
	TypeNamer typeNamer;
	
	
	Configuration templateConfig;
	Template wrappedCollectionsXSDTemplate;
	Template xsdTemplate;

	boolean includeGenerationTimestamp;

	
	private static final String CLASSPATH_TEMPLATES_DIR_PATH = "/templates";
	private static final String XMLSCHEMA_TEMPLATE =  "XMLSchema.ftl";

	
	// Primary constructor.
	public DatabaseXmlSchemaGenerator(DBMD dbmd,
	                                  String target_xml_namespace,
	                                  XmlElementCollectionStyle xml_el_coll_style,
	                                  TypeNamer type_namer)  // optional
		throws IOException
	{
		this.dbmd = dbmd;
		this.targetXmlNamespace = target_xml_namespace;
		this.xmlElementCollectionStyle = xml_el_coll_style;
		this.typeNamer = type_namer != null ? type_namer : new DefaultTypeNamer(dbmd);
		
		// Configure template engine.
		this.templateConfig = new Configuration();
		this.templateConfig.setTemplateLoader(new ClassTemplateLoader(getClass(), CLASSPATH_TEMPLATES_DIR_PATH));
		this.templateConfig.setObjectWrapper(new DefaultObjectWrapper());
		
		// Load templates.
		this.xsdTemplate =  templateConfig.getTemplate(XMLSCHEMA_TEMPLATE);
		
		this.includeGenerationTimestamp = false;
	}
	
	
	public DatabaseXmlSchemaGenerator(DBMD dbmd,
	                                  String target_xml_namespace,
	                                  XmlElementCollectionStyle xml_el_coll_style)
		throws IOException
	{
		this(dbmd,
		     target_xml_namespace,
		     xml_el_coll_style,
		     new DefaultTypeNamer(dbmd));
	}
	
	
	public DBMD getDbmd()
	{
		return dbmd;
	}
	
	public String getTargetXmlNamespace()
	{
		return targetXmlNamespace;
	}

	public void setTargetXmlNamespace(String targetXmlNamespace)
	{
		this.targetXmlNamespace = targetXmlNamespace;
	}


	public TypeNamer getTypeNamer()
	{
		return typeNamer;
	}
	
	public void setTypeNamer(TypeNamer type_namer)
	{
		this.typeNamer = type_namer;
	}
	
	public XmlElementCollectionStyle getXmlElementCollectionStyle()
	{
		return xmlElementCollectionStyle;
	}
	
	public void setXmlElementCollectionStyle(XmlElementCollectionStyle coll_style)
	{
		this.xmlElementCollectionStyle = coll_style;
	}

	public boolean getIncludeGenerationTimestamp()
	{
		return includeGenerationTimestamp;
	}


	
	public void setIncludeGenerationTimestamp(boolean includeGenerationTimestamp)
	{
		this.includeGenerationTimestamp = includeGenerationTimestamp;
	}

	
	public String getStandardXMLSchema(Set<RelId> rels_getting_toplevel_el,      // define top level elements for these
	                                   Set<RelId> rels_getting_toplevel_list_el,
	                                   ElementNamer el_namer)
	{
		List<TableOutputSpec> ospecs = new ArrayList<TableOutputSpec>();
		
		for(RelMetaData relmd: dbmd.getRelationMetaDatas())
		{
			TableOutputSpec ospec = new TableOutputSpec(relmd.getRelationId(), dbmd, el_namer);
		
			ospec.addAllChildTables();
			ospec.addAllParentTables();
			
			ospecs.add(ospec);
		}
		
		return getXMLSchema(ospecs,
		                    rels_getting_toplevel_el,
		                    rels_getting_toplevel_list_el,
		                    true,  // Part of being the "standard" XMLSchema for this relation is that each parent and child element is optional in the XMLSchema, 
		                    true); // so the schema will match regardless of which parents/children are included in a particular query.
	}

	

	public String getXMLSchema(List<TableOutputSpec> ospecs,
	                           Set<RelId> toplevel_el_rels,      // define top level elements for these, or for all if null.
	                           Set<RelId> toplevel_list_el_rels, // define top level list elements for these, or for all if null.
	                           boolean child_list_els_optional,  // whether child list elements should be optional (for schema reusability)
	                           boolean parent_els_optional)      // whether parent elements     "
	{
		Map<String,Object> template_model = new HashMap<String,Object>();
		
		template_model.put("qgen", this);
		template_model.put("inline_el_collections", xmlElementCollectionStyle == XmlElementCollectionStyle.INLINE);
		template_model.put("typeNamer", typeNamer);
		template_model.put("target_namespace", targetXmlNamespace);
		template_model.put("ospecs", ospecs);
		template_model.put("toplevel_el_rels", toplevel_el_rels);
		template_model.put("toplevel_list_el_rels", toplevel_list_el_rels);
		template_model.put("child_els_opt", child_list_els_optional);
		template_model.put("parent_els_opt", parent_els_optional);
		template_model.put("generating_program", getClass().getName());
		template_model.put("generated_date", includeGenerationTimestamp ? new java.util.Date() : null);
		
		try
		{
			Writer sw = new StringWriter();
			
			xsdTemplate.process(template_model, sw);
		
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
    
	private List<RelId> parseRelIds(String relids_strlist)
	{
		if ( relids_strlist == null || relids_strlist.trim().length() == 0 )
			return Collections.emptyList();
		else
		{
			List<RelId> relids = new ArrayList<RelId>();
			
			String[] relid_strs = relids_strlist.split("\\s*,\\s*");
			
			for(String relid_str: relid_strs)
			{
				RelId relid = dbmd.toRelId(relid_str);
				
				relids.add(relid);
			}
			
			return relids;
		}
	}
	
	///////////////////////////////////////////////////////////////
	// Type naming interface and default implementation.
	
	public static interface TypeNamer {
		
		public String getRowElementTypeName(RelId rel_id);
		
		public String getRowCollectionElementTypeName(RelId rel_id);
		
	}
	
	public static class DefaultTypeNamer implements TypeNamer {
		
		DBMD dbmd;
		
		public DefaultTypeNamer(DBMD dbmd)
		{
			this.dbmd = dbmd;
		}

		@Override
		public String getRowElementTypeName(RelId rel_id)
		{
			boolean found_dup_relname = false;
			for(RelMetaData rmd: dbmd.getRelationMetaDatas())
			{
				if ( (!rmd.getRelationId().equals(rel_id)) &&
					 rmd.getRelationId().getName().equals(rel_id.getName()) )
				{
					found_dup_relname = true;
					break;
				}
			}
			
			if ( found_dup_relname )
				return rel_id.getIdString().toLowerCase();
			else
				return rel_id.getName().toLowerCase();
		}

		@Override
		public String getRowCollectionElementTypeName(RelId rel_id)
		{
			return getRowElementTypeName(rel_id) + "-listing";
		}
	}
	
	// Type naming interface and default implementation.
	///////////////////////////////////////////////////////////////
	
	
    public static void main(String[] args) throws Exception
    {
        int arg_ix = 0;
        String dbmd_xml_infile_path;
        String target_namespace;
        XmlElementCollectionStyle xml_collection_style;
        String toplevel_el_relids_strlist;
        Set<RelId> toplevel_el_relids;
        String toplevel_el_list_relids_strlist;
        Set<RelId> toplevel_el_list_relids;
        String xmlschema_outfile_path;
    	
        if ( args.length == 6 )
        {
        	dbmd_xml_infile_path = args[arg_ix++];
        	target_namespace = args[arg_ix++];
        	xml_collection_style = XmlElementCollectionStyle.valueOf(args[arg_ix++].toUpperCase());
        	toplevel_el_relids_strlist = args[arg_ix++];
        	toplevel_el_list_relids_strlist = args[arg_ix++];
        	xmlschema_outfile_path = args[arg_ix++];
        	
        	
        	if ( toplevel_el_relids_strlist.trim().equals("*all*") )
        		toplevel_el_relids_strlist = null; // let it default
        	else if ( toplevel_el_relids_strlist.equals("*none*") )
        		toplevel_el_relids_strlist = "";
        	
        	if ( toplevel_el_list_relids_strlist.trim().equals("*all*") )
        		toplevel_el_list_relids_strlist = null; // let it default
        	else if ( toplevel_el_list_relids_strlist.equals("*none*") )
        			toplevel_el_list_relids_strlist = "";
        }
        else
        	throw new IllegalArgumentException("Expected arguments: <db-metadata-file> <target-namespace> <xml-collection-style:inline|wrapped> <toplevel-el-relids|*all*|*none*> <toplevel-el-list-relids|*all*|*none*> <xmlschema-output-file>");

        InputStream dbmd_is = new FileInputStream(dbmd_xml_infile_path);
        
        DBMD dbmd = DBMD.readXML(dbmd_is);
        dbmd_is.close();
        
        DatabaseXmlSchemaGenerator g = new DatabaseXmlSchemaGenerator(dbmd,
                                                                      target_namespace,
                                                                      xml_collection_style);

        toplevel_el_relids = toplevel_el_relids_strlist != null ? new HashSet<RelId>(g.parseRelIds(toplevel_el_relids_strlist)) : null;
        toplevel_el_list_relids = toplevel_el_list_relids_strlist != null ? new HashSet<RelId>(g.parseRelIds(toplevel_el_list_relids_strlist)) : null;
        
        ElementNamer el_namer = new DefaultElementNamer(dbmd, xml_collection_style);
        
        String xsd = g.getStandardXMLSchema(toplevel_el_relids,
                                            toplevel_el_list_relids,
                                            el_namer);
        
        OutputStream os = new FileOutputStream(xmlschema_outfile_path);
        os.write(xsd.getBytes());
        os.close();
        
        System.exit(0);
    }
   
}
