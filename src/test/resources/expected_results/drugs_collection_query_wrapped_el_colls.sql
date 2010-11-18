select xmlserialize(content xmlelement("drug-listing", xmlattributes('http://example/namespace' as "xmlns"),
         xmlagg(row_xml)) as clob) "rowcoll_xml"
from
 ( select -- rows of xdagentest.drug
     d.*,
     -- row_xml
     xmlelement("drug"
      ,xmlforest(
        d.ID "id",  
        d.NAME "name",  
        d.COMPOUND_ID "compound_id",  
        d.MESH_ID "mesh_id",  
        d.DRUGBANK_ID "drugbank_id",  
        d.CID "cid",  
        d.THERAPEUTIC_INDICATIONS "therapeutic_indications"  
       )
      --  child tables for xdagentest.drug
      ,(select xmlelement("drug_functional_category-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of xdagentest.drug_functional_category
             dfc.*,
             -- row_xml
             xmlelement("drug_functional_category"
              ,xmlforest(
                dfc.DRUG_ID "drug_id",  
                dfc.FUNCTIONAL_CATEGORY_ID "functional_category_id",  
                dfc.AUTHORITY_ID "authority_id",  
                dfc.SEQ "seq"  
               )
              -- No child tables for xdagentest.drug_functional_category
              -- No parent tables for xdagentest.drug_functional_category
             ) row_xml
           from xdagentest.drug_functional_category dfc
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement("advisory-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of xdagentest.advisory
             a.*,
             -- row_xml
             xmlelement("advisory"
              ,xmlforest(
                a.ID "id",  
                a.DRUG_ID "drug_id",  
                a.ADVISORY_TYPE_ID "advisory_type_id",  
                a.TEXT "text"  
               )
              -- No child tables for xdagentest.advisory
              -- No parent tables for xdagentest.advisory
             ) row_xml
           from xdagentest.advisory a
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement("drug_reference-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of xdagentest.drug_reference
             dr.*,
             -- row_xml
             xmlelement("drug_reference"
              ,xmlforest(
                dr.DRUG_ID "drug_id",  
                dr.REFERENCE_ID "reference_id",  
                dr.PRIORITY "priority"  
               )
              -- No child tables for xdagentest.drug_reference
              -- No parent tables for xdagentest.drug_reference
             ) row_xml
           from xdagentest.drug_reference dr
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement("brand-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of xdagentest.brand
             b.*,
             -- row_xml
             xmlelement("brand"
              ,xmlforest(
                b.DRUG_ID "drug_id",  
                b.BRAND_NAME "brand_name",  
                b.LANGUAGE_CODE "language_code",  
                b.MANUFACTURER_ID "manufacturer_id"  
               )
              -- No child tables for xdagentest.brand
              -- No parent tables for xdagentest.brand
             ) row_xml
           from xdagentest.brand b
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      --  parent tables for xdagentest.drug
      ,(select -- rows of xdagentest.compound
          -- row_xml
          xmlelement("compound"
           ,xmlforest(
             c.ID "id",  
             c.DISPLAY_NAME "display_name",  
             c.NCTR_ISIS_ID "nctr_isis_id",  
             c.SMILES "smiles",  
             c.CANONICAL_SMILES "canonical_smiles",  
             c.CAS "cas",  
             c.MOL_FORMULA "mol_formula",  
             c.MOL_WEIGHT "mol_weight",  
             c.MOL_FILE "mol_file",  
             c.INCHI "inchi",  
             c.INCHI_KEY "inchi_key",  
             c.STANDARD_INCHI "standard_inchi",  
             c.STANDARD_INCHI_KEY "standard_inchi_key"  
            )
           -- No child tables for xdagentest.compound
           -- No parent tables for xdagentest.compound
          ) row_xml
        from xdagentest.compound c
        where
          c.ID = d.COMPOUND_ID
       ) -- parent subquery
     ) row_xml
   from xdagentest.drug d
 ) 
