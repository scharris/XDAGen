package ${targetPackage};

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.DefaultTableOutputSpecFactory;
import gov.fda.nctr.dbmd.DBMD;

<#assign class_name = namer.prototypesClassName/>

public class ${class_name} {

  public static final String XML_NAMESPACE = "${xdagenOutputXmlNamespace}";

  public static final ChildCollectionsStyle CHILD_COLLECTIONS_STYLE = ${childCollectionsStyle};

<#list relIds as relId>
  public static ${namer.getTypedTableOutputSpecClassName(relId)} ${namer.getPrototypeMemberName(relId)};
</#list>

  public static class Initializer {

    public Initializer(DBMD dbmd,
                       TableOutputSpec.Factory tos_factory)
    {
      synchronized(initialized)
      {
        if ( !initialized )
        {
          <#list relIds as relId>
          ${namer.getPrototypeMemberName(relId)} = new ${namer.getTypedTableOutputSpecClassName(relId)}(dbmd, tos_factory, CHILD_COLLECTIONS_STYLE, XML_NAMESPACE);
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
