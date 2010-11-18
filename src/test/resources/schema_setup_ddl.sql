-- One time setup for local testing schema.

/* Create user, e.g. as system on Oracle XE:
create user xdagentest identified by xdagentest;
grant connect, resource to xdagentest;
grant create table to xdagentest;
*/

CREATE TABLE Drug
	    ( 
	     id INTEGER  NOT NULL , 
	     name VARCHAR2 (500)  NOT NULL , 
	     compound_id INTEGER  NOT NULL , 
	     mesh_id VARCHAR2 (7) , 
	     drugbank_id VARCHAR2 (7) , 
	     cid INTEGER , 
	     therapeutic_indications VARCHAR2 (4000)
	     )
;
	
CREATE TABLE Compound 
	    ( 
	     id INTEGER  NOT NULL , 
	     display_name VARCHAR2 (50) , 
	     nctr_isis_id VARCHAR2 (100) , 
	     smiles VARCHAR2 (2000) , 
	     canonical_smiles VARCHAR2 (2000) , 
	     cas VARCHAR2 (50) , 
	     mol_formula VARCHAR2 (2000) , 
	     mol_weight NUMBER , 
	     mol_file CLOB , 
	     inchi VARCHAR2 (2000) , 
	     inchi_key VARCHAR2 (27) , 
	     standard_inchi VARCHAR2 (2000) , 
	     standard_inchi_key VARCHAR2 (27) 
	    )
;
	
CREATE TABLE Drug_Reference 
	    ( 
	     drug_id INTEGER  NOT NULL , 
	     reference_id INTEGER  NOT NULL , 
	     priority INTEGER 
	    )
;
	
CREATE TABLE Reference 
	    ( 
	     id INTEGER  NOT NULL , 
	     publication VARCHAR2 (2000)  NOT NULL 
	    )
;
	
	
CREATE TABLE Advisory 
	    ( 
	     id INTEGER  NOT NULL , 
	     drug_id INTEGER  NOT NULL , 
	     advisory_type_id INTEGER  NOT NULL , 
	     text VARCHAR2 (2000)  NOT NULL 
	    )
;
	
CREATE TABLE Advisory_Type 
	    ( 
	     id INTEGER  NOT NULL , 
	     name VARCHAR2 (50)  NOT NULL , 
	     authority_id INTEGER  NOT NULL 
	    )
;
	
CREATE TABLE Authority 
	    ( 
	     id INTEGER  NOT NULL , 
	     name VARCHAR2 (200)  NOT NULL , 
	     url VARCHAR2 (500) , 
	     description VARCHAR2 (2000) 
	    )
;
	
	
CREATE TABLE Brand 
	    ( 
	     drug_id INTEGER  NOT NULL , 
	     brand_name VARCHAR2 (200)  NOT NULL , 
	     language_code VARCHAR2 (10) , 
	     manufacturer_id INTEGER 
	    )
;
	
	
CREATE TABLE Drug_Functional_Category 
	    ( 
	     drug_id INTEGER  NOT NULL , 
	     functional_category_id INTEGER  NOT NULL , 
	     authority_id INTEGER  NOT NULL , 
	     seq INTEGER 
	    )
;
	
	
	
CREATE TABLE Functional_Category 
	    ( 
	     id INTEGER  NOT NULL , 
	     name VARCHAR2 (500)  NOT NULL , 
	     description VARCHAR2 (2000) , 
	     parent_functional_category_id INTEGER 
	    )
;
	
	
CREATE TABLE Manufacturer 
	    ( 
	     id INTEGER  NOT NULL , 
	     name VARCHAR2 (200)  NOT NULL 
	    )
;
	
	
	
	
CREATE INDEX Advisory_advtype_IX ON Advisory 
	    ( 
	     advisory_type_id ASC 
	    ) 
;
CREATE INDEX Advisory_drug_IX ON Advisory 
	    ( 
	     drug_id ASC 
	    ) 
;
	
	
ALTER TABLE Advisory 
	    ADD CONSTRAINT Advisory_PK PRIMARY KEY ( id )
;
	
	
	
ALTER TABLE Advisory_Type 
	    ADD CONSTRAINT Advisory_Type_PK PRIMARY KEY ( id )
;
	
ALTER TABLE Advisory_Type 
	    ADD CONSTRAINT Advisory_Type_name_UN UNIQUE ( name ) 
;
	
	
ALTER TABLE Authority 
	    ADD CONSTRAINT Authority_PK PRIMARY KEY ( id )
;
	
ALTER TABLE Authority 
	    ADD CONSTRAINT Authority_name_UN UNIQUE ( name ) 
;
	
	
CREATE INDEX Brand_mfr_IX ON Brand 
	    ( 
	     manufacturer_id ASC 
	    ) 
;
	
ALTER TABLE Brand 
	    ADD CONSTRAINT Brand_PK PRIMARY KEY ( drug_id, brand_name ) ;
	
	
CREATE INDEX Compound_inchikey_IX ON Compound 
	    ( 
	     inchi_key ASC 
	    ) 
;
CREATE INDEX Compound_stdinchikey_IX ON Compound 
	    ( 
	     standard_inchi_key ASC 
	    ) 
;
CREATE INDEX Compound_canonsmiles_IX ON Compound 
	    ( 
	     canonical_smiles ASC 
	    ) 
;
	
ALTER TABLE Compound 
	    ADD CONSTRAINT Compound_PK PRIMARY KEY ( id ) ;
	
	
CREATE INDEX Drug_compoundid_IX ON Drug 
	    ( 
	     compound_id ASC 
	    ) 
;
	
ALTER TABLE Drug 
	    ADD CONSTRAINT Drug_PK PRIMARY KEY ( id ) ;
	
ALTER TABLE Drug 
	    ADD CONSTRAINT Drug_name_UN UNIQUE ( name ) 
;
	
ALTER TABLE Drug 
	    ADD CONSTRAINT Drug_meshid_UN UNIQUE ( mesh_id ) 
;
	
ALTER TABLE Drug 
	    ADD CONSTRAINT Drug_drugbankid_UN UNIQUE ( drugbank_id ) 
;
	
	
CREATE INDEX DrugFunCat_funcat_IX ON Drug_Functional_Category 
	    ( 
	     functional_category_id ASC 
	    ) 
;
CREATE INDEX DrugFunCat_authority_IX ON Drug_Functional_Category 
	    ( 
	     authority_id ASC 
	    ) 
;
	
ALTER TABLE Drug_Functional_Category 
	    ADD CONSTRAINT DrugFunCat_PK PRIMARY KEY ( drug_id, functional_category_id, authority_id ) ;
	
	
	
	
CREATE INDEX Drug_Reference_referenceid_IX ON Drug_Reference 
	    ( 
	     reference_id ASC 
	    ) 
;
	
ALTER TABLE Drug_Reference 
	    ADD CONSTRAINT Drug_Reference_PK PRIMARY KEY ( drug_id, reference_id ) ;
	
	
	
	
CREATE INDEX FunCat_parentfuncat_IX ON Functional_Category 
	    ( 
	     parent_functional_category_id ASC 
	    ) 
;
	
ALTER TABLE Functional_Category 
	    ADD CONSTRAINT Category_PK PRIMARY KEY ( id ) ;
	
ALTER TABLE Functional_Category 
	    ADD CONSTRAINT Functional_Category_name_UN UNIQUE ( name ) 
;
	
	
	
ALTER TABLE Manufacturer 
	    ADD CONSTRAINT Manufacturer_PK PRIMARY KEY ( id ) ;
	
ALTER TABLE Manufacturer 
	    ADD CONSTRAINT Manufacturer_name_UN UNIQUE ( name ) 
;
	
	
ALTER TABLE Reference 
	    ADD CONSTRAINT Reference_PK PRIMARY KEY ( id ) ;
	
	
	
ALTER TABLE Advisory 
	    ADD CONSTRAINT Advisory_Advisory_Type_FK FOREIGN KEY 
	    ( 
	     advisory_type_id
	    ) 
	    REFERENCES Advisory_Type 
	    ( 
	     id
	    ) 
;
	
	
ALTER TABLE Advisory 
	    ADD CONSTRAINT Advisory_Drug_FK FOREIGN KEY 
	    ( 
	     drug_id
	    ) 
	    REFERENCES Drug 
	    ( 
	     id
	    ) 
	    ON DELETE CASCADE 
;
	
	
ALTER TABLE Advisory_Type 
	    ADD CONSTRAINT Advisory_Type_Authority_FK FOREIGN KEY 
	    ( 
	     authority_id
	    ) 
	    REFERENCES Authority 
	    ( 
	     id
	    ) 
;
	
	
ALTER TABLE Brand 
	    ADD CONSTRAINT Brand_Drug_FK FOREIGN KEY 
	    ( 
	     drug_id
	    ) 
	    REFERENCES Drug 
	    ( 
	     id
	    ) 
	    ON DELETE CASCADE 
;
	
	
ALTER TABLE Brand 
	    ADD CONSTRAINT Brand_Manufacturer_FK FOREIGN KEY 
	    ( 
	     manufacturer_id
	    ) 
	    REFERENCES Manufacturer 
	    ( 
	     id
	    ) 
;
	
	
ALTER TABLE Drug_Functional_Category 
	    ADD CONSTRAINT DrugFunCat_Authority_FK FOREIGN KEY 
	    ( 
	     authority_id
	    ) 
	    REFERENCES Authority 
	    ( 
	     id
	    ) 
;
	
	
ALTER TABLE Drug_Functional_Category 
	    ADD CONSTRAINT Drug_Category_Drug_FK FOREIGN KEY 
	    ( 
	     drug_id
	    ) 
	    REFERENCES Drug 
	    ( 
	     id
	    ) 
	    ON DELETE CASCADE 
;
	
	
ALTER TABLE Drug 
	    ADD CONSTRAINT Drug_Compound_FK FOREIGN KEY 
	    ( 
	     compound_id
	    ) 
	    REFERENCES Compound 
	    ( 
	     id
	    ) 
;
	
	
ALTER TABLE Drug_Functional_Category 
	    ADD CONSTRAINT Drug_FunCat_funcat_FK FOREIGN KEY 
	    ( 
	     functional_category_id
	    ) 
	    REFERENCES Functional_Category 
	    ( 
	     id
	    ) 
;
	
	
ALTER TABLE Drug_Reference 
	    ADD CONSTRAINT Drug_Reference_Drug_FK FOREIGN KEY 
	    ( 
	     drug_id
	    ) 
	    REFERENCES Drug 
	    ( 
	     id
	    ) 
	    ON DELETE CASCADE 
;
	
	
ALTER TABLE Drug_Reference 
	    ADD CONSTRAINT Drug_Reference_Reference_FK FOREIGN KEY 
	    ( 
	     reference_id
	    ) 
	    REFERENCES Reference 
	    ( 
	     id
	    ) 
	    ON DELETE CASCADE 
;
	
	
ALTER TABLE Functional_Category 
	    ADD CONSTRAINT FunCat_FunCat_FK FOREIGN KEY 
	    ( 
	     parent_functional_category_id
	    ) 
	    REFERENCES Functional_Category 
	    ( 
	     id
	    ) 
;
