package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.StringFuns.lc;
import static gov.fda.nctr.util.StringFuns.stringFrom;
import static gov.fda.nctr.xdagen.ChildCollectionsStyle.INLINE;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class DefaultTableOutputSpecFactory implements TableOutputSpec.Factory {

	DBMD dbmd;
	ElementNamer elNamer;
	String outputXmlNamespace;
	ChildCollectionsStyle childCollsStyle;

	
	public DefaultTableOutputSpecFactory(DBMD dbmd,                                               // Required
	                                     ChildCollectionsStyle child_colls_style,                 // Required
	                                     String output_xml_namespace)                             // Optional (null for no namespace)
	{
		this(dbmd, 
		     child_colls_style,
		     new DefaultElementNamer(dbmd, child_colls_style),
		     output_xml_namespace);
	}
	
	public DefaultTableOutputSpecFactory(DBMD dbmd,                                               // Required
	                                     ChildCollectionsStyle child_colls_style,                 // Required
	                                     ElementNamer el_namer,                                   // Required
	                                     String output_xml_namespace)                             // Optional (null for no namespace)
	{
		this.dbmd = dbmd;
		this.childCollsStyle = child_colls_style;
		this.elNamer = el_namer;
		this.outputXmlNamespace = output_xml_namespace;
	}

	
	@Override
	public TableOutputSpec table(RelId relid)
	{
		return new TableOutputSpec(relid,
		                           dbmd,
		                           this,
		                           childCollsStyle,
		                           outputXmlNamespace,
		                           elNamer.getDefaultRowElementName(relid),
		                           elNamer.getDefaultRowCollectionElementName(relid));
	}
	
	@Override
	public TableOutputSpec table(String pq_table_name)
	{
		return table(dbmd.toRelId(pq_table_name));
	}
	
	
	@Override
	public TableOutputSpec makeChildTableOutputSpec(ForeignKey fk_from_child, TableOutputSpec within_ospec)
	{
		RelId child_relid = fk_from_child.getSourceRelationId();
		RelId within_relid = within_ospec.getRelationId();
		
		Set<String> fk_field_names = new HashSet<String>(fk_from_child.getSourceFieldNames());

		String row_el_name = elNamer.getChildRowElementNameWithinParent(child_relid, within_relid, fk_field_names);
		String row_coll_el_name = elNamer.getChildRowCollectionElementNameWithinParent(child_relid, within_relid, fk_field_names);
		
		return new TableOutputSpec(child_relid,
		                           dbmd,
		                           this,
		                           childCollsStyle,
		                           outputXmlNamespace,
		                           row_el_name,
		                           row_coll_el_name);
	}
	
	@Override
	public TableOutputSpec makeParentTableOutputSpec(ForeignKey fk_to_parent, TableOutputSpec within_ospec)
	{
		RelId parent_relid = fk_to_parent.getTargetRelationId();
		RelId within_relid = within_ospec.getRelationId();
		
		Set<String> fk_field_names = new HashSet<String>(fk_to_parent.getSourceFieldNames());
		
		String row_el_name = elNamer.getParentRowElementNameWithinChild(within_relid, parent_relid, fk_field_names);

		return new TableOutputSpec(parent_relid,
		                           dbmd,
		                           this,
		                           childCollsStyle,
		                           outputXmlNamespace,
		                           row_el_name,
		                           null); // no collection element name necessary for parent spec

	}
	
	public interface ElementNamer {
		
		public String getDefaultRowElementName(RelId rel_id);
		
		public String getDefaultRowCollectionElementName(RelId rel_id);

		public String getChildRowElementNameWithinParent(RelId child_rel_id,
		                                     RelId parent_rel_id,
		                                     Set<String> reqd_fk_field_names); // optional, set of child fk field names used to disambiguate fk's
		
		public String getChildRowCollectionElementNameWithinParent(RelId child_rel_id,
		                                                           RelId parent_rel_id,
		                                                           Set<String> reqd_fk_field_names); // optional
		
		public String getParentRowElementNameWithinChild(RelId child_rel_id,
		                                                 RelId parent_rel_id,
		                                                 Set<String> reqd_fk_field_names); // optional
		
	}
	
	
	public static class DefaultElementNamer implements ElementNamer {

		DBMD dbmd;
		
		ChildCollectionsStyle childCollsStyle;
		

		public DefaultElementNamer(DBMD dbmd,
		                           ChildCollectionsStyle collection_style)
		{
			this.dbmd = dbmd;
			this.childCollsStyle = collection_style;
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
		public String getChildRowCollectionElementNameWithinParent(RelId child_rel_id,
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
				
				return getFullyQualifiedChildRowCollectionElementNameWithinParent(fk);
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
		public String getChildRowElementNameWithinParent(RelId child_rel_id,
		                                                 RelId parent_rel_id,
		                                                 Set<String> fk_field_names)
		{
			if ( childCollsStyle == ChildCollectionsStyle.WRAPPED ) // Wrapped child row elements don't need qualified names.
				return getDefaultRowElementName(child_rel_id);
			else  // INLINE (child-) collection elements case
			{
				// Are there multiple fk's from this child to this parent?
				// If so this one will need to be distinguished with a qualified name.
				List<ForeignKey> child_fks_to_parent = dbmd.getForeignKeysFromTo(child_rel_id, parent_rel_id);
			
				// Is this child table also a parent of the parent table? 
				// If so, this child table's child elements in the parent will need to be distinguished from the possible parent entries.
				List<ForeignKey> parent_fks_to_child = dbmd.getForeignKeysFromTo(parent_rel_id, child_rel_id); // yes, parent->child on purpose here!
			
				boolean need_qualified_name = child_fks_to_parent.size() > 1 || parent_fks_to_child.size() > 0;
			
				boolean can_form_qualified_name = fk_field_names != null;
			
				if ( need_qualified_name && can_form_qualified_name )
				{
					// We find the actual foreign key so we can get a predictable properly ordered list of the foreign key names when forming the names.
					ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fk_field_names, child_fks_to_parent);
				
					if ( fk == null )
						throw new IllegalArgumentException("Foreign key with field set " + fk_field_names + " not found from " + 
						                                   child_rel_id + " to " + parent_rel_id + ".");
					
					if ( child_fks_to_parent.size() == 1 ) // This is the only fk from this child to the parent, so we only need to distinguish this link from the parent links.
						return "child-" + getDefaultRowElementName(fk.getSourceRelationId());
					else
						return getFullyQualifiedChildRowElementNameWithinParent(fk);
				}
				else // Just make a simple unqualified name
				{
					return getDefaultRowElementName(child_rel_id);
				}
			}
		}

		
		@Override
		public String getParentRowElementNameWithinChild(RelId child_rel_id, RelId parent_rel_id, Set<String> fk_field_names)
		{
			// If this is a multiple parent for this child, we'll need a qualfied name.
			List<ForeignKey> child_fks_to_parent = dbmd.getForeignKeysFromTo(child_rel_id, parent_rel_id);
			
			// For the case of inline (child) collection elements, make sure the parent rel is not also a child of the child rel, which could also cause a name conflict.
			List<ForeignKey> parent_fks_to_child = childCollsStyle == INLINE ? dbmd.getForeignKeysFromTo(parent_rel_id, child_rel_id) // yes, parent->child on purpose here!
					                                                         : null;
			
			boolean need_qualified_name = child_fks_to_parent.size() > 1 ||
			                              (childCollsStyle == INLINE && parent_fks_to_child.size() > 0);
			
			boolean can_form_qualified_name = fk_field_names != null;
			
			if ( need_qualified_name && can_form_qualified_name )
			{
				ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fk_field_names, child_fks_to_parent);
				
				if ( fk == null )
					throw new IllegalArgumentException("Foreign key with field set " + fk_field_names + " not found from " + 
					                                   child_rel_id + " to " + parent_rel_id + ".");

				if ( child_fks_to_parent.size() == 1 ) // There aren't multiple fks to this parent from the child, so just distinguish it from the children.
					return "parent-" + getDefaultRowElementName(fk.getTargetRelationId());
				else
					return getFullyQualifiedParentRowElementNameWithinChild(fk);
			}
			else // Just make a simple unqualified name.
			{
				return getDefaultRowElementName(parent_rel_id);
			}

		}
		
		
		public String getFullyQualifiedChildRowCollectionElementNameWithinParent(ForeignKey fk)
		{
			return getDefaultRowCollectionElementName(fk.getSourceRelationId()) + "-from-" + stringFrom(lc(fk.getSourceFieldNames()),"-");
		}
		
		public String getFullyQualifiedChildRowElementNameWithinParent(ForeignKey fk)
		{
			return getDefaultRowElementName(fk.getSourceRelationId()) + "-child-referencing-via-" + stringFrom(lc(fk.getSourceFieldNames()),"-");
		}

		public String getFullyQualifiedParentRowElementNameWithinChild(ForeignKey fk)
		{
			return getDefaultRowElementName(fk.getTargetRelationId()) + "-parent-referenced-via-" + stringFrom(lc(fk.getSourceFieldNames()),"-");
		}
		
	}
}