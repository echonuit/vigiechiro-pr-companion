-- V19 - Disposition des colonnes memorisee par ecran (#994, couche « defaut par ecran »).
--
-- Le selecteur de colonnes (#914) est desormais persistant : l'ordre et la visibilite choisis sur une
-- table sont retenus et restaures a la reouverture de l'ecran, independamment des vues nommees (#623,
-- qui capturent leur propre disposition dans saved_filter_view). Une ligne par (feature, table) : un
-- ecran mono-table n'a qu'une entree (table_key = 'principale'), l'analyse en a une par table
-- (especes / carres / observations).
--
-- Le layout est un descripteur JSON opaque cote base (produit par DescripteurColonnesJson), comme le
-- descriptor_json des vues : la base ne l'interprete pas.
CREATE TABLE column_layout (
    feature     TEXT NOT NULL,  -- ecran/table proprietaire (audio, analyse, multisite, sites, qualification, lot)
    table_key   TEXT NOT NULL,  -- table au sein de l'ecran ('principale', ou especes/carres/observations)
    layout_json TEXT NOT NULL,  -- disposition serialisee (ordre + visibilite des colonnes)
    PRIMARY KEY (feature, table_key)
);
