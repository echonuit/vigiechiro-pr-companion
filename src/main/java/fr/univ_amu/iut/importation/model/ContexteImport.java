package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;

/// Invariants d'**une opération d'import** (mono- ou multi-nuits) : ce qui ne change pas d'une nuit à
/// l'autre au sein du même import. Regroupé en objet-paramètre pour que le cœur [MoteurImport#importerUneNuit]
/// reste sous la limite de paramètres (PMD `ExcessiveParameterList`) sans rien masquer.
///
/// Une seule inspection de la carte (`rapport`/`journal`/`sansJournal`) est partagée par toutes les nuits ;
/// seuls le [Prefixe], la sous-liste de WAV et la date varient d'une nuit à l'autre (passés à part).
///
/// @param rapport inspection (lecture seule) de la carte SD, commune à toutes les nuits
/// @param journal journal LogPR parsé (ou repli #107), commun à toutes les nuits
/// @param sansJournal `true` si le journal est un repli synthétique (aucun `LogPR` présent)
/// @param dossierSource racine de la carte SD (lecture seule, R9)
/// @param idPoint point d'écoute de rattachement (commun à toutes les nuits)
/// @param conserverOriginaux `true` = copie protégée dans `bruts/` ; `false` = lecture en place (#…)
/// @param ecraser `true` = remplacement d'un passage existant au même quadruplet (#214), jamais en multi-nuits
/// @param jeton jeton d'annulation coopératif (#146)
record ContexteImport(
        RapportInspection rapport,
        JournalParse journal,
        boolean sansJournal,
        Path dossierSource,
        Long idPoint,
        boolean conserverOriginaux,
        boolean ecraser,
        JetonAnnulation jeton) {}
