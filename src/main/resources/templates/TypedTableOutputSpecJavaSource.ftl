package ${targetPackage};

import java.util.Set;
import java.util.HashSet;
import java.util.List;

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.ForeignKey;


<#assign class_name = namer.getTypedTableOutputSpecClassName(relId)/>

public class ${class_name} extends TableOutputSpec {

  public ${class_name}(DBMD dbmd, TableOutputSpec.Factory tos_factory, ChildCollectionsStyle child_colls_style, String xml_namespace)
  {
    super(new RelId(<#if relId.catalog??>"${relId.catalog}"<#else>null</#if>,<#rt>
                    <#if relId.schema??>"${relId.schema}"<#else>null</#if>,<#t>
                    <#if relId.name??>"${relId.name}"<#else>null</#if>)<#lt>,
          dbmd,
          tos_factory,
          child_colls_style,
          xml_namespace);
  }

<#list fksFromChildTables as fk_from_child>
  <#assign method_name = namer.getChildAdditionMethodName(fk_from_child)/><#t>
  <#assign childRelId = fk_from_child.sourceRelationId/><#t>
  <#assign child_ospec_class_name = namer.getTypedTableOutputSpecClassName(childRelId)/><#t>
  /** Add child table ${childRelId} with particular output specification. */
  public ${class_name} ${method_name}(${child_ospec_class_name} child_ospec)
  {
    Set<String> fk_field_names = new HashSet<String>();
    <#list fk_from_child.sourceFieldNames as fk_field_name>
    fk_field_names.add("${fk_field_name}");
    </#list>

    return (${class_name})
      super.withChild(new RelId(<#if childRelId.catalog??>"${childRelId.catalog}"<#else>null</#if>,<#rt>
                                <#if childRelId.schema??>"${childRelId.schema}"<#else>null</#if>,<#t>
                                <#if childRelId.name??>"${childRelId.name}"<#else>null</#if>),<#lt>
                      fk_field_names,
                      child_ospec);

  }

  /** Add child table ${childRelId} with default output specification. */
  public ${class_name} ${method_name}()
  {
    return ${method_name}(new ${child_ospec_class_name}(this.dbmd, this.factory, this.childCollsStyle, this.outputXmlNamespace));
  }
</#list>


<#list fksToParentTables as fk_to_parent>
  <#assign method_name = namer.getParentAdditionMethodName(fk_to_parent)/><#t>
  <#assign parentRelId = fk_to_parent.targetRelationId/><#t>
  <#assign parent_ospec_class_name = namer.getTypedTableOutputSpecClassName(parentRelId)/><#t>
  /** Add parent table ${parentRelId} with particular output specification. */
  public ${class_name} ${method_name}(${parent_ospec_class_name} parent_ospec)
  {
    Set<String> fk_field_names = new HashSet<String>();
    <#list fk_to_parent.sourceFieldNames as fk_field_name>
    fk_field_names.add("${fk_field_name}");
    </#list>

    return (${class_name})
      super.withParent(new RelId(<#if parentRelId.catalog??>"${parentRelId.catalog}"<#else>null</#if>,<#rt>
                                 <#if parentRelId.schema??>"${parentRelId.schema}"<#else>null</#if>,<#t>
                                 <#if parentRelId.name??>"${parentRelId.name}"<#else>null</#if>),<#lt>
                       fk_field_names,
                       parent_ospec);

  }

  /** Add parent table ${parentRelId} with default output specification. */
  public ${class_name} ${method_name}()
  {
    return ${method_name}(new ${parent_ospec_class_name}(this.dbmd, this.factory, this.childCollsStyle, this.outputXmlNamespace));
  }
</#list>


  // The rest of the methods just provide covariant return types (by casting) for the "with" methods which returned cloned and modified instances.

  @Override
  public ${class_name} withChild(ForeignKey fk_from_child,          // Required
                                 TableOutputSpec child_output_spec) // Optional
  {
    return (${class_name})super.withChild(fk_from_child, child_output_spec);
  }

  @Override
  public ${class_name} withChild(RelId childRelId,               // Required
                                 Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
                                 TableOutputSpec child_output_spec)
  {
    return (${class_name})super.withChild(childRelId, reqd_fk_field_names, child_output_spec);
  }

  @Override
  public ${class_name} withChild(String pq_child_rel_name)
  {
    return (${class_name})super.withChild(pq_child_rel_name);
  }

  @Override
  public ${class_name} withChild(String pq_child_rel_name,         // Required, possibly qualified table or view name.
                                 Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this child table reference this parent.
  {
    return (${class_name})super.withChild(pq_child_rel_name, reqd_fk_field_names);
  }

  @Override
  public ${class_name} withChild(TableOutputSpec child_output_spec) // Required
  {
    return (${class_name})super.withChild(child_output_spec);
  }

  @Override
  public ${class_name} withChild(TableOutputSpec child_output_spec, // Required
                                 Set<String> reqd_fk_field_names)   // Optional.  Required if multiple fk's from this child table reference this parent.
  {
    return (${class_name})super.withChild(child_output_spec, reqd_fk_field_names);
  }

  @Override
  public ${class_name} withAllChildTables()
  {
    return (${class_name})super.withAllChildTables();
  }

  @Override
  public ${class_name} withParent(ForeignKey fk_to_parent,            // Required
                                  TableOutputSpec parent_output_spec) // Optional
  {
    return (${class_name})super.withParent(fk_to_parent, parent_output_spec);
  }

  @Override
  public ${class_name} withParent(RelId parentRelId,               // Required
                                  Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
                                  TableOutputSpec parent_output_spec)
  {
    return (${class_name})super.withParent(parentRelId, reqd_fk_field_names, parent_output_spec);
  }

  @Override
  public ${class_name} withParent(String pq_parent_rel_name)
  {
    return (${class_name})super.withParent(pq_parent_rel_name);
  }

  @Override
  public ${class_name} withParent(String pq_parent_rel_name,         // Required, possibly qualified table or view name.
                                  Set<String> reqd_fk_field_names)   // Optional.  Required if multiple fk's from this child table reference this parent.
  {
    return (${class_name})super.withParent(pq_parent_rel_name, reqd_fk_field_names);
  }

  @Override
  public ${class_name} withParent(TableOutputSpec parent_output_spec) // Required
  {
    return (${class_name})super.withParent(parent_output_spec);
  }

  @Override
  public ${class_name} withParent(TableOutputSpec parent_output_spec, // Required
                                  Set<String> reqd_fk_field_names)    // Optional.  Required if multiple fk's from this child table reference this parent.
  {
    return (${class_name})super.withParent(parent_output_spec, reqd_fk_field_names);
  }

  @Override
  public ${class_name} withAllParentTables()
  {
    return (${class_name})super.withAllParentTables();
  }

  @Override
  public ${class_name} withFields(List<TableOutputSpec.OutputField> output_fields)
  {
    return (${class_name})super.withFields(output_fields);
  }

  @Override
  public ${class_name} withoutFields(String... db_field_names)
  {
    return (${class_name})super.withoutFields(db_field_names);
  }

  @Override
  public ${class_name} withoutFields(Set<String> db_field_names)
  {
    return (${class_name})super.withoutFields(db_field_names);
  }

  @Override
  public ${class_name} withoutFieldsOtherThan(String... db_field_names)
  {
    return (${class_name})super.withoutFieldsOtherThan(db_field_names);
  }

  @Override
  public ${class_name} withoutFieldsOtherThan(Set<String> db_field_names)
  {
    return (${class_name})super.withoutFieldsOtherThan(db_field_names);
  }

  @Override
  public ${class_name} withFieldAsElement(String db_field_name, String output_el_name)
  {
    return (${class_name})super.withFieldAsElement(db_field_name, output_el_name);
  }


  @Override
  public ${class_name} orderedBy(TableOutputSpec.RowOrdering row_ordering)
  {
    return (${class_name})super.orderedBy(row_ordering);
  }

  @Override
  public ${class_name} withFactory(TableOutputSpec.Factory f)
  {
      return (${class_name})super.withFactory(f);
  }

  @Override
  public ${class_name} withOutputXmlNamespace(String ns)
  {
      return (${class_name})super.withOutputXmlNamespace(ns);
  }

  @Override
  public ${class_name} withChildCollectionsStyle(ChildCollectionsStyle child_colls_style)
  {
      return (${class_name})super.withChildCollectionsStyle(child_colls_style);
  }
}
