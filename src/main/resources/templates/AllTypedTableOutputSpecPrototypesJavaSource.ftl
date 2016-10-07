package ${target_package};

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.DefaultTableOutputSpecFactory;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.dbmd.DBMD;

<#assign class_name = namer.prototypesClassName/>

public class ${class_name} {

  public static final String XML_NAMESPACE = "${xdagen_output_xml_namespace}";

  public static final ChildCollectionsStyle CHILD_COLLECTIONS_STYLE = ${child_collections_style};

<#list relids as relid>
  public static ${namer.getTypedTableOutputSpecClassName(relid)} ${namer.getPrototypeMemberName(relid)};
</#list>

  public static class Initializer {

    public Initializer(DBMD dbmd,
                       TableOutputSpec.Factory tos_factory)
    {
      synchronized(initialized)
      {
        if ( !initialized )
        {
          <#list relids as relid>
          ${namer.getPrototypeMemberName(relid)} = new ${namer.getTypedTableOutputSpecClassName(relid)}(dbmd, tos_factory, CHILD_COLLECTIONS_STYLE, XML_NAMESPACE);
          </#list>

          initialized = true;
        }
      }
    }

    public Initializer(DBMD dbmd)
    {
      this(dbmd,
           new DefaultTableOutputSpecFactory(dbmd, CHILD_COLLECTIONS_STYLE, XML_NAMESPACE));
    }

  }

  static Boolean initialized = new Boolean(false);
}
