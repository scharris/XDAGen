package gov.fda.nctr.xdagen;

import java.util.Set;

import gov.fda.nctr.dbmd.RelId;

public interface ElementNamer {
	
	public String getDefaultRowElementName(RelId rel_id);
	
	public String getDefaultRowCollectionElementName(RelId rel_id);

	public String getChildRowElementName(RelId child_rel_id,
	                                     RelId parent_rel_id,
	                                     Set<String> reqd_fk_field_names); // optional, set of child fk field names used to disambiguate fk's
	
	public String getChildRowCollectionElementName(RelId child_rel_id,
	                                               RelId parent_rel_id,
	                                               Set<String> reqd_fk_field_names); // optional
	
	public String getParentRowElementName(RelId child_rel_id,
	                                      RelId parent_rel_id,
	                                      Set<String> reqd_fk_field_names); // optional
	
}
