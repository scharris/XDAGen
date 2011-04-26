select -- rows of xdagentest.drug
  -- row_xml
  xmlserialize(content xmlelement(name "drug", xmlattributes('http://example/namespace' as "xmlns")
   ,xmlforest(
     d.id as "id",
     d.name as "name",
     d.compound_id as "compound_id",
     d.mesh_id as "mesh_id",
     d.drugbank_id as "drugbank_id",
     d.cid as "cid",
     d.therapeutic_indications as "therapeutic_indications",
     d.spl as "spl"
    )
   --  child tables for xdagentest.drug
   ,(select xmlelement(name "advisory-listing", 
              xmlagg(a_row.row_xml order by a_row.id)) "rowcoll_xml"
     from
      ( select -- rows of xdagentest.advisory
          a.*,
          -- row_xml
          xmlelement(name "advisory"
           ,xmlforest(
             a.id as "id",
             a.drug_id as "drug_id",
             a.advisory_type_id as "advisory_type_id",
             a.text as "text"
            )
           -- No child tables for xdagentest.advisory
           -- No parent tables for xdagentest.advisory
          ) row_xml
        from xdagentest.advisory a
      ) a_row
     where
       a_row.drug_id = d.id
    ) -- child subquery
   ,(select xmlelement(name "brand-listing", 
              xmlagg(b_row.row_xml order by b_row.drug_id,b_row.brand_name)) "rowcoll_xml"
     from
      ( select -- rows of xdagentest.brand
          b.*,
          -- row_xml
          xmlelement(name "brand"
           ,xmlforest(
             b.drug_id as "drug_id",
             b.brand_name as "brand_name",
             b.language_code as "language_code",
             b.manufacturer_id as "manufacturer_id"
            )
           -- No child tables for xdagentest.brand
           -- No parent tables for xdagentest.brand
          ) row_xml
        from xdagentest.brand b
      ) b_row
     where
       b_row.drug_id = d.id
    ) -- child subquery
   ,(select xmlelement(name "drug_functional_category-listing", 
              xmlagg(dfc_row.row_xml order by dfc_row.drug_id,dfc_row.functional_category_id,dfc_row.authority_id)) "rowcoll_xml"
     from
      ( select -- rows of xdagentest.drug_functional_category
          dfc.*,
          -- row_xml
          xmlelement(name "drug_functional_category"
           ,xmlforest(
             dfc.drug_id as "drug_id",
             dfc.functional_category_id as "functional_category_id",
             dfc.authority_id as "authority_id",
             dfc.seq as "seq"
            )
           -- No child tables for xdagentest.drug_functional_category
           -- No parent tables for xdagentest.drug_functional_category
          ) row_xml
        from xdagentest.drug_functional_category dfc
      ) dfc_row
     where
       dfc_row.drug_id = d.id
    ) -- child subquery
   ,(select xmlelement(name "drug_reference-listing", 
              xmlagg(dr_row.row_xml order by dr_row.drug_id,dr_row.reference_id)) "rowcoll_xml"
     from
      ( select -- rows of xdagentest.drug_reference
          dr.*,
          -- row_xml
          xmlelement(name "drug_reference"
           ,xmlforest(
             dr.drug_id as "drug_id",
             dr.reference_id as "reference_id",
             dr.priority as "priority"
            )
           -- No child tables for xdagentest.drug_reference
           -- No parent tables for xdagentest.drug_reference
          ) row_xml
        from xdagentest.drug_reference dr
      ) dr_row
     where
       dr_row.drug_id = d.id
    ) -- child subquery
   --  parent tables for xdagentest.drug
   ,(select -- rows of xdagentest.compound
       -- row_xml
       xmlelement(name "compound"
        ,xmlforest(
          c.id as "id",
          c.display_name as "display_name",
          c.nctr_isis_id as "nctr_isis_id",
          c.smiles as "smiles",
          c.canonical_smiles as "canonical_smiles",
          c.cas as "cas",
          c.mol_formula as "mol_formula",
          c.mol_weight as "mol_weight",
          c.mol_file as "mol_file",
          c.inchi as "inchi",
          c.inchi_key as "inchi_key",
          c.standard_inchi as "standard_inchi",
          c.standard_inchi_key as "standard_inchi_key"
         )
        -- No child tables for xdagentest.compound
        -- No parent tables for xdagentest.compound
       ) row_xml
     from xdagentest.compound c
     where
       c.id = d.compound_id
    ) -- parent subquery
  ) as text) row_xml
from xdagentest.drug d
order by d.id