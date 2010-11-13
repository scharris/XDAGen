select xmlelement("${row_collection_element_name}", <#if xmlns??>, xmlattributes('${xmlns}' as "xmlns")</#if>
         xmlagg(${rows_query_alias}.row_xml)) as "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias}
${where_cond}