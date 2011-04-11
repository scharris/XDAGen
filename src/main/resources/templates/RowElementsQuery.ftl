<#assign field_prefix = table_alias + "." />
<#assign write_xmlns_attr = !xmlns_is_default/>
select -- rows of ${relid}
<#if include_table_field_columns>  ${table_alias}.*,${"\n"}</#if>  -- row_xml
  <#if convert_to_clob>xmlserialize(content </#if>xmlelement(name "${row_element_name}"<#if write_xmlns_attr>, xmlattributes('${xmlns!}' as "xmlns")</#if>
   ,xmlforest(
     <#list output_fields as of>
     ${field_el_content_expr_gen.getFieldElementContentExpression(table_alias,of.field)} as "${of.outputElementName}"${of_has_next?string(',','')}
     </#list>
    )
   -- <#if (child_subqueries!)?size == 0>No</#if> child tables for ${relid}
   <#list child_subqueries! as child_subquery>
   ,(${child_subquery}
    ) -- child subquery
   </#list>
   -- <#if (parent_subqueries!)?size == 0>No</#if> parent tables for ${relid}
   <#list parent_subqueries! as parent_subquery>
   ,(${parent_subquery}
    ) -- parent subquery
   </#list>
  )<#if convert_to_clob> as clob)</#if> row_xml
from ${relid.idString} ${table_alias}<#if ((filter_condition!"")?length > 0)>
where
  ${filter_condition}</#if><#if (order_by_exprs!)?size != 0>
order by <#list order_by_exprs as expr>${expr}${expr_has_next?string(',','')}</#list><#t>
</#if>