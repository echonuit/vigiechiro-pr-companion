package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.Comparator;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;

/// Câblage des **colonnes** du tableau multi-sites, extrait de [MultisiteController] (pur câblage, PMD
/// NcssCount : le contrôleur était au plafond).
///
/// Deux colonnes ne se contentent pas d'afficher une valeur :
///
/// - **Année** et **N° passage** trient **numériquement** (et non alphabétiquement) au clic d'en-tête
///   (#145) : sans comparateur explicite, « 10 » passerait avant « 2 » ;
/// - **Analyse** (#1338) porte un badge **et une infobulle datée**. Le cache du traitement serveur est un
///   **relevé daté**, pas une vérité (patron « État observé ») : un badge « En cours » relevé il y a trois
///   semaines ne dit pas la même chose que le même badge relevé ce matin, et la pastille seule ne permet
///   pas de le deviner. Une nuit **non déposée** n'affiche **rien** plutôt qu'un « sans objet » qui ferait
///   du bruit sur la majorité des lignes.
final class ColonnesMultisite {

    private ColonnesMultisite() {}

    /// Câble les sept colonnes du tableau des passages.
    static void configurer(
            TableColumn<LignePassage, String> carre,
            TableColumn<LignePassage, String> point,
            TableColumn<LignePassage, String> annee,
            TableColumn<LignePassage, String> numero,
            TableColumn<LignePassage, String> date,
            TableColumn<LignePassage, String> statut,
            TableColumn<LignePassage, String> verdict,
            TableColumn<LignePassage, String> analyse) {
        carre.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().numeroCarre()));
        point.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codePoint()));
        annee.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().annee())));
        numero.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().numeroPassage())));
        // #145 : tri NUMÉRIQUE (et non alphabétique) au clic d'en-tête sur Année et N° de passage.
        annee.setComparator(Comparator.comparingInt(Integer::parseInt));
        numero.setComparator(Comparator.comparingInt(Integer::parseInt));
        date.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().dateEnregistrement()));
        statut.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().statut().libelle()));
        verdict.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().verdict() == null ? "" : c.getValue().verdict().libelle()));
        // Statut / verdict en badges (#691), comme la table de la fiche site.
        statut.setCellFactory(colonne -> ColonneBadge.cellule(ligne -> ColonneBadge.classe(ligne.statut())));
        verdict.setCellFactory(colonne -> ColonneBadge.cellule(ligne -> ColonneBadge.classe(ligne.verdict())));
        configurerAnalyse(analyse);
    }

    /// Colonne « Analyse » (#1338) : l'état en pastille, l'explication et la date du relevé en infobulle.
    private static void configurerAnalyse(TableColumn<LignePassage, String> analyse) {
        analyse.setCellValueFactory(cellule -> {
            String libelle = cellule.getValue().etatAnalyse().libelle();
            // Chaîne vide -> `null` : c'est ce que la cellule badge interprète comme « rien à afficher ».
            // Sans cela, une nuit non déposée porterait une pastille vide.
            return new ReadOnlyStringWrapper(libelle.isEmpty() ? null : libelle);
        });
        analyse.setCellFactory(colonne ->
                ColonneBadge.cellule(ligne -> ligne.etatAnalyse().classeBadge(), ColonnesMultisite::infobulleAnalyse));
    }

    /// Ce que l'état **demande** (ou n'attend de personne), suivi de la **date du relevé** quand il y en a
    /// un. « Dernier état connu le … » est la formule du chantier : elle dit honnêtement que l'information
    /// peut avoir vieilli, plutôt que de la faire passer pour fraîche.
    private static String infobulleAnalyse(LignePassage ligne) {
        EtatAnalyse etat = ligne.etatAnalyse();
        if (etat == EtatAnalyse.SANS_OBJET) {
            return "";
        }
        String texte = etat.infobulle();
        return ligne.analyseReleveeLe() == null
                ? texte
                : texte + "\n\nDernier état connu le " + ligne.analyseReleveeLe() + ".";
    }
}
