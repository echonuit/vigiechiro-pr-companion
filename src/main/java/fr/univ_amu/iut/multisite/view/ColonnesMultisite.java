package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.ColonneDate;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.multisite.model.EtatAnalyse;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.Comparator;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;

/// Câblage des **colonnes** du tableau multi-sites, pur câblage tenu hors de [MultisiteController].
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

    /// Câble les colonnes du tableau des passages.
    /// Les colonnes de la table, en **un seul objet** (#3300).
    ///
    /// Elles étaient onze paramètres positionnels du **même type** : deux d'entre eux échangés se
    /// compilaient sans un mot et se lisaient à l'écran comme un défaut de données. C'est le patron que
    /// `ColonnesAudio.Colonnes` applique déjà, et l'arbitrage de l'EPIC #2483 : un objet de paramètres
    /// avant d'ajouter le onzième.
    record Colonnes(
            TableColumn<LignePassage, String> commune,
            TableColumn<LignePassage, String> carre,
            TableColumn<LignePassage, String> nomSite,
            TableColumn<LignePassage, String> point,
            TableColumn<LignePassage, String> annee,
            TableColumn<LignePassage, String> numero,
            TableColumn<LignePassage, java.time.LocalDate> date,
            TableColumn<LignePassage, String> statut,
            TableColumn<LignePassage, String> verdict,
            TableColumn<LignePassage, String> analyse,
            TableColumn<LignePassage, String> campagne) {}

    static void configurer(Colonnes col) {
        TableColumn<LignePassage, String> commune = col.commune();
        TableColumn<LignePassage, String> carre = col.carre();
        TableColumn<LignePassage, String> point = col.point();
        TableColumn<LignePassage, String> annee = col.annee();
        TableColumn<LignePassage, String> numero = col.numero();
        TableColumn<LignePassage, java.time.LocalDate> date = col.date();
        TableColumn<LignePassage, String> statut = col.statut();
        TableColumn<LignePassage, String> verdict = col.verdict();
        TableColumn<LignePassage, String> analyse = col.analyse();
        TableColumn<LignePassage, String> campagne = col.campagne();
        // Le nom du carré (#3300) : la recherche libre de cet écran retient une ligne sur lui
        // (`CriteresMultisite.correspond`), et seule la puce « Lieu » le montrait. Qui tape « Vallon »
        // dans la recherche voyait des lignes sans savoir pourquoi.
        col.nomSite()
                .setCellValueFactory(cellule -> new ReadOnlyStringWrapper(
                        cellule.getValue().nomSite() == null
                                ? ""
                                : cellule.getValue().nomSite()));
        carre.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().numeroCarre()));
        // Commune (#3163) : cellule vide quand le GPS du point n'a résolu aucune commune. C'est
        // un état normal - `point_commune` est une table latérale (ADR 2791) - et non une anomalie
        // à signaler dans la table.
        commune.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().commune() == null ? "" : c.getValue().commune()));
        point.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codePoint()));
        annee.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().annee())));
        numero.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().numeroPassage())));
        // #145 : tri NUMÉRIQUE (et non alphabétique) au clic d'en-tête sur Année et N° de passage.
        annee.setComparator(Comparator.comparingInt(Integer::parseInt));
        numero.setComparator(Comparator.comparingInt(Integer::parseInt));
        ColonneDate.configurer(date, LignePassage::dateEnregistrement);
        statut.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().statut().libelle()));
        verdict.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().verdict() == null ? "" : c.getValue().verdict().libelle()));
        // Statut / verdict en badges (#691), comme la table de la fiche site.
        statut.setCellFactory(colonne -> ColonneBadge.cellule(ligne -> ColonneBadge.classe(ligne.statut())));
        verdict.setCellFactory(colonne -> ColonneBadge.cellule(ligne -> ColonneBadge.classe(ligne.verdict())));
        // Campagne (#2355) : cellule vide pour une nuit non rattachée, le rattachement est facultatif,
        // et la feature est désactivable.
        campagne.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().campagne() == null ? "" : c.getValue().campagne()));
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

    /// Le catalogue des colonnes pour le sélecteur (#919), tenu hors du contrôleur (#3300) : c'est
    /// de la connaissance sur les **colonnes**, pas sur l'écran.
    static List<GestionnaireColonnes.Colonne> pourLeSelecteur(Colonnes col) {
        return List.of(
                new GestionnaireColonnes.Colonne(col.carre(), "Carré", true),
                // Le nom du carré (#3300) : inscrit au sélecteur, MASQUÉ au départ. Les colonnes de cette
                // table totalisent déjà 1 360 px dans une scène partagée avec la carte - elle défilait
                // horizontalement avant cet ajout. Le nom se voit dans la puce « Lieu » ; cette colonne
                // le rend disponible dans le tableau pour qui le veut, sans l'imposer à qui ne le veut pas.
                new GestionnaireColonnes.Colonne(col.nomSite(), "Nom du carré", false),
                new GestionnaireColonnes.Colonne(col.point(), "Point", false),
                new GestionnaireColonnes.Colonne(col.commune(), "Commune", false),
                new GestionnaireColonnes.Colonne(col.annee(), "Année", false),
                new GestionnaireColonnes.Colonne(col.numero(), "N° passage", false),
                new GestionnaireColonnes.Colonne(col.date(), "Date", false),
                new GestionnaireColonnes.Colonne(col.statut(), "Statut", false),
                new GestionnaireColonnes.Colonne(col.verdict(), "Verdict", false),
                new GestionnaireColonnes.Colonne(col.analyse(), "Analyse", false),
                new GestionnaireColonnes.Colonne(col.campagne(), "Campagne", false));
    }
}
