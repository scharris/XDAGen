select xmlagg(<#if rows_query_alias??>${rows_query_alias}.</#if>row_xml<@orderby exprs=order_by_exprs!/>) "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias!""}
${where_cond}<#rt>
<#macro orderby exprs><#if (exprs![])?size != 0> order by <#list exprs as expr>${expr}${expr_has_next?string(',','')}</#list></#if></#macro>