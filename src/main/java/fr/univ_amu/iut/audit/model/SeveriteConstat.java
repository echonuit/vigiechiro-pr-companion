package fr.univ_amu.iut.audit.model;

/// Gravité d'un [ConstatAudit].
///
/// [#ERREUR] : incohérence bloquante (fichier attendu absent sous le workspace, préfixe non conforme,
/// unité déposée divergente). [#AVERTISSEMENT] : anomalie non bloquante (fichier optionnel absent,
/// orphelin sur disque). [#INFO] : information sans gravité (original externe non vérifiable, passage
/// jamais importé).
public enum SeveriteConstat {
    ERREUR,
    AVERTISSEMENT,
    INFO
}
