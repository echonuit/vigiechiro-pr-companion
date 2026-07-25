package fr.univ_amu.iut.passage.model;

/// Signature de contenu d'un fichier audio : sa **taille** et son **empreinte** (hash), regroupées
/// parce qu'elles voyagent toujours ensemble et jouent le même rôle - prouver l'identité d'un fichier à
/// moindre coût. La taille est le pré-contrôle (comparaison sans lecture) ; l'empreinte tranche (cf.
/// [IdentiteSequence]). Les deux sont **nullables** dans les mêmes cas : un fichier importé avant que
/// ces colonnes n'existent (V23) et non rétro-rempli n'a ni l'une ni l'autre.
///
/// Type valeur **générique** : il porte *un* hash de contenu, sans préjuger duquel. Une séquence
/// d'écoute y met son empreinte **courte** (64 Kio, [Empreintes#empreinteCourte]) ; un enregistrement
/// original son SHA-256 **complet** (intégrité bit-à-bit). Regrouper `Long` et `String` sous un nom
/// évite qu'ils se confondent, à la construction, avec les autres `Long` (clés étrangères) et `String`
/// (noms, chemins) des records qui les portent (EPIC #2483).
///
/// @param tailleOctets taille du fichier en octets, ou `null` si inconnue
/// @param empreinte empreinte hexadécimale du contenu, ou `null` si inconnue
public record EmpreinteContenu(Long tailleOctets, String empreinte) {

    /// Signature **absente** : ni taille ni empreinte connues (fichier importé avant V23 et non
    /// rétro-rempli). L'identité n'est alors vérifiable que par la cascade de preuves, pas par ce couple.
    public static final EmpreinteContenu ABSENTE = new EmpreinteContenu(null, null);
}
