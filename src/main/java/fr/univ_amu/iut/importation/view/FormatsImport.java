package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.sites.model.Site;

/// Libellés et statut **purs** de l'assistant d'import : libellé d'un site (combo de rattachement), état
/// de nommage inspecté, phrase de statut du wizard, et zones de la barre de statut (#1024). Extrait de
/// [ImportationController] (déjà volumineux) pour le garder sous le plafond de taille (PMD `NcssCount`).
final class FormatsImport {

    private FormatsImport() {}

    /// Libellé d'un site dans la combo de rattachement : « Carré N » ou « Carré N - nom convivial ».
    static String libelleSite(Site site) {
        return site.nomConvivial() == null
                ? "Carré " + site.numeroCarre()
                : "Carré " + site.numeroCarre() + " - " + site.nomConvivial();
    }

    /// État du nommage des fichiers inspectés, dit par **ce qu'on va en faire** (#1487).
    ///
    /// ## Pourquoi le libellé nomme des opérations plutôt qu'un état
    ///
    /// « Fichiers bruts (seront renommés) » se lit « on va renommer **mes** fichiers ». Ce sont les
    /// copies, et la phrase qui le disait a rejoint les réglages avancés avec la case « Conserver les
    /// originaux » : elle y est parfaite, et l'utilisateur inquiet ne l'ouvrira jamais. Ce libellé est le
    /// seul qu'il lira, et c'est le mot **« copiés »** qui lève la crainte.
    ///
    /// Les opérations sont nommées **dans l'ordre où elles se produisent**, et seulement celles qui ont
    /// lieu : des fichiers déjà préfixés ne sont pas renommés, les annoncer tels remplacerait une phrase
    /// trompeuse par une autre.
    ///
    /// Vaut pour le chemin de **l'assistant**. « J'ai déjà les transformés… » est une action séparée
    /// ([ActionImportTransformes]) qui ne passe pas par cette inspection, donc jamais par ce libellé.
    static String libelleNommage(EtatNommage etat) {
        if (etat == null) {
            return Formats.VALEUR_ABSENTE;
        }
        return switch (etat) {
            case BRUT -> "fichiers bruts (seront copiés, renommés et transformés)";
            case PREFIXE -> "fichiers déjà préfixés (seront copiés et transformés)";
            case VIDE -> "aucun fichier";
        };
    }

    /// Phrase de statut du wizard (annulé / mono-nuit / multi-nuits). Bornée : les avertissements
    /// (doublon, rejets, anomalies) relèvent du compte rendu, pas de la barre de statut.
    static String libelle(EtatImport etat, ResultatImport resultat, ResultatImportMultiNuits resultatNuits) {
        return CompteRenduImport.statut(etat, resultat, resultatNuits);
    }

    /// Zones de la barre de statut : statut du wizard au **centre**, progression + ETA à droite pendant un
    /// traitement (import / décompression). Agrégat racine → gauche laissée au défaut du chrome.
    static ZonesStatut zones(
            EtatImport etat,
            ResultatImport resultat,
            ResultatImportMultiNuits resultatNuits,
            String progressionMessage) {
        boolean traitement = etat == EtatImport.EN_COURS || etat == EtatImport.EXTRACTION;
        String droite = traitement ? progressionMessage : "";
        return ZonesStatut.centreEtDroite(libelle(etat, resultat, resultatNuits), droite);
    }
}
