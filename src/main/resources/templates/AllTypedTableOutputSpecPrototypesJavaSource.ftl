package ${target_package};

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.DefaultTableOutputSpecFactory;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.dbmd.DBMD;

<#assign class_name = namer.prototypesClassName/>

public class ${class_name} {
  
<#list relids as relid>
  public static ${namer.getTypedTableOutputSpecClassName(relid)} ${namer.getPrototypeMemberName(relid)};
</#list>

  public static class Initializer {

    public Initializer(DBMD dbmd,
                       TableOutputSpec.Factory tos_factory,
                       ChildCollectionsStyle child_colls_style,
                       String xml_namespace)
    {
      synchronized(initialized)
      {
        if ( !initialized )
        {
          <#list relids as relid>
          ${namer.getPrototypeMemberName(relid)} = new ${namer.getTypedTableOutputSpecClassName(relid)}(dbmd, tos_factory, child_colls_style, xml_namespace);
          </#list>
          
          initialized = true;
        }
      }
    }
    
    public Initializer(DBMD dbmd,
                       ChildCollectionsStyle child_colls_style,
                       String xml_namespace)
    {
  	  this(dbmd,
  	       new DefaultTableOutputSpecFactory(dbmd, child_colls_style, xml_namespace),
  	       child_colls_style,
  	       xml_namespace);
    }
    
  }
 
  static Boolean initialized = new Boolean(false);
}
