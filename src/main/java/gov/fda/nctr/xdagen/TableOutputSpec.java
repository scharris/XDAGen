package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.ListUtils.snoc;
import static gov.fda.nctr.util.MiscFuns.eqOrNull;
import static gov.fda.nctr.util.MiscFuns.hashcode;
import static gov.fda.nctr.util.MiscFuns.requireArg;
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
	
    List<String> includedFields;
    
	List<Pair<ForeignKey,TableOutputSpec>> childSpecsByFK;
    
	List<Pair<ForeignKey,TableOutputSpec>> parentSpecsByFK;
	
	String rowCollectionElementName;
	
	String rowElementName;
	
	DBMD dbmd;
	
	ElementNamer elementNamer;
	
	public Integer hashCode;
	
	
	/** Create an output spec with all the includedFields for the passed table/view included but no parents or children.
     If the table name is not qualified by schema, then the DBMD should have an owning schema specified, else
     database metadata may not be found for databases supporting schemas. */
	public TableOutputSpec(String pq_relname,
                           DBMD dbmd,
                           ElementNamer el_namer)
    {
    	this(dbmd.toRelId(pq_relname),
    	     dbmd,
    	     el_namer);
    }
	

    public TableOutputSpec(RelId relID,
	                       DBMD dbmd,
	                       ElementNamer el_namer)
	{
    	this(relID,
    	     dbmd,
    	     el_namer,
    	     dbmd.getFieldNames(relID),
    	     null,
    	     null,
    	     null,
    	     null);
	}

    public TableOutputSpec(RelId relID,
	                       DBMD dbmd,
	                       ElementNamer el_namer,
	                       String row_el_name,
	                       String row_collection_el_name)
	{
    	this(relID,
    	     dbmd,
    	     el_namer,
    	     dbmd.getFieldNames(relID),
    	     row_el_name,
    	     row_collection_el_name,
    	     null,
    	     null);
	}

	
    protected TableOutputSpec(RelId rel_id,
	                          DBMD db_md,
	                          ElementNamer el_namer,
	                          List<String> included_fields, // opt
	                          String row_el_name, // opt
	                          String row_collection_el_name, // opt
	                          List<Pair<ForeignKey,TableOutputSpec>> included_child_table_specs,  // opt
	                          List<Pair<ForeignKey,TableOutputSpec>> included_parent_table_specs) // opt
	{
		super();
		this.relId = requireArg(rel_id, "relation id");
		this.dbmd = requireArg(db_md, "database metadata");
		this.elementNamer = requireArg(el_namer, "element namer");
		this.includedFields = included_fields != null ? new ArrayList<String>(included_fields) : db_md.getFieldNames(rel_id);
		this.rowElementName = row_el_name != null ? row_el_name : el_namer.getDefaultRowElementName(rel_id);
		this.rowCollectionElementName = row_collection_el_name != null ? row_collection_el_name : el_namer.getDefaultRowCollectionElementName(rel_id);		
		this.childSpecsByFK = included_child_table_specs != null ? new ArrayList<Pair<ForeignKey,TableOutputSpec>>(included_child_table_specs)
				                                                 : new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		this.parentSpecsByFK = included_parent_table_specs != null ? new ArrayList<Pair<ForeignKey,TableOutputSpec>>(included_parent_table_specs) 
				                                                   : new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		
		hashCode = null;
	}
    
	
	public RelId getRelationId()
	{
		return relId;
	}
	
	public List<String> getFields()
	{
		return includedFields;
	}
	
	public String getRowCollectionElementName()
	{
		return rowCollectionElementName;
	}
	
	public String getRowElementName()
	{
		return rowElementName;
	}

	public ElementNamer getElementNamer()
	{
		return elementNamer;
	}
	

	/////////////////////////////////////////////////////////////////////////////////////
	// Retrieval of included child table specifications
	
	public List<Pair<ForeignKey,TableOutputSpec>> getChildOutputSpecsByFK()
	{
		return new ArrayList<Pair<ForeignKey,TableOutputSpec>>(childSpecsByFK);
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
		return new ArrayList<Pair<ForeignKey,TableOutputSpec>>(parentSpecsByFK);
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
	
	// Primary addChild implementation, all other addParent methods delegate to this one.
	public TableOutputSpec withChild(RelId child_rel_id,
	                                 Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
	                                 TableOutputSpec child_output_spec)// Optional.
	{
		if ( child_output_spec == null )
			child_output_spec = makeChildTableOutputSpec(child_rel_id, reqd_fk_field_names);

		final Set<String> normd_reqd_fk_field_names = dbmd.normalizeNames(reqd_fk_field_names);

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
				
				// No breaking from the loop here, so case that multiple fk's satisfy requirements can be detected.
			}
		}

		if ( sought_fk == null )
			throw new IllegalArgumentException("No foreign key found from table " + child_rel_id + " to " + relId);

		return new TableOutputSpec(relId,
		                           dbmd,
		                           elementNamer,
		                           includedFields,
		                           rowElementName,
		                           rowCollectionElementName,
		                           snoc(childSpecsByFK, Pair.make(sought_fk, child_output_spec)),
		                           parentSpecsByFK);
	}


	/** Add a child table for inclusion in this table's output, with default output options.
	    The child table should have exactly one foreign key to this table, otherwise the function
	    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withChild(String pq_child_rel_name)
	{
		if ( pq_child_rel_name == null || pq_child_rel_name.length() == 0 )
			throw new IllegalArgumentException("Child table name is required.");
		
		RelId child_rel_id = dbmd.toRelId(pq_child_rel_name);
		
		return withChild(child_rel_id,
		                 null,  // fk field names unspecified
		                 null); // default output spec
	}
	
	public TableOutputSpec withChild(String pq_child_rel_name, // possibly qualified table or view name
	                                 Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this child table reference this parent.
	{
		return withChild(dbmd.toRelId(pq_child_rel_name),
		                 reqd_fk_field_names,
		                 null);
	}

	
	/** Add a child table for inclusion in this table's output with specified output options.
	    The child table should have exactly one foreign key to this table, otherwise the function
	    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withChild(TableOutputSpec child_output_spec)
	{
		if ( child_output_spec == null )
			throw new IllegalArgumentException("Child table output specification is required.");
		
		return withChild(child_output_spec.getRelationId(),
		                 null, // specific fk includedFields unspecified
		                 child_output_spec);
	}
	
	public TableOutputSpec withChild(TableOutputSpec child_output_spec,
	                                 Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this child table reference this parent.
	{
		if ( child_output_spec == null )
			throw new IllegalArgumentException("Child table output specification is required.");
		
		return withChild(child_output_spec.getRelationId(),
		                 reqd_fk_field_names,
		                 child_output_spec);
	}
	
	/** Convenience method to include all child tables with default options and naming.  
	 *  The included table output specifications will have default options, so they will
	 *  not themselves specify any child or parent tables in their own output.  An
	 *  individual table specification added in this manner can be customized by retrieving
	 *  it via one of the getSpec* methods and modifying it. The newly added child table
	 *  specifications will replace any existing specifications for child tables.*/
	public TableOutputSpec withAllChildTables()
	{
		List<Pair<ForeignKey,TableOutputSpec>> child_specs_by_fk = new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		
		for(ForeignKey fk: dbmd.getForeignKeysFromChildrenTo(this.relId))
		{
			RelId child_rel_id = fk.getSourceRelationId();
			
			if ( dbmd.getRelationMetaData(child_rel_id) != null ) // ignore tables for which we have no metadata
			{
				Set<String> fk_field_names = new HashSet<String>(fk.getSourceFieldNames());
				
				child_specs_by_fk.add(Pair.make(fk,
				                                makeChildTableOutputSpec(child_rel_id, fk_field_names)));
			}
		}
		
		return new TableOutputSpec(relId,
		                           dbmd,
		                           elementNamer,
		                           includedFields,
		                           rowElementName,
		                           rowCollectionElementName,
		                           child_specs_by_fk,
		                           parentSpecsByFK);
	}
	
	protected TableOutputSpec makeChildTableOutputSpec(RelId child_rel_id, Set<String> reqd_fk_field_names)
	{
		return new TableOutputSpec(child_rel_id,
		                           dbmd,
		                           elementNamer,
		                           elementNamer.getChildRowElementNameWithinParent(child_rel_id, relId, reqd_fk_field_names),
		                           elementNamer.getChildRowCollectionElementNameWithinParent(child_rel_id, relId, reqd_fk_field_names));
	}

	
	// Methods for including a child table in the output
	////////////////////////////////////////////////////////////////////////////////////


	////////////////////////////////////////////////////////////////////////////////////
	// Methods for including a parent table in the output
	

	// Primary addParent implementation, all other addParent methods delegate to this one.
	public TableOutputSpec withParent(RelId parent_rel_id,
	                                  Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this table reference the parent.
	                                  TableOutputSpec parent_output_spec)// Optional.
	{
		if ( parent_output_spec == null )
			parent_output_spec = makeParentTableOutputSpec(parent_rel_id, reqd_fk_field_names);

		final Set<String> normd_reqd_fk_field_names = dbmd.normalizeNames(reqd_fk_field_names);

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
				
				// No breaking from the loop here, so case that multiple fk's satisfy requirements can be detected.
			}
		}

		if ( sought_fk == null )
			throw new IllegalArgumentException("No foreign key found from table " + parent_rel_id + " to " + relId);


		return new TableOutputSpec(relId,
		                           dbmd,
		                           elementNamer,
		                           includedFields,
		                           rowElementName,
		                           rowCollectionElementName,
		                           childSpecsByFK,
		                           snoc(parentSpecsByFK, Pair.make(sought_fk, parent_output_spec)));
	}


	/** Add a parent table for inclusion in this table's output, with default output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withParent(String pq_parent_rel_name)
	{
		if ( pq_parent_rel_name == null || pq_parent_rel_name.length() == 0 )
			throw new IllegalArgumentException("Parent table name is required.");
	
		return withParent(dbmd.toRelId(pq_parent_rel_name),
		                  null,  // fk field names unspecified
		                  null); // default output spec
	}
	

	public TableOutputSpec withParent(String pq_parent_rel_name, // possibly qualified table or view name
	                                  Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this table reference this parent.
	{
		RelId parent_rel_id = dbmd.toRelId(pq_parent_rel_name);
		
		return withParent(parent_rel_id,
		                  reqd_fk_field_names,
		                  null);  // default output spec
		                 
	}


	/** Add a parent table for inclusion in this table's output with specified output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withParent(TableOutputSpec parent_output_spec)
	{
		if ( parent_output_spec == null )
			throw new IllegalArgumentException("Parent table output specification is required.");
	
		return withParent(parent_output_spec.getRelationId(),
		                  null, // specific fk includedFields unspecified
		                  parent_output_spec);
	}

	public TableOutputSpec withParent(TableOutputSpec parent_output_spec,
	                                  Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this table reference this parent.
	{
		if ( parent_output_spec == null )
			throw new IllegalArgumentException("Parent table output specification is required.");
	
		return withParent(parent_output_spec.getRelationId(),
		                  reqd_fk_field_names,
		                  parent_output_spec);
	}
	
	/** Convenience method to include all parent tables with default options and naming.  
	 *  The included table output specifications will have default options, so they will
	 *  not themselves specify any child or parent tables in their own output.  An
	 *  individual table specification added in this manner can be customized by retrieving
	 *  it via one of the getSpec* methods and modifying it.  The newly added parent
	 *  specifications will replace any existing specifications for parent tables. */
	public TableOutputSpec withAllParentTables()
	{
		List<Pair<ForeignKey,TableOutputSpec>> parent_specs_by_fk = new ArrayList<Pair<ForeignKey,TableOutputSpec>>();

		for(ForeignKey fk: dbmd.getForeignKeysToParentsFrom(this.relId))
		{
			RelId parent_rel_id = fk.getTargetRelationId();
			
			if ( dbmd.getRelationMetaData(parent_rel_id) != null ) // ignore tables for which we have no metadata
			{
				Set<String> fk_field_names = new HashSet<String>(fk.getSourceFieldNames()); // not a mistake - source fields identify the foreign key
				
				parent_specs_by_fk.add(Pair.make(fk,
				                                 makeParentTableOutputSpec(parent_rel_id, fk_field_names)));
			}
		}
		
		return new TableOutputSpec(relId,
		                           dbmd,
		                           elementNamer,
		                           includedFields,
		                           rowElementName,
		                           rowCollectionElementName,
		                           childSpecsByFK,
		                           parent_specs_by_fk);

	}
	
	protected TableOutputSpec makeParentTableOutputSpec(RelId parent_rel_id, Set<String> reqd_fk_field_names)
	{
		return new TableOutputSpec(parent_rel_id,
		                           dbmd,
		                           elementNamer,
		                           elementNamer.getParentRowElementNameWithinChild(relId, parent_rel_id, reqd_fk_field_names),
		                           null); // no collection element name necessary for parent spec
	}
	
	// Methods for including a parent table in the output
	////////////////////////////////////////////////////////////////////////////////////


	
	public int hashCode()
	{
		if ( hashCode == null )
		{
			hashCode = hashcode(relId)
			         + hashcode(includedFields)
			         + hashcode(childSpecsByFK)
			         + hashcode(parentSpecsByFK)
			         + hashcode(rowCollectionElementName)
			         + hashcode(rowElementName)
			         + hashcode(dbmd)
			         + hashcode(elementNamer);
		}
		
		return hashCode;
	}
	
	public boolean equals(Object o)
	{
		if ( !(o instanceof TableOutputSpec) )
			return false;
		else
		{
			final TableOutputSpec tos = (TableOutputSpec)o;
			
			if (this == tos)
				return true;
			else
				return eqOrNull(relId,tos.relId)
				    && eqOrNull(includedFields,tos.includedFields)
				    && eqOrNull(childSpecsByFK,tos.childSpecsByFK)
				    && eqOrNull(parentSpecsByFK,tos.parentSpecsByFK)
				    && eqOrNull(rowCollectionElementName,tos.rowCollectionElementName)
				    && eqOrNull(rowElementName,tos.rowElementName)
				    && eqOrNull(dbmd,tos.dbmd)
				    && eqOrNull(elementNamer,tos.elementNamer);
		}
	}
}
