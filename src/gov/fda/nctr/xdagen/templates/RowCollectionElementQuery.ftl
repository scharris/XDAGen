select xmlelement("${row_collection_element_name}", xmlagg(${rows_query_alias}.row_xml)) as "rowcoll_xml"
from
 ( ${rows_query}
 ) ${rows_query_alias}
${where_cond}