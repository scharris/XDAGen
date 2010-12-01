package ${target_package};

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.dbmd.DBMD;

<#assign class_name = namer.prototypesClassName/>

public class ${class_name} {
  
<#list relids as relid>
  public static ${namer.getTypedTableOutputSpecClassName(relid)} ${namer.getPrototypeMemberName(relid)};
</#list>

  public static class Initializer {
    public Initializer(DBMD dbmd, TableOutputSpec.Factory tos_factory)
    {
      synchronized(initialized)
      {
        if ( !initialized )
        {
        	<#list relids as relid>
          ${namer.getPrototypeMemberName(relid)} = new ${namer.getTypedTableOutputSpecClassName(relid)}(dbmd, tos_factory);
					</#list>
        	
          initialized = true;
        }
      }
    }
  }
 
  static Boolean initialized = new Boolean(false);
}
