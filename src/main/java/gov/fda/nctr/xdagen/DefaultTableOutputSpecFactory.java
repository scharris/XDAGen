package gov.fda.nctr.xdagen;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;

import java.util.HashSet;
import java.util.Set;

public class DefaultTableOutputSpecFactory implements TableOutputSpec.Factory {

	DBMD dbmd;
	ElementNamer elNamer;
	String outputXmlNamespace;
	

	public DefaultTableOutputSpecFactory(DBMD dbmd, XmlElementCollectionStyle el_coll_style, String output_xml_namespace)
	{
		this(dbmd, new DefaultElementNamer(dbmd, el_coll_style), output_xml_namespace);
	}
	
	public DefaultTableOutputSpecFactory(DBMD dbmd, ElementNamer el_namer, String output_xml_namespace)
	{
		this.dbmd = dbmd;
		this.elNamer = el_namer;
		this.outputXmlNamespace = output_xml_namespace;
	}
	
	public void setElementNamer(ElementNamer el_namer)
	{
		elNamer = el_namer;
	}
	
	public ElementNamer getElementNamer()
	{
		return elNamer;
	}
	
	public String getOutputXmlNamespace()
	{
		return outputXmlNamespace;
	}

	
	public void setOutputXmlNamespace(String outputXmlNamespace)
	{
		this.outputXmlNamespace = outputXmlNamespace;
	}
	
	
	@Override
	public TableOutputSpec table(RelId relid)
	{
		return new TableOutputSpec(relid,
		                           dbmd,
		                           this,
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
		                           outputXmlNamespace,
		                           row_el_name,
		                           null); // no collection element name necessary for parent spec

	}
}