package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.StringFunctions.stringFrom;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableOutputSpec {

	RelId relId;
	
    List<String> fields;
    
	List<Pair<ForeignKey,TableOutputSpec>> childSpecsByFK;
    
	List<Pair<ForeignKey,TableOutputSpec>> parentSpecsByFK;
	
	String rowCollectionElementName;
	
	String rowElementName;
	
	DBMD dbmd;
	

	/** Create an output spec with all the fields for the passed table/view included but no parents or children.
     If the table name is not qualified by schema, then the DBMD should have an owning schema specified, else
     database metadata may not be found for databases supporting schemas. */
	public TableOutputSpec(String pq_relname,
                           DBMD dbmd)
    {
    	this(dbmd.toRelId(pq_relname),
    	     dbmd);
    }
	

    public TableOutputSpec(RelId relID,
	                       DBMD dbmd)
	{
    	this(relID,
    	     dbmd,
    	     dbmd.getFieldNames(relID),
    	     null,
    	     null,
    	     null,
    	     null);
	}

	
    protected TableOutputSpec(RelId rel_id,
	                          DBMD db_md,
	                          List<String> included_fields, // opt
	                          List<Pair<ForeignKey,TableOutputSpec>> included_child_table_specs, // opt
	                          List<Pair<ForeignKey,TableOutputSpec>> included_parent_table_specs,// opt
	                          String row_collection_el_name, // opt
	                          String row_el_name) // opt
	{
		super();
		this.relId = rel_id;
		this.dbmd = db_md;
		this.fields = included_fields != null ? new ArrayList<String>(included_fields) : db_md.getFieldNames(rel_id); 
		this.childSpecsByFK = included_child_table_specs != null ? new ArrayList<Pair<ForeignKey,TableOutputSpec>>(included_child_table_specs)
				                                                          : new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		this.parentSpecsByFK = included_parent_table_specs != null ? new ArrayList<Pair<ForeignKey,TableOutputSpec>>(included_parent_table_specs) 
				                                                            : new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		this.rowCollectionElementName = row_collection_el_name != null ? row_collection_el_name : getDefaultRowCollectionElementName(rel_id);
		this.rowElementName = row_el_name != null ? row_el_name : getDefaultRowElementName(rel_id);
		
	}
    
	
	public RelId getRelationId()
	{
		return relId;
	}
	
	public List<String> getFields()
	{
		return fields;
	}
	
	public TableOutputSpec setFields(List<String> incl_fields)
	{
		if ( incl_fields == null )
			throw new IllegalArgumentException("List of fields to include cannot be null.");
		
		fields = incl_fields;
		return this;
	}
	
	
	public String getRowCollectionElementName()
	{
		return rowCollectionElementName;
	}
	
	public TableOutputSpec setRowCollectionElementName(String coll_el_name)
	{
		rowCollectionElementName = coll_el_name;
		return this;
	}
	
	
	public String getRowElementName()
	{
		return rowElementName;
	}

	public TableOutputSpec setRowElementName(String el_name)
	{
		rowElementName = el_name;
		return this;
	}

	

	/////////////////////////////////////////////////////////////////////////////////////
	// Retrieval of included child table specifications
	
	public List<Pair<ForeignKey,TableOutputSpec>> getChildOutputSpecsByFK()
	{
		return childSpecsByFK;
	}
	
	public List<TableOutputSpec> getChildOutputSpecs()
	{
		List<TableOutputSpec> ospecs = new ArrayList<TableOutputSpec>();
		
		for(Pair<ForeignKey,TableOutputSpec> fk_ospec_pair: childSpecsByFK)
			ospecs.add(fk_ospec_pair.snd());
		
		return ospecs;
	}
	
	
	public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForChild(String pq_child_rel_name)
	{
		return getOutputSpecsByFKForChild(dbmd.toRelId(pq_child_rel_name));
	}
	

	/** Obtains all of the current specifications for the indicated child table (which may be included multiple times
	    via differing foreign keys), or the empty list if the child table is not currently included. */
	public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForChild(RelId child_rel_id)
	{
		List<Pair<ForeignKey,TableOutputSpec>> child_specs = new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		
		for(Pair<ForeignKey,TableOutputSpec> fk_ospec_pair: childSpecsByFK)
		{
			if ( fk_ospec_pair.snd().getRelationId().equals(child_rel_id) )
				child_specs.add(fk_ospec_pair);
		}
		
		return child_specs;
	}
	
	
	public TableOutputSpec getOutputSpecForChild(String pq_child_rel_name)
	{
		return getOutputSpecForChild(dbmd.toRelId(pq_child_rel_name));
	}
	
	
	public TableOutputSpec getOutputSpecForChild(RelId child_rel_id)
	{
		List<Pair<ForeignKey,TableOutputSpec>> child_specs = getOutputSpecsByFKForChild(child_rel_id);
		
		if ( child_specs.size() == 0 )
			throw new IllegalArgumentException("Table " + child_rel_id + " has no foreign key links to " + relId + ".");
		else if ( child_specs.size() > 1 )
			throw new IllegalArgumentException("Child table " + child_rel_id + " has multiple links to " + relId + ".");
		else
			return child_specs.get(0).snd();
	}

	// Retrieval of included child table specifications
	/////////////////////////////////////////////////////////////////////////////////////
	
	

	/////////////////////////////////////////////////////////////////////////////////////
	// Retrieval of included parent table specifications
	
	public List<Pair<ForeignKey,TableOutputSpec>> getParentOutputSpecsByFK()
	{
		return parentSpecsByFK;
	}
	
	public List<TableOutputSpec> getParentOutputSpecs()
	{
		List<TableOutputSpec> ospecs = new ArrayList<TableOutputSpec>();
		
		for(Pair<ForeignKey,TableOutputSpec> fk_ospec_pair: parentSpecsByFK)
			ospecs.add(fk_ospec_pair.snd());
		
		return ospecs;
	}
	
	public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForParent(String pq_parent_rel_name)
	{
		return getOutputSpecsByFKForParent(dbmd.toRelId(pq_parent_rel_name));
	}
	

	/** Obtains all of the current specifications for the indicated parent tables (which may be included multiple times under differing foreign keys), or the empty list if the parent
	    table is not currently included. */
	public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForParent(RelId parent_rel_id)
	{
		List<Pair<ForeignKey,TableOutputSpec>> parent_specs = new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		
		for(Pair<ForeignKey,TableOutputSpec> fk_ospec_pair: parentSpecsByFK)
		{
			if ( fk_ospec_pair.snd().getRelationId().equals(parent_rel_id) )
				parent_specs.add(fk_ospec_pair);
		}
		
		return parent_specs;
	}
	
	public TableOutputSpec getOutputSpecForParent(String pq_parent_rel_name)
	{
		return getOutputSpecForParent(dbmd.toRelId(pq_parent_rel_name));
	}
	
	
	public TableOutputSpec getOutputSpecForParent(RelId parent_rel_id)
	{
		List<Pair<ForeignKey,TableOutputSpec>> parent_specs = getOutputSpecsByFKForParent(parent_rel_id);
		
		if ( parent_specs.size() == 0 )
			throw new IllegalArgumentException("Table " + relId + " has no foreign key links to " + parent_rel_id + ".");
		else if ( parent_specs.size() > 1 )
			throw new IllegalArgumentException("Parent table " + parent_rel_id + " has multiple foreign key links from " + relId + ".");
		else
			return parent_specs.get(0).snd();
	}

	// Retrieval of included parent table specifications
	/////////////////////////////////////////////////////////////////////////////////////
	

	
	////////////////////////////////////////////////////////////////////////////////////
	// Methods for including a child table in the output
	
	
	public TableOutputSpec addChild(RelId child_rel_id,
	                                Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
	                                TableOutputSpec child_output_spec)// Optional.
	{
		if ( child_output_spec == null )
			child_output_spec = new TableOutputSpec(child_rel_id, dbmd);

		final Set<String> normd_reqd_fk_field_names = normalizeNames(reqd_fk_field_names);


		ForeignKey sought_fk = null;
		for(ForeignKey fk: dbmd.getForeignKeysFromTo(child_rel_id, relId))
		{
			if ( normd_reqd_fk_field_names == null || fk.sourceFieldNamesSetEqualsNormalizedNamesSet(normd_reqd_fk_field_names) )
			{
				if ( sought_fk != null ) // already found an fk satisfying requirements?
					throw new IllegalArgumentException("Child table " + child_rel_id +
					                                   " has multiple foreign keys to parent table " + relId +
					                           	       ": the proper foreign key must be indicated.");

				sought_fk = fk;
				childSpecsByFK.add(Pair.make(sought_fk, child_output_spec));

				// No breaking from the loop here, so case that multiple fk's satisfy requirements can be detected.
			}
		}

		if ( sought_fk == null )
			throw new IllegalArgumentException("No foreign key found from table " + child_rel_id + " to " + relId);

		return this;
	}


	/** Add a child table for inclusion in this table's output, with default output options.
	    The child table should have exactly one foreign key to this table, otherwise the function
	    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec addChild(String pq_child_rel_name)
	{
		if ( pq_child_rel_name == null || pq_child_rel_name.length() == 0 )
			throw new IllegalArgumentException("Child table name is required.");
		
		TableOutputSpec child_output_spec = new TableOutputSpec(pq_child_rel_name, dbmd);
		
		return addChild(child_output_spec.getRelationId(),
		                null, // fk field names unspecified
		                child_output_spec);
	}
	
	public TableOutputSpec addChild(String pq_child_rel_name, // possibly qualified table or view name
	                                Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this child table reference this parent.
	{
		return addChild(dbmd.toRelId(pq_child_rel_name),
		                reqd_fk_field_names,
		                new TableOutputSpec(pq_child_rel_name, dbmd));
	}

	
	/** Add a child table for inclusion in this table's output with specified output options.
	    The child table should have exactly one foreign key to this table, otherwise the function
	    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec addChild(TableOutputSpec child_output_spec)
	{
		if ( child_output_spec == null )
			throw new IllegalArgumentException("Child table output specification is required.");
		
		return addChild(child_output_spec.getRelationId(),
		                null, // specific fk fields unspecified
		                child_output_spec);
	}
	
	public TableOutputSpec addChild(TableOutputSpec child_output_spec,
	                                Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this child table reference this parent.
	{
		if ( child_output_spec == null )
			throw new IllegalArgumentException("Child table output specification is required.");
		
		return addChild(child_output_spec.getRelationId(),
		                reqd_fk_field_names,
		                child_output_spec);
	}
	
	/** Convenience method to include all child tables with default options and naming.  
	 *  The included table output specifications will have default options, so they will
	 *  not themselves specify any child or parent tables in their own output.  An
	 *  individual table specification added in this manner can be customized by retrieving
	 *  it via one of the getSpec* methods and modifying it. */
	public TableOutputSpec addAllChildTables()
	{
		// Find the child tables with multiple foreign keys to this table, because they will need properly qualified collection element names.
		Set<RelId> multiply_referencing_child_rels = dbmd.getMultiplyReferencingChildTablesForParent(this.relId);
		
		for(ForeignKey fk: dbmd.getForeignKeysFromChildrenTo(this.relId))
		{
			RelId child_rel_id = fk.getSourceRelationId();
			
			if ( dbmd.getRelationMetaData(child_rel_id) != null ) // ignore tables for which we have no metadata
			{
				TableOutputSpec child_ospec = new TableOutputSpec(child_rel_id, dbmd);
			
				String coll_el_name = multiply_referencing_child_rels.contains(child_rel_id) ?
						getDefaultFullyQualifiedChildRowCollectionElementName(fk)
					  : getDefaultRowCollectionElementName(child_rel_id);
			
						child_ospec.setRowCollectionElementName(coll_el_name);
			
						Set<String> fk_fieldnames = new HashSet<String>(fk.getSourceFieldNames());
			
						addChild(child_rel_id, fk_fieldnames, child_ospec);
			}
		}
		
		return this;
	}
	
	// Methods for including a child table in the output
	////////////////////////////////////////////////////////////////////////////////////


	////////////////////////////////////////////////////////////////////////////////////
	// Methods for including a parent table in the output
	
	public TableOutputSpec addParent(RelId parent_rel_id,
	                                 Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this table reference the parent.
	                                 TableOutputSpec parent_output_spec)// Optional.
	{
		if ( parent_output_spec == null )
			parent_output_spec = new TableOutputSpec(parent_rel_id, dbmd);

		final Set<String> normd_reqd_fk_field_names = normalizeNames(reqd_fk_field_names);

		ForeignKey sought_fk = null;
		for(ForeignKey fk: dbmd.getForeignKeysFromTo(relId, parent_rel_id))
		{
			if ( normd_reqd_fk_field_names == null || fk.sourceFieldNamesSetEqualsNormalizedNamesSet(normd_reqd_fk_field_names) )
			{
				if ( sought_fk != null ) // already found an fk satisfying requirements?
					throw new IllegalArgumentException("Parent table " + parent_rel_id +
					                                   " has multiple foreign keys from child table " + relId +
						                               ": the proper foreign key must be indicated.");

				sought_fk = fk;
				parentSpecsByFK.add(Pair.make(sought_fk, parent_output_spec));

				// No breaking from the loop here, so case that multiple fk's satisfy requirements can be detected.
			}
		}

		if ( sought_fk == null )
			throw new IllegalArgumentException("No foreign key found from table " + parent_rel_id + " to " + relId);

		return this;
	}
	
	/** Add a parent table for inclusion in this table's output, with default output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec addParent(String pq_parent_rel_name)
	{
		if ( pq_parent_rel_name == null || pq_parent_rel_name.length() == 0 )
			throw new IllegalArgumentException("Parent table name is required.");
	
		TableOutputSpec parent_output_spec = new TableOutputSpec(pq_parent_rel_name, dbmd);
	
		return addParent(parent_output_spec.getRelationId(),
		                 null, // fk field names unspecified
		                 parent_output_spec);
	}

	public TableOutputSpec addParent(String pq_parent_rel_name, // possibly qualified table or view name
	                                 Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this table reference this parent.
	{
		return addParent(dbmd.toRelId(pq_parent_rel_name),
		                 reqd_fk_field_names,
		                 new TableOutputSpec(pq_parent_rel_name, dbmd));
	}


	/** Add a parent table for inclusion in this table's output with specified output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec addParent(TableOutputSpec parent_output_spec)
	{
		if ( parent_output_spec == null )
			throw new IllegalArgumentException("Parent table output specification is required.");
	
		return addParent(parent_output_spec.getRelationId(),
		                 null, // specific fk fields unspecified
		                 parent_output_spec);
	}

	public TableOutputSpec addParent(TableOutputSpec parent_output_spec,
	                                 Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this table reference this parent.
	{
		if ( parent_output_spec == null )
			throw new IllegalArgumentException("Parent table output specification is required.");
	
		return addParent(parent_output_spec.getRelationId(),
		                 reqd_fk_field_names,
		                 parent_output_spec);
	}
	
	/** Convenience method to include all parent tables with default options and naming.  
	 *  The included table output specifications will have default options, so they will
	 *  not themselves specify any child or parent tables in their own output.  An
	 *  individual table specification added in this manner can be customized by retrieving
	 *  it via one of the getSpec* methods and modifying it. */
	public TableOutputSpec addAllParentTables()
	{
		// Find the child tables with multiple foreign keys to this table, because they will need properly qualified collection element names.
		Set<RelId> multiply_referenced_parent_rels = dbmd.getMultiplyReferencedParentTablesForChild(this.relId);
		
		for(ForeignKey fk: dbmd.getForeignKeysToParentsFrom(this.relId))
		{
			RelId parent_rel_id = fk.getTargetRelationId();
			
			if ( dbmd.getRelationMetaData(parent_rel_id) != null ) // ignore tables for which we have no metadata
			{
				TableOutputSpec parent_ospec = new TableOutputSpec(parent_rel_id, dbmd);
			
				String parent_el_name = multiply_referenced_parent_rels.contains(parent_rel_id) ?
						getDefaultFullyQualifiedParentRowElementName(fk)
					  : getDefaultRowElementName(parent_rel_id);
			
				parent_ospec.setRowElementName(parent_el_name);
			
				Set<String> fk_fieldnames = new HashSet<String>(fk.getSourceFieldNames());
			
				addParent(parent_rel_id, fk_fieldnames, parent_ospec);
			}
		}
		
		return this;
	}
	
	// Methods for including a parent table in the output
	////////////////////////////////////////////////////////////////////////////////////
	


	//////////////////////////////////////////////////////////////////////////////////////////
	// Default element naming
	
	public static String getDefaultRowCollectionElementName(RelId rel_id)
	{
		return rel_id.getName().toLowerCase() + "-list";
	}
	
	public static String getDefaultRowElementName(RelId rel_id)
	{
		return rel_id.getName().toLowerCase();
	}
	
	public static String getDefaultFullyQualifiedChildRowCollectionElementName(ForeignKey fk)
	{
		return getDefaultRowCollectionElementName(fk.getSourceRelationId()) + "-from-" + stringFrom(fk.getSourceFieldNames(),"-");
	}
	
	public static String getDefaultFullyQualifiedParentRowElementName(ForeignKey fk)
	{
		return getDefaultRowElementName(fk.getTargetRelationId()) + "-via-" + stringFrom(fk.getSourceFieldNames(),"-");
	}
	
	// Default element naming
	//////////////////////////////////////////////////////////////////////////////////////////
	
	
    // Miscellaneous utilities
	
	public Set<String> normalizeNames(Set<String> names)
	{
		if ( names == null )
			return null;
		else
		{
			final Set<String> normd_names = new HashSet<String>();
		
			for(String name: names)
				normd_names.add(dbmd.normalizeDatabaseId(name));
		
			return normd_names;
		}
	}
	
	
	// A couple of factory functions for calling the 2-arg constructor, mainly for avoiding the new syntax in expressions.
	
	public static TableOutputSpec table(String pq_table_name, DBMD dbmd)
	{
		return new TableOutputSpec(pq_table_name, dbmd);
	}

	public static TableOutputSpec rel(String pq_table_name, DBMD dbmd)
	{
		return new TableOutputSpec(pq_table_name, dbmd);
	}


}
