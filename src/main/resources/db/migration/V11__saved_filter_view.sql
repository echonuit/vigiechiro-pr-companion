-- E7.S1 - Vues memorisees generiques (#623) : un etat de filtres (descripteur serialise en JSON)
-- enregistre sous un nom, pour un ecran/table donne (colonne `feature` : audio / analyse / multisite).
-- Generalise `saved_view` (specifique multisite) a toutes les vues tabulaires ; table autonome, sans
-- cle etrangere (le descripteur est opaque cote base).
CREATE TABLE saved_filter_view (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  feature         TEXT NOT NULL,
  name            TEXT NOT NULL,
  descriptor_json TEXT NOT NULL
);
