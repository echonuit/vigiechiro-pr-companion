-- ============================================================================
-- Réglages applicatifs persistés (clé/valeur).
--
-- Jusqu'ici l'application n'avait aucun endroit où mémoriser une **préférence utilisateur**
-- durable (seule `saved_view` conservait des vues nommées). Ce besoin apparaît avec l'option
-- « conserver les originaux » de l'import, que l'utilisateur veut voir **mémorisée** d'un
-- lancement à l'autre.
--
-- Table volontairement **générique** (une ligne = un réglage), pour accueillir d'autres options
-- futures sans nouvelle migration : `cle` est une clé naturelle en TEXT (ex.
-- `import.conserver-originaux`), `valeur` porte la valeur sérialisée en texte (`"true"` /
-- `"false"`, un nombre, etc.). L'interprétation typée (booléen…) est faite côté service `Reglages`.
-- ============================================================================

CREATE TABLE app_setting (
  cle    TEXT PRIMARY KEY,
  valeur TEXT NOT NULL
);
