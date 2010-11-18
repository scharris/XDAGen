select xmlagg(<#if rows_query_alias??>${rows_query_alias}.</#if>row_xml) "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias!""}
${where_cond}