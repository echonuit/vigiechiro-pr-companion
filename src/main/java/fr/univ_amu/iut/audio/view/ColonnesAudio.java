package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.ComparateursAudio;
import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;

/// Câblage des colonnes de la table des observations de « Sons & validation » : valeur affichée par
/// colonne, cellules personnalisées (fichier élidé + infobulle, heure, indicateurs), et comparateurs de
/// tri **numériques / par ordre de revue** là où l'affichage est une chaîne formatée (sans quoi « 100 % »
/// précèderait « 83 % » et « N°10 » « N°2 »). Les colonnes purement texte gardent le tri par défaut.
///
/// Extrait de [SonsValidationController] : ce bloc formait une unité cohésive, et l'y garder repassait
/// le contrôleur au-dessus du seuil de God Class (NcssCount). Les colonnes restent injectées par le
/// contrôleur (FXML) et lui sont passées ici, regroupées.
final class ColonnesAudio {

    /// Colonnes de la table, regroupées en objet-paramètre : le constructeur **canonique d'un record**
    /// est exempté d'`ExcessiveParameterList` (là où une méthode à quinze paramètres serait refusée).
    record Colonnes(
            TableColumn<LigneObservationAudio, String> tadarida,
            TableColumn<LigneObservationAudio, String> proba,
            TableColumn<LigneObservationAudio, String> frequence,
            TableColumn<LigneObservationAudio, String> debut,
            TableColumn<LigneObservationAudio, String> duree,
            TableColumn<LigneObservationAudio, String> observateur,
            TableColumn<LigneObservationAudio, String> fichier,
            TableColumn<LigneObservationAudio, String> passage,
            TableColumn<LigneObservationAudio, String> carre,
            TableColumn<LigneObservationAudio, String> point,
            TableColumn<LigneObservationAudio, String> date,
            TableColumn<LigneObservationAudio, LocalDateTime> heure,
            TableColumn<LigneObservationAudio, String> statut,
            TableColumn<LigneObservationAudio, String> reference,
            TableColumn<LigneObservationAudio, String> commentaire) {}

    private ColonnesAudio() {}

    static void configurer(Colonnes col, BiConsumer<Long, String> enregistrerCommentaire) {
        col.tadarida().setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.tadarida(c.getValue())));
        col.proba()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.probabilite(c.getValue().probTadarida())));
        col.frequence()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.frequenceColonne(c.getValue().frequenceKHz())));
        col.debut()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.positionColonne(c.getValue().debutS())));
        col.duree()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.dureeColonne(
                        c.getValue().debutS(), c.getValue().finS())));
        col.observateur()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatLigneAudio.votreTaxon(c.getValue())));
        col.fichier()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().nomFichier())));
        // Le nom de fichier transformé est long (préfixe de campagne + suffixe de segment) : la cellule
        // l'élide, une infobulle en donne la valeur complète au survol.
        col.fichier().setCellFactory(colonne -> CellulesAudio.avecInfobulle());
        col.passage()
                .setCellValueFactory(
                        c -> new ReadOnlyStringWrapper("N°" + c.getValue().numeroPassage()));
        col.carre()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().numeroCarre())));
        col.point()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().codePoint())));
        col.date()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().dateEnregistrement())));
        // « Heure » : valeur = l'INSTANT complet (tri chronologique naturel de LocalDateTime, correct à
        // cheval sur minuit) ; affichage « HH:mm » via une cellule dédiée. Pas de comparateur de chaîne.
        CellulesAudio.configurerColonneHeure(col.heure());
        col.statut()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.libelleStatut(c.getValue().statut())));

        // Colonnes dont l'affichage est une chaîne à préfixe/suffixe numérique : même comparateur numérique
        // (sinon « 100 % » précèderait « 83 % » et « N°10 » « N°2 »). Le statut a son propre ordre de revue.
        List.of(col.proba(), col.frequence(), col.debut(), col.passage())
                .forEach(colonne -> colonne.setComparator(ComparateursAudio.comparateurNumerique()));
        // Durée : unité adaptative ms/s → comparateur dédié (le tri numérique naïf mêlerait « 120 ms » et
        // « 2,1 s »).
        col.duree().setComparator(ComparateursAudio.comparateurDuree());
        col.statut().setComparator(ComparateursAudio.comparateurStatut());

        // Indicateurs référence / commentaire : en-tête et cellule rendus par une icône Ikonli colorée,
        // un id stable pour les retrouver, cellules dédiées (icône + infobulle), et non triables.
        CellulesAudio.configurerIndicateurs(col.reference(), col.commentaire(), enregistrerCommentaire);
    }
}
