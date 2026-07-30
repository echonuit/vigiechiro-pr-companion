package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import java.util.Map;
import java.util.Set;

/// État d'un site local vis-à-vis de la plateforme VigieChiro (#718, #734) :
/// - [#ABSENT] : aucune correspondance (le site n'est pas connu de la plateforme) ;
/// - [#ENREGISTRE] : correspondance établie mais site **non** verrouillé (en attente de validation) ;
/// - [#VERROUILLE] : site **verrouillé** côté plateforme, le dépôt d'une participation est possible.
///
/// L'enum porte **son propre badge** (libellé, famille de couleur, infobulle) : le même sur la carte de
/// « Mes sites » et sur le détail de site (#734). Ces libellés vivaient en dur dans `MesSitesController` ;
/// les recopier dans le second écran aurait fait diverger deux vérités.
///
/// L'infobulle porte, dans les trois cas, la **règle qui compte** : *un site doit être verrouillé pour
/// qu'on puisse y déposer*. « Verrouillé » désigne en effet un état **favorable**, ce que le seul libellé
/// laisse mal deviner (#801).
public enum StatutPlateforme {
    ABSENT(
            "Non enregistré sur Vigie-Chiro",
            "badge-neutre",
            "Ce carré n'est pas connu de Vigie-Chiro. Déclarez-le sur la plateforme : un site doit y être"
                    + " enregistré, puis verrouillé, pour que vous puissiez y déposer vos nuits."),
    ENREGISTRE(
            "Enregistré sur Vigie-Chiro",
            "badge-info",
            "Ce carré est enregistré sur Vigie-Chiro, mais il n'est pas encore verrouillé : le dépôt de vos"
                    + " nuits n'y est pas encore possible."),
    VERROUILLE(
            "Verrouillé sur Vigie-Chiro",
            "badge-succes",
            "Carré figé côté Vigie-Chiro : le dépôt de vos données y est désormais possible.");

    private final String libelle;
    private final String classeBadge;
    private final String infobulle;

    StatutPlateforme(String libelle, String classeBadge, String infobulle) {
        this.libelle = libelle;
        this.classeBadge = classeBadge;
        this.infobulle = infobulle;
    }

    /// Statut d'un site, **déduit** des correspondances VigieChiro : verrouillé l'emporte sur enregistré,
    /// qui l'emporte sur absent. Règle de précédence partagée par les deux écrans, plutôt que réécrite
    /// dans chacun.
    ///
    /// @param refLocale référence locale du site (son `id`, en chaîne)
    /// @param enregistres correspondances site local vers `_id` plateforme
    /// @param verrouilles références locales des sites dont la correspondance est verrouillée
    public static StatutPlateforme deduire(String refLocale, Map<String, String> enregistres, Set<String> verrouilles) {
        if (verrouilles.contains(refLocale)) {
            return VERROUILLE;
        }
        if (enregistres.containsKey(refLocale)) {
            return ENREGISTRE;
        }
        return ABSENT;
    }

    /// Statut d'**un seul** site : la variante à la demande de [#deduire], pour le détail de site. L'écran
    /// « Mes sites », lui, lit les correspondances une seule fois pour toute sa liste.
    public static StatutPlateforme duSite(Long idSite, LienVigieChiroDao liens) {
        return deduire(
                String.valueOf(idSite),
                liens.tous(LienVigieChiro.ENTITE_SITE),
                liens.verrouilles(LienVigieChiro.ENTITE_SITE));
    }

    /// Texte du badge (ex. « Verrouillé sur VigieChiro »).
    public String libelle() {
        return libelle;
    }

    /// Famille de couleur sémantique du badge, comme `Fraicheur#classeBadge` : la vue applique la classe,
    /// elle ne choisit pas la couleur.
    public String classeBadge() {
        return classeBadge;
    }

    /// Infobulle du badge : ce que l'état **autorise ou non**, pas seulement ce qu'il est.
    public String infobulle() {
        return infobulle;
    }

    /// `true` si le dépôt d'une nuit est possible sur ce site (site verrouillé côté plateforme).
    public boolean depotPossible() {
        return this == VERROUILLE;
    }
}
