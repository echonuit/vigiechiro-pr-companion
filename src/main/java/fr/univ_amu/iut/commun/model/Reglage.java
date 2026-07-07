package fr.univ_amu.iut.commun.model;

/// Un réglage applicatif persisté (une ligne de la table `app_setting`) : un couple
/// **clé/valeur** en texte, transverse aux features.
///
/// La `cle` est une clé naturelle (ex. `import.conserver-originaux`) ; la `valeur` porte la
/// donnée sérialisée en texte (`"true"` / `"false"`, un nombre…). Le modèle **ne l'interprète
/// pas** : la lecture typée (booléen…) est faite par le service [Reglages].
///
/// @param cle clé naturelle du réglage (obligatoire, colonne `cle`)
/// @param valeur valeur sérialisée en texte (obligatoire, colonne `valeur`)
public record Reglage(String cle, String valeur) {}
