-- V43 - Ce que la plateforme portait a NOTRE derniere lecture (#4706, EPIC #4640).
--
-- Constater un conflit demande TROIS valeurs : la base, la notre, la leur. Le depot n'en avait que
-- deux, et sans base « l'utilisateur a modifie la meteo » et « la plateforme l'a modifiee » sont
-- indiscernables. C'est pourquoi la garde de #4552 s'etait rabattue sur la seule question que deux
-- valeurs permettent : quelque chose a-t-il bouge pendant mon appel.
--
-- Cette table porte la base. Elle est ecrite par le tirage et par un envoi accepte, jamais par un
-- envoi refuse : un relevé pose sur un refus decrirait un etat distant qui n'existe pas.
--
-- MEME FORME QUE V22 (participation_traitement), et pour la meme raison : c'est un RELEVE horodate
-- de ce que la plateforme disait, pas une donnee de l'application. La borne de l'ADR 4640 s'applique
-- ici : ce releve ne dit pas ce qui est VRAI, il dit ce que nous avions VU. Il ne se montre jamais a
-- l'utilisateur comme une donnee, et il ne sert qu'a repondre « ce champ a-t-il change de notre
-- cote, du leur, ou des deux ».
--
-- Les champs retenus sont exactement ceux que le PATCH ecrit, moins ceux que l'application n'emet
-- pas : dates, meteo, configuration. Comparer un champ qu'on n'envoie pas ne servirait a rien.
CREATE TABLE participation_relevee (
    passage_id       INTEGER PRIMARY KEY REFERENCES passage(id) ON DELETE CASCADE,
    participation_id TEXT    NOT NULL,  -- objectid de la participation relevee (tracabilite)
    date_debut       TEXT,              -- debut de nuit tel que la plateforme le portait (RFC 1123)
    date_fin         TEXT,              -- fin de nuit, idem
    meteo_vent       TEXT,              -- NUL, FAIBLE, MOYEN ou FORT, ou NULL si absent chez eux
    meteo_couverture TEXT,              -- 0-25, 25-50, 50-75 ou 75-100, ou NULL si absent
    configuration    TEXT,              -- le dictionnaire materiel serialise en JSON, jamais NULL
    releve_le        TEXT    NOT NULL   -- horodatage ISO de NOTRE lecture
);
