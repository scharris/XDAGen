package gov.fda.nctr.xdagen;

import java.util.List;
import java.util.Set;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;

import static gov.fda.nctr.util.StringFunctions.stringFrom;


public class DefaultElementNamer implements ElementNamer {

	DBMD dbmd;
	
	XmlElementCollectionStyle xmlElCollStyle;
	

	public DefaultElementNamer(DBMD dbmd,
	                            XmlElementCollectionStyle collection_style)
	{
		this.dbmd = dbmd;
		this.xmlElCollStyle = collection_style;
	}
	
	
	@Override
	public String getDefaultRowElementName(RelId rel_id)
	{
		return rel_id.getName().toLowerCase();
	}
	
	@Override
	public String getDefaultRowCollectionElementName(RelId rel_id)
	{
		return rel_id.getName().toLowerCase() + "-listing";
	}


	/** Returns a simple name based on the child relation id alone, unless:
	 *  1) there are multiple fks from this child to parent, and
	 *  2) the set of fk field names has been specified.
	*/
	@Override
	public String getChildRowCollectionElementName(RelId child_rel_id,
	                                               RelId parent_rel_id,
	                                               Set<String> fk_field_names)
	{
		// Are there multiple fk's from this child to this parent?  If so, use a fully qualified name if we have enough information to (fk_field_names).
		List<ForeignKey> fks = dbmd.getForeignKeysFromTo(child_rel_id, parent_rel_id);
		
		if ( fks.size() <= 1 || fk_field_names == null )
		{
			return getDefaultRowCollectionElementName(child_rel_id);
		}
		else
		{
			ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fk_field_names, fks);
			
			if ( fk == null )
				throw new IllegalArgumentException("Foreign key with field set " + fk_field_names + " not found from " + 
				                                   child_rel_id + " to " + parent_rel_id + ".");
			
			return getdFullyQualifiedChildRowCollectionElementName(fk);
		}
	}

	/** Returns a simple name based on the child relation id alone, unless:
	 *  1) there are multiple fks from this child to parent, and
	 *  2) the set of fk field names has been specified, and
	 *  3) the xml collection style setting is inline (for wrapped child elements, 
	 *     the wrapper parent name has the job of disambiguating so the child name
	 *     can be simple).
	*/
	@Override
	public String getChildRowElementName(RelId child_rel_id,
	                                     RelId parent_rel_id,
	                                     Set<String> fk_field_names)
	{
		// Are there multiple fk's from this child to this parent?
		List<ForeignKey> fks = dbmd.getForeignKeysFromTo(child_rel_id, parent_rel_id);
		
		if ( xmlElCollStyle == XmlElementCollectionStyle.WRAPPED || fks.size() <= 1 || fk_field_names == null )
		{
			return getDefaultRowElementName(child_rel_id);
		}
		else
		{
			ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fk_field_names, fks);
			
			if ( fk == null )
				throw new IllegalArgumentException("Foreign key with field set " + fk_field_names + " not found from " + 
				                                   child_rel_id + " to " + parent_rel_id + ".");
			
			return getdFullyQualifiedChildRowCollectionElementName(fk);
		}
	}

	@Override
	public String getParentRowElementName(RelId child_rel_id, RelId parent_rel_id, Set<String> fk_field_names)
	{
		List<ForeignKey> fks = dbmd.getForeignKeysFromTo(child_rel_id, parent_rel_id);
		
		if ( fks.size() <= 1 || fk_field_names == null )
		{
			return getDefaultRowElementName(parent_rel_id);
		}
		else
		{
			ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fk_field_names, fks);
			
			if ( fk == null )
				throw new IllegalArgumentException("Foreign key with field set " + fk_field_names + " not found from " + 
				                                   child_rel_id + " to " + parent_rel_id + ".");
			
			return getFullyQualifiedParentRowElementName(fk);
		}

	}
	
	
	protected String getdFullyQualifiedChildRowCollectionElementName(ForeignKey fk)
	{
		return getDefaultRowCollectionElementName(fk.getSourceRelationId()) + "-from-" + stringFrom(fk.getSourceFieldNames(),"-");
	}

	protected String getFullyQualifiedParentRowElementName(ForeignKey fk)
	{
		return getDefaultRowElementName(fk.getTargetRelationId()) + "-via-" + stringFrom(fk.getSourceFieldNames(),"-");
	}
	
}
