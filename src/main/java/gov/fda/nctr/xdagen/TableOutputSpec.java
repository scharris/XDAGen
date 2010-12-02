package gov.fda.nctr.xdagen;

import static gov.fda.nctr.util.CoreFuns.eqOrNull;
import static gov.fda.nctr.util.CoreFuns.hashcode;
import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.StringFuns.dotQualify;
import static java.util.Arrays.asList;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.Field;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;
import gov.fda.nctr.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class TableOutputSpec implements Cloneable {

	
	public interface Factory {
		
		// These two functions are convenience methods intended for direct use by clients.
		public TableOutputSpec table(RelId relid);
		public TableOutputSpec table(String pq_table_name); // possibly qualified table name

		// These methods are used internally by TableOutputSpec to create parent/child output specs when they aren't specified when adding parent or child tables.
		public TableOutputSpec makeChildTableOutputSpec(ForeignKey fk_from_child, TableOutputSpec attached_to_ospec);
		public TableOutputSpec makeParentTableOutputSpec(ForeignKey fk_to_parent, TableOutputSpec attached_to_ospec);
		
	}
	
	protected RelId relId;
	
    protected List<Field> includedFields;
    
	protected List<Pair<ForeignKey,TableOutputSpec>> childSpecsByFK;
    
	protected List<Pair<ForeignKey,TableOutputSpec>> parentSpecsByFK;
	
	protected String rowCollectionElementName;
	
	protected String rowElementName;
	
	protected RowOrdering rowOrdering;
	
	protected DBMD dbmd;

	protected Factory factory;

	
	protected Integer hashCode;
	
	
	/** Create an output spec with all the includedFields for the passed table/view included but no parents or children.
     If the table name is not qualified by schema, then the DBMD should have an owning schema specified, else
     database metadata may not be found for databases supporting schemas. */
	public TableOutputSpec(String pq_relname,     // required
                           DBMD dbmd,             // required
                           Factory ospec_factory) // required
    {
    	this(dbmd.toRelId(pq_relname),
    	     dbmd,
    	     ospec_factory);
    }
	

    public TableOutputSpec(RelId relId,           // required
	                       DBMD dbmd,             // required
	                       Factory ospec_factory) // required
	{
    	this(relId,
    	     dbmd,
    	     ospec_factory,
    	     dbmd.getRelationMetaData(relId).getFields(),
    	     null, // row ordering
    	     null, // row el name
    	     null, // row coll el name
    	     null, // child table specs
    	     null);// parent table specs
	}

    public TableOutputSpec(RelId relId,                   // required
	                       DBMD dbmd,                     // required
	                       Factory ospec_factory,         // required
	                       String row_el_name,            // optional
	                       String row_collection_el_name) // optional
	{
    	this(relId,
    	     dbmd,
    	     ospec_factory,
    	     dbmd.getRelationMetaData(relId).getFields(),
    	     null, // row ordering
    	     row_el_name,
    	     row_collection_el_name,
    	     null,
    	     null);
	}


    protected TableOutputSpec(RelId relid,                   // required
                              DBMD dbmd,                     // required
                              Factory ospec_factory,         // required
                              List<Field> included_fields,  // optional
                              RowOrdering row_ordering,      // optional
                              String row_el_name,            // optional
                              String row_collection_el_name, // optional
                              List<Pair<ForeignKey,TableOutputSpec>> included_child_table_specs,  // optional
                              List<Pair<ForeignKey,TableOutputSpec>> included_parent_table_specs) // optional
	{
		super();
		this.relId = requireArg(relid, "relation id");
		this.dbmd = requireArg(dbmd, "database metadata");
		this.factory = requireArg(ospec_factory, "table output spec factory");
		this.includedFields = included_fields != null ? new ArrayList<Field>(included_fields) : dbmd.getRelationMetaData(relid).getFields();
		this.rowOrdering = row_ordering;
		this.rowElementName = row_el_name != null ? row_el_name : relid.getName().toLowerCase();
		this.rowCollectionElementName = row_collection_el_name != null ? row_collection_el_name : relid.getName().toLowerCase() + "-listing";		
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
	
	public DBMD getDatabaseMetaData()
	{
		return dbmd;
	}
	
	public Factory getFactory()
	{
		return factory;
	}
	
	public List<Field> getIncludedFields()
	{
		return includedFields;
	}
	
	public List<String> getIncludedFieldNames()
	{
		List<String> res = new ArrayList<String>();
		
		for(Field f: includedFields)
			res.add(f.getName());
		
		return res;
	}
	
	public String getRowCollectionElementName()
	{
		return rowCollectionElementName;
	}
	
	public String getRowElementName()
	{
		return rowElementName;
	}

	public RowOrdering getRowOrdering()
	{
		return rowOrdering;
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
	public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForChild(RelId child_relid)
	{
		List<Pair<ForeignKey,TableOutputSpec>> child_specs = new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		
		for(Pair<ForeignKey,TableOutputSpec> fk_ospec_pair: childSpecsByFK)
		{
			if ( fk_ospec_pair.snd().getRelationId().equals(child_relid) )
				child_specs.add(fk_ospec_pair);
		}
		
		return child_specs;
	}
	
	
	public TableOutputSpec getOutputSpecForChild(String pq_child_rel_name)
	{
		return getOutputSpecForChild(dbmd.toRelId(pq_child_rel_name));
	}
	
	
	public TableOutputSpec getOutputSpecForChild(RelId child_relid)
	{
		List<Pair<ForeignKey,TableOutputSpec>> child_specs = getOutputSpecsByFKForChild(child_relid);
		
		if ( child_specs.size() == 0 )
			throw new IllegalArgumentException("Table " + child_relid + " has no foreign key links to " + relId + ".");
		else if ( child_specs.size() > 1 )
			throw new IllegalArgumentException("Child table " + child_relid + " has multiple links to " + relId + ".");
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
	public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForParent(RelId parent_relid)
	{
		List<Pair<ForeignKey,TableOutputSpec>> parent_specs = new ArrayList<Pair<ForeignKey,TableOutputSpec>>();
		
		for(Pair<ForeignKey,TableOutputSpec> fk_ospec_pair: parentSpecsByFK)
		{
			if ( fk_ospec_pair.snd().getRelationId().equals(parent_relid) )
				parent_specs.add(fk_ospec_pair);
		}
		
		return parent_specs;
	}
	
	public TableOutputSpec getOutputSpecForParent(String pq_parent_rel_name)
	{
		return getOutputSpecForParent(dbmd.toRelId(pq_parent_rel_name));
	}
	
	
	public TableOutputSpec getOutputSpecForParent(RelId parent_relid)
	{
		List<Pair<ForeignKey,TableOutputSpec>> parent_specs = getOutputSpecsByFKForParent(parent_relid);
		
		if ( parent_specs.size() == 0 )
			throw new IllegalArgumentException("Table " + relId + " has no foreign key links to " + parent_relid + ".");
		else if ( parent_specs.size() > 1 )
			throw new IllegalArgumentException("Parent table " + parent_relid + " has multiple foreign key links from " + relId + ".");
		else
			return parent_specs.get(0).snd();
	}

	// Retrieval of included parent table specifications
	/////////////////////////////////////////////////////////////////////////////////////
	

	
	////////////////////////////////////////////////////////////////////////////////////
	// Methods for including a child table in the output
	
	public TableOutputSpec withChild(ForeignKey fk_from_child,          // Required
	                                 TableOutputSpec child_output_spec) // Optional
	{
		requireArg(fk_from_child, "foreign key from child table");
		
		if ( child_output_spec == null )
			child_output_spec = factory.makeChildTableOutputSpec(fk_from_child, this);
		try
		{
			TableOutputSpec copy = (TableOutputSpec)this.clone();
			
			copy.childSpecsByFK = associativeListWithEntry(childSpecsByFK, fk_from_child, child_output_spec);
			
			return copy;
		}
		catch(CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	// Primary withChild implementation, all other withChild methods delegate to this one.
	public TableOutputSpec withChild(RelId child_relid,               // Required
	                                 Set<String> reqd_fk_field_names,  // Optional.  Required if multiple fk's from this child table reference this parent.
	                                 TableOutputSpec child_output_spec)// Optional
	{
		requireArg(child_relid, "child table relation identifier");
		
		ForeignKey sought_fk = dbmd.getForeignKeyFromTo(child_relid,
		                                                relId,
		                                                reqd_fk_field_names,
		                                                DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY);

		if ( sought_fk == null )
			throw new IllegalArgumentException("No foreign key found from table " + child_relid + " to " + relId);

		return withChild(sought_fk,
		                 child_output_spec);
	}


	/** Add a child table for inclusion in this table's output, with default output options.
	    The child table should have exactly one foreign key to this table, otherwise the function
	    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withChild(String pq_child_rel_name)
	{
		requireArg(pq_child_rel_name, "child table name");
		
		return withChild(dbmd.toRelId(pq_child_rel_name),
		                 null,  // fk field names unspecified
		                 null); // default output spec
	}
	
	public TableOutputSpec withChild(String pq_child_rel_name,         // Required, possibly qualified table or view name.
	                                 Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this child table reference this parent.
	{
		return withChild(dbmd.toRelId(pq_child_rel_name),
		                 reqd_fk_field_names,
		                 null);
	}

	
	/** Add a child table for inclusion in this table's output with specified output options.
	    The child table should have exactly one foreign key to this table, otherwise the function
	    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withChild(TableOutputSpec child_output_spec) // Required
	{
		requireArg(child_output_spec, "child table output specification");
		
		return withChild(child_output_spec.getRelationId(),
		                 null, // specific fk unspecified
		                 child_output_spec);
	}
	
	public TableOutputSpec withChild(TableOutputSpec child_output_spec, // Required
	                                 Set<String> reqd_fk_field_names)   // Optional.  Required if multiple fk's from this child table reference this parent.
	{
		requireArg(child_output_spec, "child table output specification");
		
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
		
		for(ForeignKey fk: dbmd.getForeignKeysFromTo(null, this.relId, DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY))
		{
			child_specs_by_fk.add(Pair.make(fk, factory.makeChildTableOutputSpec(fk, this)));
		}
		
		try
		{
			TableOutputSpec copy = (TableOutputSpec)this.clone();
			copy.childSpecsByFK = child_specs_by_fk;
			
			return copy;
		}
		catch(CloneNotSupportedException cnse)
		{
			throw new RuntimeException(cnse);
		}
	}
	
	// Methods for including a child table in the output
	////////////////////////////////////////////////////////////////////////////////////


	////////////////////////////////////////////////////////////////////////////////////
	// Methods for including a parent table in the output
	
	
	public TableOutputSpec withParent(ForeignKey fk_to_parent,            // Required
	                                  TableOutputSpec parent_output_spec) // Optional
	{
		requireArg(fk_to_parent, "foreign key to parent table");
		
		if ( parent_output_spec == null )
			parent_output_spec = factory.makeParentTableOutputSpec(fk_to_parent, this);
		try
		{
			TableOutputSpec copy = (TableOutputSpec)this.clone();
			
			copy.parentSpecsByFK = associativeListWithEntry(parentSpecsByFK, fk_to_parent, parent_output_spec);
			
			return copy;
		}
		catch(CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	// Primary withParent implementation, all other withParent methods delegate to this one.
	public TableOutputSpec withParent(RelId parent_relid,                // Required
	                                  Set<String> reqd_fk_field_names,    // Optional.  Required if multiple fk's from this table reference the parent.
	                                  TableOutputSpec parent_output_spec) // Optional.
	{
		requireArg(parent_relid, "parent table relation identifier");
		
		ForeignKey sought_fk = dbmd.getForeignKeyFromTo(relId,
		                                                parent_relid,
		                                                reqd_fk_field_names,
		                                                DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY);

		
		if ( sought_fk == null )
			throw new IllegalArgumentException("No foreign key found from table " + relId + " to " + parent_relid);

		return withParent(sought_fk,
		                  parent_output_spec);
	}


	/** Add a parent table for inclusion in this table's output, with default output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withParent(String pq_parent_rel_name)
	{
		requireArg(pq_parent_rel_name, "parent table name");
	
		return withParent(dbmd.toRelId(pq_parent_rel_name),
		                  null,  // fk field names unspecified
		                  null); // default output spec
	}
	

	public TableOutputSpec withParent(String pq_parent_rel_name,        // Required, possibly qualified table or view name
	                                  Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this table reference this parent.
	{
		requireArg(pq_parent_rel_name, "parent table name");
		
		return withParent(dbmd.toRelId(pq_parent_rel_name),
		                  reqd_fk_field_names,
		                  null);  // default output spec
		                 
	}


	/** Add a parent table for inclusion in this table's output with specified output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */ 
	public TableOutputSpec withParent(TableOutputSpec parent_output_spec)
	{
		requireArg(parent_output_spec, "parent table output specification");
	
		return withParent(parent_output_spec.getRelationId(),
		                  null, // specific fk unspecified
		                  parent_output_spec);
	}

	public TableOutputSpec withParent(TableOutputSpec parent_output_spec,
	                                  Set<String> reqd_fk_field_names)  // Optional.  Required if multiple fk's from this table reference this parent.
	{
		requireArg(parent_output_spec, "parent table output specification");
	
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

		for(ForeignKey fk: dbmd.getForeignKeysFromTo(this.relId, null, DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY))
		{
			parent_specs_by_fk.add(Pair.make(fk, factory.makeParentTableOutputSpec(fk, this)));
		}
		
		try
		{
			TableOutputSpec copy = (TableOutputSpec)this.clone();
			copy.parentSpecsByFK = parent_specs_by_fk;
			
			return copy;
		}
		catch(CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	// Methods for including a parent table in the output
	////////////////////////////////////////////////////////////////////////////////////

	
	
    ////////////////////////////////////////////////////////////////////////////////////
	// Row ordering

	public static abstract class RowOrdering {
		
		// Get a list of expressions to order by, in terms of the table fields and the passed field qualifying alias.
		public abstract List<String> getOrderByExpressions(String field_qualifying_alias);
		
		/** Convenience method for constructing order by expressions for field names.
		 *  The field names may optionally including a trailing " asc" or " desc" to specify sort direction. */ 
		public static RowOrdering fields(final String... field_names)
		{
			return new RowOrdering() {
				public List<String> getOrderByExpressions(String field_qualifying_alias)
				{
					return dotQualify(asList(field_names), field_qualifying_alias);
				}
			};
		}
	}
	
	
	public TableOutputSpec orderedBy(RowOrdering row_ordering)
	{
		try
		{
			TableOutputSpec copy = (TableOutputSpec)this.clone();
			copy.rowOrdering = row_ordering;
			
			return copy;
		}
		catch(CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}

	// Row ordering
    ////////////////////////////////////////////////////////////////////////////////////

	
	///////////////////////////////////////////////////////////////////////////////////
	// Factory customization

	public TableOutputSpec withTableOutputSpecFactory(Factory f)
	{
		try
		{
			TableOutputSpec copy = (TableOutputSpec)this.clone();
			copy.factory = f;
			
			return copy;
		}
		catch(CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	// Factory customization
	///////////////////////////////////////////////////////////////////////////////////
	
	
	public int hashCode()
	{
		if ( hashCode == null )
		{
			hashCode = hashcode(relId)
			         + hashcode(includedFields)
			         + hashcode(childSpecsByFK)
			         + hashcode(parentSpecsByFK)
			         + hashcode(rowElementName)
			         + hashcode(rowCollectionElementName)
			         + hashcode(rowOrdering)
				     + hashcode(dbmd)
			         + hashcode(factory);

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
			
			if (this == tos )
				return true;
			else
			{
				if ( hashCode != null && tos.hashCode != null && !hashCode.equals(tos.hashCode()) )
						return false;
				else
					return eqOrNull(relId,tos.relId)
					    && eqOrNull(includedFields,tos.includedFields)
					    && eqOrNull(childSpecsByFK,tos.childSpecsByFK)
					    && eqOrNull(parentSpecsByFK,tos.parentSpecsByFK)
					    && eqOrNull(rowCollectionElementName,tos.rowCollectionElementName)
					    && eqOrNull(rowElementName,tos.rowElementName)
					    && eqOrNull(rowOrdering, tos.rowOrdering)
					    && eqOrNull(dbmd,tos.dbmd)
					    && eqOrNull(factory,tos.factory);

			}
		}
	}
	
    public static <E> List<E> snoc(List<E> l, E e)
    {
        List<E> cl = new ArrayList<E>(l);
        cl.add(e);
        return cl;
    }
    
	static <K,V> List<Pair<K,V>> associativeListWithEntry(final List<Pair<K,V>> l, final K k, final V v)
	{
		List<Pair<K,V>> res = new ArrayList<Pair<K,V>>();
		
		Pair<K,V> new_entry = Pair.make(k,v);
		
		boolean added = false;
		for(Pair<K,V> entry: l)
		{
			if (entry.fst().equals(k))
			{
				res.add(new_entry);
				added = true;
			}
			else
				res.add(entry);
		}
		
		if ( !added )
			res.add(new_entry);
		
		return res;
	}

}
