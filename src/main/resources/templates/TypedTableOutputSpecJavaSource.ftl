package ${target_package};

import java.util.Set;
import java.util.HashSet;

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.ElementNamer;
import gov.fda.nctr.xdagen.DefaultElementNamer;
import gov.fda.nctr.xdagen.XmlElementCollectionStyle;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;


<#assign class_name = namer.getGeneratedClassName(relid)/>

public class ${class_name} extends TableOutputSpec {

  public ${class_name}(DBMD dbmd, ElementNamer el_namer)
  {
    super(new RelId(<#if relid.catalog??>"${relid.catalog}"<#else>null</#if>,<#rt>
                    <#if relid.schema??>"${relid.schema}"<#else>null</#if>,<#t>
                    <#if relid.name??>"${relid.name}"<#else>null</#if>)<#lt>,
          dbmd,
          el_namer);
  }
  
  public ${class_name}(DBMD dbmd)
  {
    this(dbmd,
         new DefaultElementNamer(dbmd, XmlElementCollectionStyle.INLINE));
  }
  
  
<#list fks_from_child_tables as fk_from_child>
  <#assign method_name = namer.getChildAdditionMethodName(fk_from_child)/><#t>
  <#assign child_relid = fk_from_child.sourceRelationId/><#t>
  <#assign child_ospec_class_name = namer.getGeneratedClassName(child_relid)/><#t>
  /** Add child table ${child_relid} with particular output specification. */
  public ${class_name} ${method_name}(${child_ospec_class_name} child_ospec)
  {
    Set<String> fk_field_names = new HashSet<String>();
    <#list fk_from_child.sourceFieldNames as fk_field_name>
    fk_field_names.add("${fk_field_name}");
    </#list>
    
    return (${class_name})
      super.withChild(new RelId(<#if child_relid.catalog??>"${child_relid.catalog}"<#else>null</#if>,<#rt>
                                <#if child_relid.schema??>"${child_relid.schema}"<#else>null</#if>,<#t>
                                <#if child_relid.name??>"${child_relid.name}"<#else>null</#if>),<#lt>
                      fk_field_names,
                      child_ospec);
                           
  }
  
  /** Add child table ${child_relid} with default output specification. */
  public ${class_name} ${method_name}()
  {
    return ${method_name}(new ${child_ospec_class_name}(this.dbmd, this.elementNamer));
  }
</#list>


<#list fks_to_parent_tables as fk_to_parent>
  <#assign method_name = namer.getParentAdditionMethodName(fk_to_parent)/><#t>
  <#assign parent_relid = fk_to_parent.targetRelationId/><#t>
  <#assign parent_ospec_class_name = namer.getGeneratedClassName(parent_relid)/><#t>
  /** Add parent table ${parent_relid} with particular output specification. */
  public ${class_name} ${method_name}(${parent_ospec_class_name} parent_ospec)
  {
    Set<String> fk_field_names = new HashSet<String>();
    <#list fk_to_parent.sourceFieldNames as fk_field_name>
    fk_field_names.add("${fk_field_name}");
    </#list>
    
    return (${class_name})
      super.withParent(new RelId(<#if parent_relid.catalog??>"${parent_relid.catalog}"<#else>null</#if>,<#rt>
                                 <#if parent_relid.schema??>"${parent_relid.schema}"<#else>null</#if>,<#t>
                                 <#if parent_relid.name??>"${parent_relid.name}"<#else>null</#if>),<#lt>
                       fk_field_names,
                       parent_ospec);
                           
  }
  
  /** Add parent table ${parent_relid} with default output specification. */
  public ${class_name} ${method_name}()
  {
    return ${method_name}(new ${parent_ospec_class_name}(this.dbmd, this.elementNamer));
  }
</#list>
}
