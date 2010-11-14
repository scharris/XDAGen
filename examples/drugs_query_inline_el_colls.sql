select -- rows of ltkb.drug
  -- row_xml
  xmlserialize(content xmlelement("drug", xmlattributes('http://example/namespace' as "xmlns")
   ,xmlelement("id", d.ID)
   ,xmlelement("name", d.NAME)
   ,xmlelement("compound_id", d.COMPOUND_ID)
   ,xmlelement("mesh_id", d.MESH_ID)
   ,xmlelement("drugbank_id", d.DRUGBANK_ID)
   ,xmlelement("cid", d.CID)
   ,xmlelement("therapeutic_indications", d.THERAPEUTIC_INDICATIONS)
   --  child tables for ltkb.drug
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_histopath_class
          dhc.*,
          -- row_xml
          xmlelement("drug_histopath_class"
           ,xmlelement("drug_id", dhc.DRUG_ID)
           ,xmlelement("histopath_cass_id", dhc.HISTOPATH_CASS_ID)
           -- No child tables for ltkb.drug_histopath_class
           -- No parent tables for ltkb.drug_histopath_class
          ) row_xml
        from ltkb.drug_histopath_class dhc
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.brand
          b.*,
          -- row_xml
          xmlelement("brand"
           ,xmlelement("drug_id", b.DRUG_ID)
           ,xmlelement("brand_name", b.BRAND_NAME)
           ,xmlelement("language_code", b.LANGUAGE_CODE)
           ,xmlelement("manufacturer_id", b.MANUFACTURER_ID)
           -- No child tables for ltkb.brand
           -- No parent tables for ltkb.brand
          ) row_xml
        from ltkb.brand b
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_functional_category
          dfc.*,
          -- row_xml
          xmlelement("drug_functional_category"
           ,xmlelement("drug_id", dfc.DRUG_ID)
           ,xmlelement("functional_category_id", dfc.FUNCTIONAL_CATEGORY_ID)
           ,xmlelement("authority_id", dfc.AUTHORITY_ID)
           ,xmlelement("seq", dfc.SEQ)
           -- No child tables for ltkb.drug_functional_category
           -- No parent tables for ltkb.drug_functional_category
          ) row_xml
        from ltkb.drug_functional_category dfc
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_link
          dl.*,
          -- row_xml
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
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.advisory
          a.*,
          -- row_xml
          xmlelement("advisory"
           ,xmlelement("id", a.ID)
           ,xmlelement("drug_id", a.DRUG_ID)
           ,xmlelement("advisory_type_id", a.ADVISORY_TYPE_ID)
           ,xmlelement("text", a.TEXT)
           -- No child tables for ltkb.advisory
           -- No parent tables for ltkb.advisory
          ) row_xml
        from ltkb.advisory a
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_market_status
          dms.*,
          -- row_xml
          xmlelement("drug_market_status"
           ,xmlelement("drug_id", dms.DRUG_ID)
           ,xmlelement("route_id", dms.ROUTE_ID)
           ,xmlelement("country_code", dms.COUNTRY_CODE)
           ,xmlelement("market_status_id", dms.MARKET_STATUS_ID)
           ,xmlelement("date", dms.date)
           -- No child tables for ltkb.drug_market_status
           -- No parent tables for ltkb.drug_market_status
          ) row_xml
        from ltkb.drug_market_status dms
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.atc_classification
          ac.*,
          -- row_xml
          xmlelement("atc_classification"
           ,xmlelement("drug_id", ac.DRUG_ID)
           ,xmlelement("code", ac.CODE)
           -- No child tables for ltkb.atc_classification
           -- No parent tables for ltkb.atc_classification
          ) row_xml
        from ltkb.atc_classification ac
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_synonym
          ds.*,
          -- row_xml
          xmlelement("drug_synonym"
           ,xmlelement("drug_id", ds.DRUG_ID)
           ,xmlelement("drug_synonym", ds.DRUG_SYNONYM)
           ,xmlelement("language_code", ds.LANGUAGE_CODE)
           -- No child tables for ltkb.drug_synonym
           -- No parent tables for ltkb.drug_synonym
          ) row_xml
        from ltkb.drug_synonym ds
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.fda_application
          fa.*,
          -- row_xml
          xmlelement("fda_application"
           ,xmlelement("fda_application_num", fa.FDA_APPLICATION_NUM)
           ,xmlelement("drug_id", fa.DRUG_ID)
           ,xmlelement("fda_doc_type", fa.FDA_DOC_TYPE)
           ,xmlelement("manufacturer_id", fa.MANUFACTURER_ID)
           ,xmlelement("fda_chemical_type_num", fa.FDA_CHEMICAL_TYPE_NUM)
           ,xmlelement("fda_document_type_code", fa.FDA_DOCUMENT_TYPE_CODE)
           -- No child tables for ltkb.fda_application
           -- No parent tables for ltkb.fda_application
          ) row_xml
        from ltkb.fda_application fa
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_liver_injury_score
          dlis.*,
          -- row_xml
          xmlelement("drug_liver_injury_score"
           ,xmlelement("id", dlis.ID)
           ,xmlelement("drug_id", dlis.DRUG_ID)
           ,xmlelement("route_id", dlis.ROUTE_ID)
           ,xmlelement("authority_id", dlis.AUTHORITY_ID)
           ,xmlelement("protocol_id", dlis.PROTOCOL_ID)
           ,xmlelement("score_code", dlis.SCORE_CODE)
           -- No child tables for ltkb.drug_liver_injury_score
           -- No parent tables for ltkb.drug_liver_injury_score
          ) row_xml
        from ltkb.drug_liver_injury_score dlis
      ) row_els_q
     where
       row_els_q.DRUG_ID = d.ID
    ) -- child subquery
   ,(select xmlagg(row_els_q.row_xml) "rowcoll_xml"
     from
      ( select -- rows of ltkb.drug_reference
          dr.*,
          -- row_xml
          xmlelement("drug_reference"
           ,xmlelement("drug_id", dr.DRUG_ID)
           ,xmlelement("reference_id", dr.REFERENCE_ID)
           ,xmlelement("priority", dr.PRIORITY)
           -- No child tables for ltkb.drug_reference
           -- No parent tables for ltkb.drug_reference
          ) row_xml
        from ltkb.drug_reference dr
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