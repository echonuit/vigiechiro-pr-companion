package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.ComptageAudio;
import fr.univ_amu.iut.audio.viewmodel.ComptageEnjeu;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import java.util.List;
import java.util.Optional;

/// Chrome de la vue audio unifiée, **piloté par la source** (#1194) : le fil d'Ariane et les zones
/// de la barre de statut, fonctions pures de la source et du comptage.
final class ChromeAudio {

    private ChromeAudio() {
        // Fonctions pures : jamais instanciée.
    }

    /// Emplacement dans le fil d'Ariane : pour `ParPassage`, on reconstruit les ancêtres site/passage
    /// (retour au passage via les contrats socle, comme l'ancienne validation) ; pour `ParEspece` et
    /// `ParPassages`, le segment parent rouvre l'écran d'origine (analyse, multisite) ; pour les autres
    /// sources (`References`…), l'écran est autonome (segment courant seul).
    static List<Lieu> emplacement(
            SourceObservations source,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            Optional<OuvrirAnalyse> ouvrirAnalyse,
            OuvrirMultisite ouvrirMultisite) {
        // ParPassage cible un passage : ascendance site › passage › écran (retour au passage).
        var contextePassage = source.contexteDuPassage();
        if (contextePassage != null) {
            return EmplacementPassage.emplacementEnfant(contextePassage, ouvrirSite, ouvrirPassage, source.titre());
        }
        if (source instanceof SourceObservations.ParEspece) {
            // Accueil › Espèces & observations › Écoute : [espèce], le segment analyse rouvre l'écran, sauf
            // si la feature `analyse` est désactivable et coupée (#1087) : le segment ne fait alors rien.
            return List.of(
                    Lieu.vers("Espèces & observations", () -> ouvrirAnalyse.ifPresent(OuvrirAnalyse::ouvrir)),
                    Lieu.courant(source.titre()));
        }
        if (source instanceof SourceObservations.ParPassages) {
            // Accueil › Carte & passages › Écoute : lot, le segment multisite rouvre la vue agrégée.
            return List.of(
                    Lieu.vers("Carte & passages", () -> ouvrirMultisite.ouvrirSurCarre(null)),
                    Lieu.courant(source.titre()));
        }
        return List.of(Lieu.courant(source.titre()));
    }

    /// Zones de la **barre de statut** : le total d'observations en **centre**, l'avancement de la revue à
    /// **droite** (« N observation(s) » · « X / N revues »). Zone gauche = identité (#1025) : le passage
    /// ciblé (Carré · point · N°) s'il y en a un, sinon l'intitulé de la source (Références, Sons non
    /// identifiés…). Sans le nom d'écran (déjà porté par le fil d'Ariane). [ZonesStatut#VIDE] tant que la
    /// vue n'est pas ouverte sur une source.
    static ZonesStatut zonesStatut(SourceObservations source, ComptageAudio comptage, ComptageEnjeu enjeu) {
        if (source == null) {
            return ZonesStatut.VIDE;
        }
        var passage = source.contexteDuPassage();
        String gauche = passage != null ? passage.identiteStatut() : source.titre();
        String centre = comptage.total() == 0 ? "Aucune observation" : comptage.total() + " observation(s)";
        // Le compteur d'espèces à enjeu (#2353) rejoint la progression, à droite : les deux répondent à
        // « que me reste-t-il à faire ? ». Il s'efface quand il n'y en a aucune, plutôt que d'occuper la
        // place avec un « 0 à enjeu » qu'on finirait par ne plus lire.
        String progression = comptage.total() == 0 ? "" : comptage.progression();
        String droite = enjeu.libelle().isEmpty() ? progression : progression + " · " + enjeu.libelle();
        return new ZonesStatut(gauche, centre, droite);
    }
}
