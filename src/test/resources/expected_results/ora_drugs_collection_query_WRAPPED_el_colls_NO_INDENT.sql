select xmlserialize(content xmlelement(name "drug-listing", xmlattributes('http://nctr.fda.gov/xdagen' as "xmlns"),
         xmlagg(d_row.row_xml order by d_row.ID)) as clob no indent) "rowcoll_xml"
from
 ( select -- rows of XDAGENTEST.DRUG
     d.*,
     -- row_xml
     xmlelement(name "drug"
      ,xmlforest(
        d.ID as "id",
        d.NAME as "name",
        d.COMPOUND_ID as "compound_id",
        d.MESH_ID as "mesh_id",
        d.DRUGBANK_ID as "drugbank_id",
        d.CID as "cid",
        d.THERAPEUTIC_INDICATIONS as "therapeutic_indications",
        d.SPL as "spl"
       )
      --  child tables for XDAGENTEST.DRUG
      ,(select xmlelement(name "advisory-listing", 
                 xmlagg(a_row.row_xml order by a_row.ID)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.ADVISORY
             a.*,
             -- row_xml
             xmlelement(name "advisory"
              ,xmlforest(
                a.ID as "id",
                a.DRUG_ID as "drug_id",
                a.ADVISORY_TYPE_ID as "advisory_type_id",
                a.TEXT as "text"
               )
              -- No child tables for XDAGENTEST.ADVISORY
              -- No parent tables for XDAGENTEST.ADVISORY
             ) row_xml
           from XDAGENTEST.ADVISORY a
         ) a_row
        where
          a_row.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement(name "brand-listing", 
                 xmlagg(b_row.row_xml order by b_row.DRUG_ID,b_row.BRAND_NAME)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.BRAND
             b.*,
             -- row_xml
             xmlelement(name "brand"
              ,xmlforest(
                b.DRUG_ID as "drug_id",
                b.BRAND_NAME as "brand_name",
                b.LANGUAGE_CODE as "language_code",
                b.MANUFACTURER_ID as "manufacturer_id"
               )
              -- No child tables for XDAGENTEST.BRAND
              -- No parent tables for XDAGENTEST.BRAND
             ) row_xml
           from XDAGENTEST.BRAND b
         ) b_row
        where
          b_row.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement(name "drug_functional_category-listing", 
                 xmlagg(dfc_row.row_xml order by dfc_row.DRUG_ID,dfc_row.FUNCTIONAL_CATEGORY_ID,dfc_row.AUTHORITY_ID)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY
             dfc.*,
             -- row_xml
             xmlelement(name "drug_functional_category"
              ,xmlforest(
                dfc.DRUG_ID as "drug_id",
                dfc.FUNCTIONAL_CATEGORY_ID as "functional_category_id",
                dfc.AUTHORITY_ID as "authority_id",
                dfc.SEQ as "seq"
               )
              -- No child tables for XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY
              -- No parent tables for XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY
             ) row_xml
           from XDAGENTEST.DRUG_FUNCTIONAL_CATEGORY dfc
         ) dfc_row
        where
          dfc_row.DRUG_ID = d.ID
       ) -- child subquery
      ,(select xmlelement(name "drug_reference-listing", 
                 xmlagg(dr_row.row_xml order by dr_row.DRUG_ID,dr_row.REFERENCE_ID)) "rowcoll_xml"
        from
         ( select -- rows of XDAGENTEST.DRUG_REFERENCE
             dr.*,
             -- row_xml
             xmlelement(name "drug_reference"
              ,xmlforest(
                dr.DRUG_ID as "drug_id",
                dr.REFERENCE_ID as "reference_id",
                dr.PRIORITY as "priority"
               )
              -- No child tables for XDAGENTEST.DRUG_REFERENCE
              -- No parent tables for XDAGENTEST.DRUG_REFERENCE
             ) row_xml
           from XDAGENTEST.DRUG_REFERENCE dr
         ) dr_row
        where
          dr_row.DRUG_ID = d.ID
       ) -- child subquery
      --  parent tables for XDAGENTEST.DRUG
      ,(select -- rows of XDAGENTEST.COMPOUND
          -- row_xml
          xmlelement(name "compound"
           ,xmlforest(
             c.ID as "id",
             c.DISPLAY_NAME as "display_name",
             c.NCTR_ISIS_ID as "nctr_isis_id",
             c.SMILES as "smiles",
             c.CANONICAL_SMILES as "canonical_smiles",
             c.CAS as "cas",
             c.MOL_FORMULA as "mol_formula",
             c.MOL_WEIGHT as "mol_weight",
             c.MOL_FILE as "mol_file",
             c.INCHI as "inchi",
             c.INCHI_KEY as "inchi_key",
             c.STANDARD_INCHI as "standard_inchi",
             c.STANDARD_INCHI_KEY as "standard_inchi_key"
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
 ) d_row
