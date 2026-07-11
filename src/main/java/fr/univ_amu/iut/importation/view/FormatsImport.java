package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.sites.model.Site;

/// Libellés et statut **purs** de l'assistant d'import : libellé d'un site (combo de rattachement), état
/// de nommage inspecté, phrase de statut du wizard, et zones de la barre de statut (#1024). Extrait de
/// [ImportationController] (déjà volumineux) pour le garder sous le plafond de taille (PMD `NcssCount`).
final class FormatsImport {

    private FormatsImport() {}

    /// Libellé d'un site dans la combo de rattachement : « Carré N » ou « Carré N — nom convivial ».
    static String libelleSite(Site site) {
        return site.nomConvivial() == null
                ? "Carré " + site.numeroCarre()
                : "Carré " + site.numeroCarre() + " — " + site.nomConvivial();
    }

    /// État du nommage des fichiers inspectés (bruts à renommer / déjà préfixés / aucun).
    static String libelleNommage(EtatNommage etat) {
        if (etat == null) {
            return "—";
        }
        return switch (etat) {
            case BRUT -> "fichiers bruts (seront renommés)";
            case PREFIXE -> "fichiers déjà préfixés";
            case VIDE -> "aucun fichier";
        };
    }

    /// Phrase de statut du wizard (issue de l'import : annulé / mono-nuit / multi-nuits), via [RecapImport].
    static String libelle(EtatImport etat, ResultatImport resultat, ResultatImportMultiNuits resultatNuits) {
        return RecapImport.libelle(etat, resultat, resultatNuits);
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
