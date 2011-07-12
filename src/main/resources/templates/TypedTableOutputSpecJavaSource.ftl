package ${target_package};

import java.util.Set;
import java.util.HashSet;
import java.util.List;

import gov.fda.nctr.xdagen.TableOutputSpec;
import gov.fda.nctr.xdagen.ChildCollectionsStyle;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.dbmd.ForeignKey;


<#assign class_name = namer.getTypedTableOutputSpecClassName(relid)/>

public class ${class_name} extends TableOutputSpec {

  public ${class_name}(DBMD dbmd, TableOutputSpec.Factory tos_factory, ChildCollectionsStyle child_colls_style, String xml_namespace)
  {
    super(new RelId(<#if relid.catalog??>"${relid.catalog}"<#else>null</#if>,<#rt>
                    <#if relid.schema??>"${relid.schema}"<#else>null</#if>,<#t>
                    <#if relid.name??>"${relid.name}"<#else>null</#if>)<#lt>,
          dbmd,
          tos_factory,
          child_colls_style,
          xml_namespace);
  }

<#list fks_from_child_tables as fk_from_child>
  <#assign method_name = namer.getChildAdditionMethodName(fk_from_child)/><#t>
  <#assign child_relid = fk_from_child.sourceRelationId/><#t>
  <#assign child_ospec_class_name = namer.getTypedTableOutputSpecClassName(child_relid)/><#t>
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
    return ${method_name}(new ${child_ospec_class_name}(this.dbmd, this.factory, this.childCollsStyle, this.outputXmlNamespace));
  }
</#list>


<#list fks_to_parent_tables as fk_to_parent>
  <#assign method_name = namer.getParentAdditionMethodName(fk_to_parent)/><#t>
  <#assign parent_relid = fk_to_parent.targetRelationId/><#t>
  <#assign parent_ospec_class_name = namer.getTypedTableOutputSpecClassName(parent_relid)/><#t>
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
  public ${class_name} withChild(RelId child_relid,               // Required
                                 Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
                                 TableOutputSpec child_output_spec)
  {
    return (${class_name})super.withChild(child_relid, reqd_fk_field_names, child_output_spec);
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
  public ${class_name} withParent(RelId parent_relid,               // Required
                                  Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
                                  TableOutputSpec parent_output_spec)
  {
    return (${class_name})super.withParent(parent_relid, reqd_fk_field_names, parent_output_spec);
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
  public ${class_name} outputFields(List<TableOutputSpec.OutputField> output_fields)
  {
    return (${class_name})super.outputFields(output_fields);
  }

  @Override
  public ${class_name} suppressOutputForFields(Set<String> db_field_names)
  {
    return (${class_name})super.suppressOutputForFields(db_field_names);
  }

  @Override
  public ${class_name} suppressOutputForFields(String... db_field_names)
  {
    return (${class_name})super.suppressOutputForFields(db_field_names);
  }


  @Override
  public ${class_name} suppressOutputForFieldsOtherThan(Set<String> db_field_names)
  {
    return (${class_name})super.suppressOutputForFieldsOtherThan(db_field_names);
  }

  @Override
  public ${class_name} suppressOutputForFieldsOtherThan(String... db_field_names)
  {
    return (${class_name})super.suppressOutputForFieldsOtherThan(db_field_names);
  }

  @Override
  public ${class_name} outputFieldAs(String db_field_name, String output_el_name)
  {
    return (${class_name})super.outputFieldAs(db_field_name, output_el_name);
  }


  @Override
  public ${class_name} orderedBy(TableOutputSpec.RowOrdering row_ordering)
  {
    return (${class_name})super.orderedBy(row_ordering);
  }

  @Override
  public ${class_name} factory(TableOutputSpec.Factory f)
  {
      return (${class_name})super.factory(f);
  }

  @Override
  public ${class_name} outputXmlNamespace(String ns)
  {
      return (${class_name})super.outputXmlNamespace(ns);
  }

  @Override
  public ${class_name} childCollectionsStyle(ChildCollectionsStyle child_colls_style)
  {
      return (${class_name})super.childCollectionsStyle(child_colls_style);
  }
}
