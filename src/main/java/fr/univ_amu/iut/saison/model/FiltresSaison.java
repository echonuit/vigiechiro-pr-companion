package fr.univ_amu.iut.saison.model;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import java.util.List;

/// Les deux filtres de « Ma saison » : **chercher un lieu** et **ne garder que ce qu'il reste à faire**
/// (#3103, portés en ligne de commande à la clôture du chantier #3092).
///
/// Ils vivent dans `model` parce que **deux surfaces** posent la même question : la barre de l'écran et
/// `solde-saison`. Écrite deux fois, la règle finit par diverger - c'est le motif que le dépôt applique
/// déjà à [fr.univ_amu.iut.validation.model.FiltresLieu], lu par l'écran comme par la commande.
public final class FiltresSaison {

    private FiltresSaison() {}

    /// Les lignes dont le **carré** ou le **code du point** contient `recherche`, insensible à la casse
    /// et aux accents. Une recherche vide ou absente ne retient rien de particulier : la ligne de
    /// commande passe `null` quand `--lieu` n'est pas donné.
    public static List<LigneSaison> parLieu(List<LigneSaison> lignes, String recherche) {
        if (recherche == null || NormalisationTexte.normaliser(recherche).isEmpty()) {
            return lignes;
        }
        return lignes.stream()
                .filter(ligne -> correspondAuLieu(ligne, recherche))
                .toList();
    }

    /// Vrai si le **carré**, le **nom qu'on lui a donné** ou le **code du point** contient `recherche`.
    ///
    /// Le nom n'est pas un troisième lieu : c'est la **seconde étiquette du carré** (ADR 3157), ajoutée
    /// à la recherche par #3219. Les autres colonnes ne s'y prêtent pas : elles portent des états de
    /// passage et une phrase d'action, que « Reste à faire » interroge mieux qu'une recherche libre.
    public static boolean correspondAuLieu(LigneSaison ligne, String recherche) {
        String aiguille = NormalisationTexte.normaliser(recherche);
        return NormalisationTexte.contient(ligne.numeroCarre(), aiguille)
                || NormalisationTexte.contient(ligne.codePoint(), aiguille)
                || NormalisationTexte.contient(ligne.nomSite(), aiguille);
    }

    /// Les points qui ne sont **pas à jour**, c'est-à-dire ceux dont il reste une action à mener.
    ///
    /// La règle n'est pas réécrite ici : elle est lue sur [LigneSaison#aJour], sans quoi la colonne
    /// « Reste à faire » et le filtre pourraient un jour se contredire sur la même ligne.
    public static List<LigneSaison> resteAFaire(List<LigneSaison> lignes) {
        return lignes.stream().filter(ligne -> !ligne.aJour()).toList();
    }
}
