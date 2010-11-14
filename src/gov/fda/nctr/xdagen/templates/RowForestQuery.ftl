select xmlagg(${rows_query_alias}.row_xml) "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias}
${where_cond}