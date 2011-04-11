select xmlserialize(content xmlelement(name "drug-listing", xmlattributes('http://example/namespace' as "xmlns"),
         xmlagg(row_xml)) as clob) "rowcoll_xml"
from
 ( select -- rows of XDAGENTEST.DRUG
     d.*,
     -- row_xml
     xmlelement(name "drug"
      ,xmlforest(
        d.ID "id",
        d.NAME "name",
        d.COMPOUND_ID "compound_id",
        d.MESH_ID "mesh_id",
        d.DRUGBANK_ID "drugbank_id",
        d.CID "cid",
        d.THERAPEUTIC_INDICATIONS "therapeutic_indications",
        d.spl "spl"
       )
      --  child tables for XDAGENTEST.DRUG
      ,(select xmlelement(name "drug_functional_category-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY
             dfc.*,
             -- row_xml
             xmlelement(name "drug_functional_category"
              ,xmlforest(
                dfc.DRUG_ID "drug_id",
                dfc.FUNCTIONAL_CATEGORY_ID "functional_category_id",
                dfc.AUTHORITY_ID "authority_id",
                dfc.SEQ "seq"
               )
              -- No child tables for XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY
              -- No parent tables for XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY
             ) row_xml
           from XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY dfc
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement(name "advisory-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.ADVISORY
             a.*,
             -- row_xml
             xmlelement(name "advisory"
              ,xmlforest(
                a.ID "id",
                a.DRUG_ID "drug_id",
                a.ADVISORY_TYPE_ID "advisory_type_id",
                a.TEXT "text"
               )
              -- No child tables for XDAGENTEST.ADVISORY
              -- No parent tables for XDAGENTEST.ADVISORY
             ) row_xml
           from XDAGENTEST.ADVISORY a
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement(name "drug_reference-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.DRUG_REFERENCE
             dr.*,
             -- row_xml
             xmlelement(name "drug_reference"
              ,xmlforest(
                dr.DRUG_ID "drug_id",
                dr.REFERENCE_ID "reference_id",
                dr.PRIORITY "priority"
               )
              -- No child tables for XDAGENTEST.DRUG_REFERENCE
              -- No parent tables for XDAGENTEST.DRUG_REFERENCE
             ) row_xml
           from XDAGENTEST.DRUG_REFERENCE dr
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement(name "brand-listing", 
                 xmlagg(row_els_q.row_xml)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.BRAND
             b.*,
             -- row_xml
             xmlelement(name "brand"
              ,xmlforest(
                b.DRUG_ID "drug_id",
                b.BRAND_NAME "brand_name",
                b.LANGUAGE_CODE "language_code",
                b.MANUFACTURER_ID "manufacturer_id"
               )
              -- No child tables for XDAGENTEST.BRAND
              -- No parent tables for XDAGENTEST.BRAND
             ) row_xml
           from XDAGENTEST.BRAND b
         ) row_els_q
        where
          row_els_q.DRUG_ID = d.ID
       ) -- child subquery
      --  parent tables for XDAGENTEST.DRUG
      ,(select -- rows of XDAGENTEST.COMPOUND
          -- row_xml
          xmlelement(name "compound"
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
           -- No child tables for XDAGENTEST.COMPOUND
           -- No parent tables for XDAGENTEST.COMPOUND
          ) row_xml
        from XDAGENTEST.COMPOUND c
        where
          c.ID = d.COMPOUND_ID
       ) -- parent subquery
     ) row_xml
   from XDAGENTEST.DRUG d
 ) 
