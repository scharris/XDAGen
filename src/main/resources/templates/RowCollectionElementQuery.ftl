select <#if convert_to_clob>xmlserialize(content </#if>xmlelement("${row_collection_element_name}", <#if xmlns??>xmlattributes('${xmlns}' as "xmlns"),</#if>
         xmlagg(<#if rows_query_alias??>${rows_query_alias}.</#if>row_xml))<#if convert_to_clob> as clob)</#if> "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias!""}
${where_cond}