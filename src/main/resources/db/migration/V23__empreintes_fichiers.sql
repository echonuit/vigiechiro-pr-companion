-- V23 - Taille et empreinte de contenu des fichiers audio (#1299, EPIC #1297).
--
-- La reactivation d'un passage archive (#1302) consiste a rebrancher des observations sur des
-- fichiers reimportes. Sans preuve d'identite, deux jeux de fichiers homonymes (redecoupe, autre
-- expansion, autre nuit du meme carre) se confondent : le rebranchement produirait un resultat
-- scientifiquement faux, et silencieusement faux. On pose donc a l'import, pour chaque sequence :
--
-- - size_bytes : la taille du fichier, discriminant quasi gratuit (deja calculee a l'import pour
--   les totaux de session, jamais persistee par ligne jusqu'ici)
-- - content_fingerprint : SHA-256 hexadecimal des 64 premiers Kio du fichier (en-tete WAV + debut
--   du signal), cf. Empreintes.empreinteCourte. Une empreinte integrale couterait ~20 fois plus de
--   lectures pour un gain de discrimination nul sur ce cas d'usage - la taille attrape ce que la
--   fenetre ne voit pas (troncature au-dela des 64 Kio).
--
-- original_recording porte deja sha256 (empreinte integrale, posee a l'import depuis toujours) :
-- on n'y ajoute que size_bytes, pre-controle rapide avant un eventuel hachage complet quand la
-- reactivation repart des bruts.
--
-- Toutes les colonnes sont NULLABLES : les lignes importees avant cette migration n'ont pas
-- d'empreinte. Un retro-remplissage APPLICATIF (BackfillEmpreintes, en Java) renseigne celles dont
-- les fichiers sont encore sur disque - les autres restent explicitement a NULL, et l'IHM (issue D)
-- doit dire que ces passages ne seront pas reactivables par empreinte (la cascade #1309 reste
-- possible).
--
-- NB : aucun point-virgule dans ces commentaires. Le decoupeur de MigrationSchema coupe les
-- instructions sur CHAQUE point-virgule, commentaires compris - un « ; » ici casse la migration.
ALTER TABLE listening_sequence ADD COLUMN size_bytes INTEGER;
ALTER TABLE listening_sequence ADD COLUMN content_fingerprint TEXT;
ALTER TABLE original_recording ADD COLUMN size_bytes INTEGER;
