package fr.univ_amu.iut.saison.view;

import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/// Ce que la colonne « Hors protocole » de Ma saison écrit pour un point (#2525, #6106).
///
/// Une nuit opportuniste seule se nomme avec sa date. À partir de deux, la pastille **compte** les
/// nuits et leur détail passe dans l'infobulle de la cellule. Jusqu'à #6106 la pastille joignait une
/// entrée par nuit : dès la deuxième, la cellule la coupait par une ellipse, et la nuit disparaissait
/// de l'écran sans que rien ne le dise. Aucune largeur de colonne n'y répondait, le texte
/// s'allongeant avec le nombre de nuits. Un compte, lui, tient dans la colonne quel que soit ce nombre.
///
/// Une nuit porte aussi un statut et un verdict. Cette colonne n'en a jamais rien montré, et le
/// compte n'efface donc aucun état : pour le lire, on ouvre la nuit.
final class PastilleHorsProtocole {

    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    private PastilleHorsProtocole() {}

    /// Le texte de la pastille : la nuit et sa date s'il n'y en a qu'une, le nombre de nuits sinon.
    ///
    /// Le compte s'écrit « 2 nuits », sans redire « opportunistes » : l'en-tête de la colonne le dit
    /// déjà, et la forme longue déborde des 123 px de la cellule dès un compte à deux chiffres.
    ///
    /// **`null`** sans nuit hors protocole, le cas courant : la cellule badge
    /// ([fr.univ_amu.iut.commun.view.ColonneBadge]) le lit comme « rien à afficher », là où `""`
    /// lui ferait poser une pastille vide.
    static String libelle(LigneSaison ligne) {
        int combien = ligne.horsProtocole().size();
        if (combien == 0) {
            return null;
        }
        if (combien == 1) {
            return nuit(ligne.horsProtocole().get(0));
        }
        return combien + " nuits";
    }

    /// Le détail que le compte ne dit plus : une nuit par ligne, dans l'ordre où le solde les range.
    ///
    /// **`null`** jusqu'à une nuit. La pastille la nomme alors en entier, et une infobulle la
    /// répéterait mot pour mot.
    static String infobulle(LigneSaison ligne) {
        if (ligne.horsProtocole().size() < 2) {
            return null;
        }
        return ligne.horsProtocole().stream().map(PastilleHorsProtocole::nuit).collect(Collectors.joining("\n"));
    }

    private static String nuit(CasePassage cas) {
        return cas.date() == null
                ? "Opportuniste"
                : "Opportuniste · " + cas.date().format(JOUR_MOIS);
    }
}
