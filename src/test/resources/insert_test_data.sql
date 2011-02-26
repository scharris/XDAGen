begin
  insert into authority(id, name, url, description)
    values(1, 'FDA', 'http://www.fda.gov', 'Food and Drug Administration');
  insert into advisory_type(id, name, authority_id)
   values(1, 'Black Box Warning', 1);
  insert into advisory_type(id, name, authority_id)
   values(2, 'Caution', 1);
  insert into functional_category(id, name, description, parent_functional_category_id)
    values(1, 'Category A', 'Top level category A', null);
  insert into functional_category(id, name, description, parent_functional_category_id)
    values(2, 'Category A.1', 'sub category 1 of A', 1);
  insert into functional_category(id, name, description, parent_functional_category_id)
    values(3, 'Category A.1.1', 'sub category 1 of A.1', 2);
  insert into functional_category(id, name, description, parent_functional_category_id)
    values(4, 'Category B', 'Top level category B', null);
  insert into functional_category(id, name, description, parent_functional_category_id)
    values(5, 'Category B.1', 'sub category 1 of B', 4);
  insert into functional_category(id, name, description, parent_functional_category_id)
    values(6, 'Category B.1.1', 'sub category 1 of B.1', 5);
  insert into manufacturer(id, name)
    values(1, 'Acme Drug Co');
  insert into manufacturer(id, name)
    values(2, 'PharmaCorp');
  insert into manufacturer(id, name)
    values(3, 'SellsAll Drug Co.');

	insert all
	 into compound(id, display_name, nctr_isis_id)
	   values(n, 'Test Compound ' || n, 'DUMMY' || n)
	 into drug(id, name, compound_id, therapeutic_indications, spl_xml)
	   values(n, 'Test Drug ' || n, n, 'Indication ' || n, xmltype('<document><gen-name>drug ' || n || '</gen-name></document>'))
	 into reference(id, publication)
	   values(100*n + 1, 'Publication 1 about drug # ' || n)
	 into reference(id, publication)
	   values(100*n + 2, 'Publication 2 about drug # ' || n)
	 into reference(id, publication)
	   values(100*n + 3, 'Publication 3 about drug # ' || n)
	 into drug_reference (drug_id, reference_id, priority)
	   values(n, 100*n + 1, n)
	 into drug_reference (drug_id, reference_id, priority)
	   values(n, 100*n + 2, n)
	 into drug_reference (drug_id, reference_id, priority)
	   values(n, 100*n + 3, n)
	 into drug_functional_category(drug_id, functional_category_id, authority_id, seq)
	   values(n, mod(n,3)+1, 1, 1)
	 into drug_functional_category(drug_id, functional_category_id, authority_id, seq)
	   values(n, mod(n,3)+4, 1, 2)
	 into brand(drug_id, brand_name, language_code, manufacturer_id)
	   values(n, 'Brand'||n||'(TM)', 'EN', mod(n,3)+1)
	 into advisory(id, drug_id, advisory_type_id, text)
	   values(100*n+1, n, 1, 'Advisory concerning drug ' || n)
	 into advisory(id, drug_id, advisory_type_id, text)
	   values(100*n+2, n, 2, 'Caution concerning drug ' || n)
	select rownum n from dual connect by rownum <= 10;
end;