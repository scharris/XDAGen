<#assign write_xmlns_attr = !xmlns_is_default/>
select <#if convert_to_clob>xmlserialize(content </#if>xmlelement("${row_collection_element_name}", <#if write_xmlns_attr>xmlattributes('${xmlns!}' as "xmlns"),</#if>
         xmlagg(<#if rows_query_alias??>${rows_query_alias}.</#if>row_xml<@orderby exprs=order_by_exprs!/>))<#if convert_to_clob> as clob)</#if> "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias!""}
${where_cond}<#rt>
<#macro orderby exprs><#if (exprs![])?size != 0> order by <#list exprs as expr>${expr}${expr_has_next?string(',','')}</#list></#if></#macro>