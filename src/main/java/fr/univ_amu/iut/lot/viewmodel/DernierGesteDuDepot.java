package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import java.util.ArrayList;
import java.util.List;

/// Le **dernier geste** d'un dépôt : son nom, et s'il reste à faire.
///
/// Ce geste change de sens (#984) : « Marquer déposé » quand le dépôt se fait à la main, « Lancer la
/// participation » dès qu'une participation est liée, l'application ayant alors déjà déposé la nuit.
/// Le bouton et le titre de la carte le disaient ; le fil d'étapes lisait une liste figée et nommait le
/// même geste « Marquer déposé » au-dessus d'eux (#5859).
///
/// Le renommer ne suffisait pas. Le fil rend toutes ses étapes franchies dès que la nuit est sur la
/// plateforme : une puce « Lancer la participation » se serait affichée faite au-dessus d'un bouton
/// encore offert, ce que la recette avait déjà relevé (S4-C03). Le fil suit donc l'**état** du bouton
/// avec son nom : l'étape est courante tant qu'il est offert, franchie quand l'analyse est demandée ou
/// faite.
public enum DernierGesteDuDepot {

    /// Le dépôt s'est fait à la main, hors de l'application : il reste à le marquer.
    MARQUER_DEPOSE("Marquer déposé"),

    /// L'application a déposé la nuit, et l'analyse n'est pas encore demandée.
    LANCER_LA_PARTICIPATION("Lancer la participation"),

    /// L'analyse est demandée ou faite : le bouton n'est plus offert, son geste est accompli.
    PARTICIPATION_LANCEE("Lancer la participation");

    private final String nom;

    DernierGesteDuDepot(String nom) {
        this.nom = nom;
    }

    /// Le nom que le bouton de la dernière étape porte.
    public String nom() {
        return nom;
    }

    /// Le fil d'étapes tel que ce geste le fait lire : sa dernière étape prend le nom du geste, et reste
    /// **courante** quand la nuit est déposée mais que la participation reste à lancer.
    ///
    /// Le calcul des étapes ne connaît que le statut du passage, et rend tout franchi dès que la nuit est
    /// sur la plateforme. C'est ce fil-là qui est corrigé ici, à l'affichage : un fil qui n'est pas tout
    /// franchi garde ses états, le dernier geste n'y déplaçant rien.
    ///
    /// @param etapes les étapes calculées, libellées « N · Nom »
    public List<EtapeDepot> appliquerAuFil(List<EtapeDepot> etapes) {
        if (etapes.isEmpty()) {
            return etapes;
        }
        List<EtapeDepot> fil = new ArrayList<>(etapes);
        int dernier = fil.size() - 1;
        boolean toutFranchi = etapes.stream().allMatch(etape -> etape.etat() == EtatEtape.FRANCHIE);
        EtatEtape etat = toutFranchi && this == LANCER_LA_PARTICIPATION
                ? EtatEtape.COURANTE
                : fil.get(dernier).etat();
        fil.set(dernier, new EtapeDepot(fil.size() + " · " + nom, etat));
        return List.copyOf(fil);
    }

    /// Le geste, d'après ce que l'écran sait du passage.
    ///
    /// @param participationLiee une participation est liée au passage
    /// @param analyseDemandeeOuFaite l'analyse est demandée à Vigie-Chiro, ou déjà rendue
    public static DernierGesteDuDepot de(boolean participationLiee, boolean analyseDemandeeOuFaite) {
        if (!participationLiee) {
            return MARQUER_DEPOSE;
        }
        return analyseDemandeeOuFaite ? PARTICIPATION_LANCEE : LANCER_LA_PARTICIPATION;
    }
}
