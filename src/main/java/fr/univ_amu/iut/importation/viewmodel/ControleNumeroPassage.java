package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel.DemandeImport;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/// Pré-contrôle d'unicité R5 **proactif** (#108) du rattachement de l'assistant M-Import.
///
/// Extrait de [ImportationViewModel] (même esprit que [ApercuPrefixe] et les sous-VM #183) pour garder
/// l'orchestrateur cohésif : détecter qu'un n° de passage est **déjà pris** pour un point et une année,
/// proposer le **prochain n° libre**, et corriger en un clic, est une préoccupation autonome. Ce
/// collaborateur **observe lui-même** le rattachement (point / année / n° de passage) et entretient son
/// état observable ; l'orchestrateur se contente de l'exposer et de composer `peutImporter`.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est importé,
/// jamais `javafx.scene`. La lecture passe par le **service** (un VM ne touche jamais le DAO).
public final class ControleNumeroPassage {

    private final ServiceImport serviceImport;
    private final RattachementImportViewModel rattachement;

    /// `true` quand le quadruplet (point, année, n° de passage) courant correspond déjà à un passage : un
    /// import échouerait (R5). Bloque `peutImporter` et déclenche l'avertissement.
    private final ReadOnlyBooleanWrapper dejaUtilise = new ReadOnlyBooleanWrapper(this, "dejaUtilise", false);

    /// Avertissement lisible (vide si le n° est libre) : explique le doublon et propose le n° libre.
    /// Le pre-controle R5 (#108), **type** : l'import reste possible, c'est un avertissement et non un
    /// echec. Le « ⚠ » qui ouvrait la phrase a disparu - la severite est portee par la valeur (#2045).
    private final ReadOnlyObjectWrapper<RetourOperation> avertissement =
            new ReadOnlyObjectWrapper<>(this, "avertissement", RetourOperation.AUCUN);

    /// Dernier « prochain n° libre » calculé, proposé en un clic. Sur le seul fil JavaFX (renseigné par
    /// [#verifier()]) : un simple champ suffit.
    /// Ce que voit l'utilisateur quand sa carte rapporte une nuit **déjà récupérée** : la situation, la
    /// raison, le geste. Le nommer ici plutôt que l'écrire dans la branche garde `verifier()` lisible et
    /// rend le texte citable par les tests.
    private static final String MESSAGE_NUIT_RECUPEREE = "Cette nuit est déjà là : elle a été récupérée de"
            + " Vigie-Chiro, avec ses observations et son rattachement, mais sans son audio. Ouvrez-la et"
            + " utilisez « Réactiver ce passage » : votre carte lui rendra son son en gardant tout le reste."
            + " L'importer ici en ferait une seconde, et vos observations resteraient sur l'autre.";

    private int prochainNumeroLibre = 1;

    /// L'identité de la nuit qu'on s'apprête à importer (série, date), quand l'inspection l'a établie.
    /// Fournie par l'orchestrateur : ce contrôle ne lit pas la carte, il consulte ce que d'autres ont vu.
    private final Supplier<Optional<IdentiteNuit>> identiteNuit;

    /// La nuit **déjà récupérée de Vigie-Chiro** que le n° courant heurte (#2580), s'il y en a une.
    private final ReadOnlyObjectWrapper<Optional<Long>> nuitRecuperee =
            new ReadOnlyObjectWrapper<>(this, "nuitRecuperee", Optional.empty());

    ControleNumeroPassage(
            ServiceImport serviceImport,
            RattachementImportViewModel rattachement,
            Supplier<Optional<IdentiteNuit>> identiteNuit) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.rattachement = Objects.requireNonNull(rattachement, "rattachement");
        this.identiteNuit = Objects.requireNonNull(identiteNuit, "identiteNuit");
        // Recalcule le pré-contrôle dès qu'un champ déterminant du rattachement change (le PRÉ-REMPLISSAGE
        // du n° au prochain bloc libre est porté par CoordinationNuits, qui connaît le nombre de nuits).
        rattachement.pointSelectionneProperty().addListener((obs, ancien, nouveau) -> verifier());
        rattachement.anneeProperty().addListener((obs, ancien, nouveau) -> verifier());
        rattachement.numeroPassageProperty().addListener((obs, ancien, nouveau) -> verifier());
    }

    /// Recalcule le pré-contrôle pour la nuit et le rattachement courants. Appelé par l'orchestrateur
    /// après une inspection (la nuit change) et, en interne, à chaque champ déterminant du rattachement.
    ///
    /// Sans point sélectionné ou avec un n° &lt; 1
    /// (rattachement incomplet), l'avertissement est vide : rien à signaler tant que la saisie n'est pas
    /// exploitable.
    void verifier() {
        PointDEcoute point = rattachement.pointSelectionneProperty().get();
        int annee = rattachement.anneeProperty().get();
        int numero = rattachement.numeroPassageProperty().get();
        if (point == null || numero < 1) {
            dejaUtilise.set(false);
            nuitRecuperee.set(Optional.empty());
            avertissement.set(RetourOperation.AUCUN);
            return;
        }
        // Cette nuit est-elle DÉJÀ LÀ, récupérée de Vigie-Chiro ? Depuis #2557, c'est le cas ordinaire
        // après une synchro : la nuit porte ses observations et son rattachement, mais pas son audio.
        // L'importer une seconde fois, sur ce n° ou sur le suivant, en ferait DEUX (#2580).
        Optional<Long> recuperee = identiteNuit
                .get()
                .flatMap(identite -> serviceImport.nuitRecuperee(identite.numeroSerie(), identite.dateNuit()));
        nuitRecuperee.set(recuperee);
        boolean numeroPris = serviceImport.numeroPassageDejaUtilise(point.id(), annee, numero);
        dejaUtilise.set(numeroPris);
        if (numeroPris) {
            prochainNumeroLibre = serviceImport.prochainNumeroPassageLibre(point.id(), annee);
        }
        // Quand les deux se présentent, la nuit déjà récupérée prime : le doublon qu'elle annonce est le
        // plus coûteux des deux (deux moitiés d'une même nuit, sur deux n° différents), et le geste qu'elle
        // demande n'est pas de changer de n°.
        if (recuperee.isPresent()) {
            avertissement.set(RetourOperation.avertissement(MESSAGE_NUIT_RECUPEREE));
        } else if (numeroPris) {
            avertissement.set(RetourOperation.avertissement(String.format(
                    "Le passage n° %d existe déjà pour ce point en %d. Prochain n° libre : %d.",
                    numero, annee, prochainNumeroLibre)));
        } else {
            avertissement.set(RetourOperation.AUCUN);
        }
    }

    /// La nuit **déjà récupérée** que cette carte rapporte, s'il y en a une : l'écran y offre l'accès à sa
    /// fiche, là où se trouve « Réactiver ce passage », plutôt que les deux gestes qui la mutileraient
    /// (#2580).
    public ReadOnlyObjectProperty<Optional<Long>> nuitRecupereeProperty() {
        return nuitRecuperee.getReadOnlyProperty();
    }

    /// Renseigne le n° de passage du rattachement avec le **prochain n° libre** proposé.
    ///
    /// **Sans effet quand la nuit est déjà là** (#2580) : c'est précisément le geste qui fabriquerait le
    /// doublon - une nuit avec les observations, une autre avec l'audio. L'écran retire d'ailleurs le
    /// bouton dans ce cas ; cette garde tient la règle même si l'écran l'oubliait.
    public void utiliserProchainNumeroLibre() {
        if (nuitRecuperee.get().isPresent()) {
            return;
        }
        if (dejaUtilise.get()) {
            rattachement.numeroPassageProperty().set(prochainNumeroLibre);
        }
    }

    /// `true` si le n° de passage courant est déjà pris (R5).
    boolean estDejaUtilise() {
        return dejaUtilise.get();
    }

    /// `true` si l'import doit être **retenu** : n° déjà pris (R5), ou nuit déjà récupérée de Vigie-Chiro
    /// (#2580). Le second cas bloque **même sur un n° libre** : ce n'est pas le n° qui est en cause, c'est
    /// que la nuit est déjà là et attend d'être réactivée, pas réimportée.
    boolean estBloque() {
        return dejaUtilise.get() || nuitRecuperee.get().isPresent();
    }

    /// Aperçu de ce que l'écrasement du passage existant au quadruplet courant supprimerait (#214) :
    /// séquences et validations observateur, pour rendre tangibles les deux confirmations. [ApercuEcrasement#VIDE]
    /// si le rattachement est incomplet ou le n° libre.
    public ApercuEcrasement apercuEcrasement() {
        PointDEcoute point = rattachement.pointSelectionneProperty().get();
        int numero = rattachement.numeroPassageProperty().get();
        if (point == null || numero < 1 || !dejaUtilise.get()) {
            return ApercuEcrasement.VIDE;
        }
        return serviceImport.apercuEcrasement(
                point.id(), rattachement.anneeProperty().get(), numero);
    }

    /// Variante **écrasement** (#214) de l'import : supprime d'abord le passage existant au quadruplet
    /// choisi (suppression destructive en cascade) **puis** importe la nuit. À n'appeler qu'après double
    /// confirmation côté IHM. **Ne mute aucune Property** : sûr sur un fil d'arrière-plan.
    public ResultatImport ecraserEtImporter(
            DemandeImport demande, Consumer<Progression> progres, JetonAnnulation jeton) {
        return ecraserEtImporter(demande, progres, jeton, SuiviFichiers.inerte());
    }

    /// Variante **écrasement** avec suivi par fichier (#947), notifié hors-thread comme la progression.
    /// **Ne mute aucune Property** : sûr sur un fil d'arrière-plan.
    public ResultatImport ecraserEtImporter(
            DemandeImport demande, Consumer<Progression> progres, JetonAnnulation jeton, SuiviFichiers suivi) {
        return serviceImport.ecraserEtImporter(
                demande.dossier(),
                demande.idPoint(),
                demande.prefixe(),
                progres,
                jeton,
                demande.conserverOriginaux(),
                suivi);
    }

    /// Message à présenter quand l'import est bloqué (`peutImporter` faux) : l'avertissement de doublon
    /// s'il y en a un (cause précise), sinon le message générique fourni (rattachement incomplet). Évite
    /// le message trompeur « Complétez le rattachement » alors que la vraie cause est un n° déjà pris.
    String messageBlocage(String messageRattachementIncomplet) {
        return dejaUtilise.get() ? avertissement.get().texte() : messageRattachementIncomplet;
    }

    ReadOnlyBooleanProperty dejaUtiliseProperty() {
        return dejaUtilise.getReadOnlyProperty();
    }

    ReadOnlyObjectProperty<RetourOperation> avertissementProperty() {
        return avertissement.getReadOnlyProperty();
    }
}
