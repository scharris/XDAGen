select -- rows of ltkb.drug
  -- row_xml
  xmlserialize(content xmlelement("drug"
   ,xmlelement("id", d.ID)
   ,xmlelement("name", d.NAME)
   ,xmlelement("compound_id", d.COMPOUND_ID)
   ,xmlelement("mesh_id", d.MESH_ID)
   ,xmlelement("drugbank_id", d.DRUGBANK_ID)
   ,xmlelement("cid", d.CID)
   ,xmlelement("therapeutic_indications", d.THERAPEUTIC_INDICATIONS)
   --  child tables for ltkb.drug
   ,(select xmlelement("drug_link-list", xmlagg(row_els_q.row_xml)) as "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_link
        dl.*,  -- row_xml
          xmlelement("drug_link"
           ,xmlelement("drug_id", dl.DRUG_ID)
           ,xmlelement("name", dl.NAME)
           ,xmlelement("href", dl.HREF)
           -- No child tables for ltkb.drug_link
           -- No parent tables for ltkb.drug_link
          ) row_xml
        from ltkb.drug_link dl
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   --  parent tables for ltkb.drug
   ,(select -- rows of ltkb.compound
       -- row_xml
       xmlelement("compound"
        ,xmlelement("id", c.ID)
        ,xmlelement("display_name", c.DISPLAY_NAME)
        ,xmlelement("nctr_isis_id", c.NCTR_ISIS_ID)
        ,xmlelement("smiles", c.SMILES)
        ,xmlelement("canonical_smiles", c.CANONICAL_SMILES)
        ,xmlelement("cas", c.CAS)
        ,xmlelement("mol_formula", c.MOL_FORMULA)
        ,xmlelement("mol_weight", c.MOL_WEIGHT)
        ,xmlelement("mol_file", c.MOL_FILE)
        ,xmlelement("inchi", c.INCHI)
        ,xmlelement("inchi_key", c.INCHI_KEY)
        ,xmlelement("standard_inchi", c.STANDARD_INCHI)
        ,xmlelement("standard_inchi_key", c.STANDARD_INCHI_KEY)
        -- No child tables for ltkb.compound
        -- No parent tables for ltkb.compound
       ) row_xml
     from ltkb.compound c
     where
       c.ID = d.COMPOUND_ID
    ) -- parent subquery
  ) as clob) row_xml
from ltkb.drug d