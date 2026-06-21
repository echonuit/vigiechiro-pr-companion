package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.util.ArrayList;
import java.util.List;

/// Fabrique partagée des segments du **fil d'Ariane** autour d'un passage, réutilisée par M-Passage
/// (écran pivot) et par ses écrans enfants (vérification, diagnostic, dépôt, validation).
///
/// Centralise la hiérarchie `Mes sites › Carré N › Détails du passage N° X` pour qu'elle soit
/// **identique quelle que soit la route** (depuis M-Sites comme depuis M-Multisite) et pour éviter de
/// dupliquer la construction des [Lieu] dans chaque écran. Les ancêtres sont cliquables via les
/// contrats socle [OuvrirSite] et [OuvrirPassage].
public final class EmplacementPassage {

    private EmplacementPassage() {}

    /// Ancêtres cliquables `Mes sites › Carré N` d'un passage, ou liste vide si le carré est inconnu
    /// (l'appelant retombe alors sur un fil minimal).
    public static List<Lieu> ancetresSite(ContexteSite site, OuvrirSite ouvrirSite) {
        if (site == null || site.numeroCarre() == null) {
            return List.of();
        }
        String carre = site.numeroCarre();
        return List.of(
                Lieu.vers("Mes sites", ouvrirSite::ouvrirListe),
                Lieu.vers("Carré " + carre, () -> ouvrirSite.ouvrirDetail(carre)));
    }

    /// Libellé du segment d'un passage (« Détails du passage N° X »), repli « Détails du passage » si le
    /// numéro est inconnu.
    public static String libellePassage(int numeroPassage) {
        return numeroPassage > 0 ? "Détails du passage N° " + numeroPassage : "Détails du passage";
    }

    /// Emplacement complet d'un **écran enfant** d'un passage : `Mes sites › Carré N › Détails du
    /// passage N° X › <écran>`. Le segment passage est cliquable (rouvre M-Passage) ; le dernier
    /// segment (l'écran courant) ne l'est pas. Si le contexte site est inconnu, retombe sur un fil
    /// minimal réduit à l'écran courant.
    public static List<Lieu> emplacementEnfant(
            ContextePassage passage, OuvrirSite ouvrirSite, OuvrirPassage ouvrirPassage, String libelleEcran) {
        if (passage == null || passage.site() == null || passage.site().numeroCarre() == null) {
            return List.of(Lieu.courant(libelleEcran));
        }
        List<Lieu> fil = new ArrayList<>(ancetresSite(passage.site(), ouvrirSite));
        fil.add(Lieu.vers(
                libellePassage(passage.numeroPassage()),
                () -> ouvrirPassage.ouvrir(passage.idPassage(), passage.site())));
        fil.add(Lieu.courant(libelleEcran));
        return List.copyOf(fil);
    }
}
