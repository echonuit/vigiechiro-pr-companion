package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.ComparateursAudio;
import fr.univ_amu.iut.audio.viewmodel.FormatAvisValidateur;
import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.ColonneDate;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
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
            TableColumn<LigneObservationAudio, String> certitude,
            TableColumn<LigneObservationAudio, String> fichier,
            TableColumn<LigneObservationAudio, String> passage,
            TableColumn<LigneObservationAudio, String> carre,
            TableColumn<LigneObservationAudio, String> nomSite,
            TableColumn<LigneObservationAudio, String> point,
            TableColumn<LigneObservationAudio, String> commune,
            TableColumn<LigneObservationAudio, java.time.LocalDate> date,
            TableColumn<LigneObservationAudio, LocalDateTime> heure,
            TableColumn<LigneObservationAudio, String> statut,
            TableColumn<LigneObservationAudio, String> reference,
            TableColumn<LigneObservationAudio, String> commentaire,
            TableColumn<LigneObservationAudio, String> texteCommentaire,
            TableColumn<LigneObservationAudio, String> validateur,
            TableColumn<LigneObservationAudio, String> fil,
            TableColumn<LigneObservationAudio, String> enjeu) {}

    private ColonnesAudio() {}

    /// Colonnes de **contexte** (passage / carré / point / date) masquées quand la source cible un
    /// **unique passage** : elles y seraient constantes. La date d'enregistrement est constante au
    /// sein d'un passage (une nuit), inutile en source unique. Appelée à chaque `ouvrirSur` (#1194).
    static void adapterAuContexte(Colonnes col, boolean passageUnique) {
        col.passage().setVisible(!passageUnique);
        col.carre().setVisible(!passageUnique);
        col.point().setVisible(!passageUnique);
        // La commune est du contexte au même titre (#3164) : sur un passage unique, toutes les
        // lignes partagent celle de son point, et la colonne ne dirait rien de plus que l'en-tête.
        col.commune().setVisible(!passageUnique);
        col.date().setVisible(!passageUnique);
    }

    static void configurer(
            Colonnes col, Predicate<LigneObservationAudio> aEnjeu, BiConsumer<Long, String> enregistrerCommentaire) {
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
        // Certitude (#1139) : la déclaration manuelle de l'observateur, tiret tant que non renseignée
        // (vide par défaut). Affichage seul : la saisie passe par le menu « Certitude » et les touches
        // 1/2/3, comme les autres actions de revue.
        col.certitude()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.libelleCertitude(c.getValue().certitude())));
        // Le TROISIEME avis (#1417) : ce que le validateur du MNHN a tranche. Il arrivait deja du serveur
        // a chaque import, et la table ne le montrait pas - l'observateur pouvait croire que sa correction
        // faisait foi. Place juste apres sa certitude : les trois avis se lisent alors de gauche a droite,
        // Tadarida propose -> vous corrigez -> l'expert tranche.
        col.validateur().setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatAvisValidateur.avis(c.getValue())));
        // Badge : le DESACCORD est ce qu'il faut voir en premier. Un expert qui confirme ne demande rien.
        col.validateur().setCellFactory(colonne -> ColonneBadge.cellule(FormatAvisValidateur::classeBadge));
        // Fil de discussion : le nombre de messages, vide si personne n'a ecrit. Sans cet indicateur, un fil
        // ouvert par un validateur resterait invisible - on ne va pas ouvrir une modale sur chaque ligne.
        col.fil().setCellValueFactory(c -> new ReadOnlyStringWrapper(FormatAvisValidateur.marqueFil(c.getValue())));
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
        // Le nom du carré (#3300) : la recherche libre de cet écran retient une ligne sur lui
        // (`CriteresAudio.correspond`), et seule la puce « Lieu » le montrait. La colonne arrive
        // MASQUÉE - cette table en compte déjà 22, et le sélecteur (#919) est là pour ça.
        col.nomSite()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        c.getValue().nomSite() == null ? "" : c.getValue().nomSite()));
        col.point()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().codePoint())));
        // Le tiret, et non la cellule vide de Carte & passages : c'est la convention de CETTE
        // table, où toutes les colonnes de contexte marquent l'absence de la même façon. Une
        // commune non résolue est un état normal (`point_commune` est une table latérale, ADR 2791).
        col.commune()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().commune())));
        // « Date » : même idiome que « Heure » juste en dessous, et pour la même raison (#4019). La
        // valeur est la DATE (tri chronologique par construction), l'affichage est français.
        //
        // Le cadratin est passé explicitement : cette table a sa règle, « carré, point, commune, date
        // et fichier : cinq colonnes, une seule règle » (#3236). Une cellule vide ici aurait rompu une
        // convention déjà décidée, et c'est son test qui l'a rappelé.
        ColonneDate.configurer(col.date(), LigneObservationAudio::dateEnregistrement, Formats.VALEUR_ABSENTE);
        // « Heure » : valeur = l'INSTANT complet (tri chronologique naturel de LocalDateTime, correct à
        // cheval sur minuit) ; affichage « HH:mm » via une cellule dédiée. Pas de comparateur de chaîne.
        CellulesAudio.configurerColonneHeure(col.heure());
        col.statut()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.libelleStatut(c.getValue().statut())));
        // Statut de revue affiché en pastille (badge unifié #686, comme multisite/analyse/fiche site) :
        // couleur dérivée du statut de la ligne, jamais stockée. Le libellé (texte) vient du value factory.
        // Le mapping statut → classe CSS reste côté feature (FormatLigneAudio), le socle ColonneBadge ne
        // connaît que les types de commun.model (sinon commun → validation = cycle d'architecture).
        col.statut()
                .setCellFactory(
                        colonne -> ColonneBadge.cellule(ligne -> FormatLigneAudio.classeBadgeStatut(ligne.statut())));

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
        // Le TEXTE du commentaire (#3348) : meme cellule elidee a infobulle que les autres colonnes
        // longues, donc le contenu entier reste atteignable quand la colonne est etroite.
        col.texteCommentaire()
                .setCellValueFactory(c -> new ReadOnlyStringWrapper(
                        FormatLigneAudio.ouTiret(c.getValue().commentaire())));
        col.texteCommentaire().setCellFactory(colonne -> CellulesAudio.avecInfobulle());
        CellulesAudio.configurerIndicateurs(
                col.reference(), col.commentaire(), col.fil(), col.enjeu(), aEnjeu, enregistrerCommentaire);
    }

    /// Colonnes de la table proposées au **sélecteur** (#916), partagées entre le câblage et la
    /// capture dans les vues mémorisées (#994). « Proposition Tadarida », colonne d'identité, reste
    /// toujours affichée (verrouillée) mais déplaçable.
    ///
    /// Ici plutôt que dans le contrôleur : c'est une **description des colonnes**, comme le reste de
    /// cette classe, et l'y laisser repassait `SonsValidationController` au-dessus du plafond de God
    /// class (#3164).
    /// @param fme et `freqTerminale` : les deux **métriques acoustiques**, qui ne sont pas dans
    ///     [Colonnes] parce qu'elles sont pilotées par [MetriquesAcoustiquesAudio] et non câblées ici
    static List<GestionnaireColonnes.Colonne> pourLeSelecteur(
            Colonnes col,
            TableColumn<LigneObservationAudio, String> fme,
            TableColumn<LigneObservationAudio, String> freqTerminale) {
        return List.of(
                new GestionnaireColonnes.Colonne(col.tadarida(), "Proposition Tadarida", true),
                new GestionnaireColonnes.Colonne(col.proba(), "Proba.", false),
                new GestionnaireColonnes.Colonne(col.frequence(), "Fréquence", false),
                new GestionnaireColonnes.Colonne(fme, "FME", false),
                new GestionnaireColonnes.Colonne(freqTerminale, "Fréq. terminale", false),
                new GestionnaireColonnes.Colonne(col.debut(), "Début", false),
                new GestionnaireColonnes.Colonne(col.duree(), "Durée", false),
                new GestionnaireColonnes.Colonne(col.observateur(), "Votre taxon", false),
                new GestionnaireColonnes.Colonne(col.certitude(), "Certitude", false),
                new GestionnaireColonnes.Colonne(col.fichier(), "Fichier", false),
                new GestionnaireColonnes.Colonne(col.passage(), "Passage", false),
                new GestionnaireColonnes.Colonne(col.carre(), "Carré", false),
                new GestionnaireColonnes.Colonne(col.nomSite(), "Nom du carré", false),
                new GestionnaireColonnes.Colonne(col.point(), "Point", false),
                new GestionnaireColonnes.Colonne(col.commune(), "Commune", false),
                new GestionnaireColonnes.Colonne(col.date(), "Date", false),
                new GestionnaireColonnes.Colonne(col.heure(), "Heure", false),
                new GestionnaireColonnes.Colonne(col.statut(), "Statut", false),
                new GestionnaireColonnes.Colonne(col.reference(), "Référence", false),
                new GestionnaireColonnes.Colonne(col.commentaire(), "Commentaire", false),
                new GestionnaireColonnes.Colonne(col.texteCommentaire(), "Commentaire (texte)", false),
                new GestionnaireColonnes.Colonne(col.validateur(), "Avis du validateur", false),
                new GestionnaireColonnes.Colonne(col.fil(), "Discussion", false),
                new GestionnaireColonnes.Colonne(col.enjeu(), "Espèce à enjeu", false));
    }
}
