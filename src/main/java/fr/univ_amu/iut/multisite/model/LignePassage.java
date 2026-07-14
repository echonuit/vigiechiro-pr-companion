package fr.univ_amu.iut.multisite.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;

/// Ligne d'une vue agrégée multi-sites (parcours P5, épopée E5) : un passage **aplati** avec ses
/// informations clés pour un tableau haute densité (écran M-MultiSite). Chaque ligne croise les
/// trois features dont la vue dépend : le site et le point (`sites.model`), le passage
/// (`passage.model`).
///
/// Ce n'est pas une entité persistée : c'est une **projection en lecture seule** construite
/// par [ServiceMultisite] à partir des DAO de `sites` et `passage`. Les champs
/// reprennent exactement les colonnes que le tableau affiche (P5-CA2 : « triable et filtrable par
/// site, point, n° passage, statut, verdict et date »).
///
/// @param idPassage identifiant technique du passage (référence vers la fiche détaillée)
/// @param numeroCarre n° de carré du site d'appartenance (identifie le site, colonne « site »)
/// @param codePoint code du point d'écoute (lettre + chiffre, R2)
/// @param annee année du passage (4 chiffres)
/// @param numeroPassage n° de passage dans l'année (typiquement 1 ou 2, R3)
/// @param dateEnregistrement date d'enregistrement (ISO `AAAA-MM-JJ`)
/// @param statut statut d'avancement dans le workflow d'import → dépôt
/// @param verdict verdict de vérification (`null` tant que le passage n'a pas été vérifié)
/// @param etatAnalyse où en est l'analyse Tadarida de cette nuit (#1338), déduite du relevé daté du
///     serveur croisé avec la présence de résultats en base ; jamais `null`
/// @param analyseReleveeLe horodatage ISO de **notre dernière lecture** de l'état serveur, ou `null` si
///     on ne l'a jamais demandé. Le cache est un relevé daté, pas une vérité : la vue doit pouvoir dire
///     de quand l'information date (patron « État observé »)
public record LignePassage(
        Long idPassage,
        String numeroCarre,
        String codePoint,
        int annee,
        int numeroPassage,
        String dateEnregistrement,
        StatutWorkflow statut,
        Verdict verdict,
        EtatAnalyse etatAnalyse,
        String analyseReleveeLe) {}
