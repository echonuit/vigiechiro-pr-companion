package fr.univ_amu.iut.passage.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import fr.univ_amu.iut.passage.model.IndiceAcoustique;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// ViewModel de la modale **« Réactiver ce passage »** (`ReactivationModale.fxml`, #1780).
///
/// La réactivation enchaîne deux phases longues - la **régénération / le rebranchement** des séquences
/// (disque) puis, sur un passage reconstruit, l'**acquisition de l'ancrage** (réseau VigieChiro, #1571).
/// Chacune a **sa** progression ([#progressionRegeneration], [#progressionAncrage]) : la modale les montre
/// sur deux barres, pour que la barre ne reste plus **figée à 100 %** pendant l'ancrage silencieux (#1780).
///
/// Le compte rendu est **honnête** : il dit combien de séquences ont été rebranchées et **sur quelle
/// preuve**, combien ont été refusées et pourquoi, et ce qui manque encore (formatage repris de l'ancienne
/// `ActionReactivation`, désormais affiché **dans** la modale plutôt qu'en notification).
public class ReactivationModaleViewModel {

    /// Nombre d'écarts détaillés dans le compte rendu : au-delà, on résume (une nuit peut en compter des
    /// milliers, et la modale doit rester lisible).

    /// Combien d'absences on nomme avant de résumer : assez pour identifier le motif dominant, pas assez
    /// pour noyer la modale quand une nuit entière manque.

    /// Progression de la phase **disque** (régénération / rebranchement des séquences), 0 -> 1.
    private final ProgressionOperation progressionRegeneration = new ProgressionOperation();

    /// Progression de la phase **réseau** (acquisition de l'ancrage des observations, #1571), 0 -> 1. Ne
    /// bouge que sur un passage reconstruit dont l'audio est revenu : sinon la phase ne se déclenche pas.
    private final ProgressionOperation progressionAncrage = new ProgressionOperation();

    private final ReadOnlyObjectWrapper<CompteRendu> compteRendu =
            new ReadOnlyObjectWrapper<>(CompteRendu.de("", List.of()));
    private final ReadOnlyStringWrapper erreur = new ReadOnlyStringWrapper("");

    /// Vrai dès qu'une réactivation s'est **conclue** : l'écran appelant recharge alors ses volumes et
    /// boutons (l'audio a pu revenir, le passage redevenir écoutable).
    private final ReadOnlyBooleanWrapper reactive = new ReadOnlyBooleanWrapper(false);

    @Inject
    public ReactivationModaleViewModel() {
        // Aucune dépendance : le travail (réseau + base) est fourni par l'appelant (PassageViewModel), la
        // modale ne porte que la présentation. Instancié à neuf par modale (non-singleton) : état propre.
    }

    /// Progression de la phase disque (régénération / rebranchement), à lier à la première barre.
    public ProgressionOperation progressionRegeneration() {
        return progressionRegeneration;
    }

    /// Progression de la phase réseau (ancrage), à lier à la seconde barre - révélée seulement quand la
    /// phase démarre.
    public ProgressionOperation progressionAncrage() {
        return progressionAncrage;
    }

    /// Compte rendu de fin, lacunes comprises. Vide tant que l'opération n'est pas conclue.
    public ReadOnlyObjectProperty<CompteRendu> compteRenduProperty() {
        return compteRendu.getReadOnlyProperty();
    }

    /// Échec ou refus (dossier introuvable, plateforme injoignable), affiché comme tel - distinct du compte
    /// rendu, qui n'est pas un incident.
    public ReadOnlyStringProperty erreurProperty() {
        return erreur.getReadOnlyProperty();
    }

    /// Vrai dès qu'une réactivation s'est conclue : l'appelant doit recharger sa fiche.
    public ReadOnlyBooleanProperty reactiveProperty() {
        return reactive.getReadOnlyProperty();
    }

    /// Publie le compte rendu (**fil JavaFX**) et marque [#reactiveProperty] : l'opération s'est conclue,
    /// l'écran appelant se rechargera à la fermeture.
    public void restituer(RapportReactivation rapport) {
        compteRendu.set(construire(rapport));
        erreur.set("");
        reactive.set(true);
    }

    /// Route un échec vers le message d'erreur de la modale : un refus (dossier introuvable, plateforme
    /// injoignable) **dit quoi faire**, il ne disparaît pas dans le fil de fond.
    public void signalerErreur(Throwable echec) {
        String detail = echec.getMessage();
        erreur.set(detail != null && !detail.isBlank() ? detail : "Réactivation impossible.");
    }

    /// Annulation demandée : état **neutre**, pas une erreur. Rien n'a été supprimé (la réactivation
    /// **ajoute** de l'audio, elle n'en retire pas), et on le dit.
    public void signalerAnnulation() {
        erreur.set("");
        compteRendu.set(CompteRendu.de(
                "Réactivation annulée", List.of(Constat.de("Aucun fichier n'a été modifié.", Severite.INFO))));
    }

    /// Le compte rendu, **structuré** (ADR 0031) : un titre, une mise en contexte, les faits avec leurs
    /// détails, ce qu'il faut retenir.
    ///
    /// Il était jusqu'ici assemblé au `StringBuilder` et rendu dans un `Label` unique. La structure permet
    /// à chaque surface de décider ce qu'elle montre - la modale résume au-delà de cinq détails, la ligne
    /// de commande les rend tous - sans que la mise en forme ait à être écrite deux fois.
    private static CompteRendu construire(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            return reconstruit(rapport);
        }
        List<Constat> constats = new ArrayList<>();
        constats.add(Constat.de(faitReactivees(rapport), Severite.SUCCES));
        if (rapport.dejaPresentes() > 0) {
            constats.add(
                    Constat.de(rapport.dejaPresentes() + " séquence(s) étaient déjà sur le disque.", Severite.INFO));
        }
        if (rapport.manquantes() > 0) {
            constats.add(new Constat(
                    rapport.manquantes() + " séquence(s) restent introuvables dans ce dossier.",
                    Severite.ERREUR,
                    detailsAbsences(rapport.absences())));
        }
        ajouterEcarts(constats, rapport.ecarts());
        ajouterIndiceAcoustique(constats, rapport.indiceAcoustique());
        ajouterRapatriement(constats, rapport.rapatriement());
        return new CompteRendu(titre(rapport), preambule(rapport), constats, conclusion(rapport));
    }

    /// Titre du compte rendu : honnête aussi quand rien n'a pu être tenté (passage reconstruit, #1648).
    private static String titre(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            return "Passage reconstruit";
        }
        return rapport.complete() ? "Passage réactivé" : "Réactivation partielle";
    }

    private static String preambule(RapportReactivation rapport) {
        return rapport.voie() == VoieReactivation.BRUTS
                ? "Ce dossier ne contenait que vos enregistrements bruts : les séquences d'écoute ont été"
                        + " régénérées à partir d'eux, puis vérifiées une à une."
                : "";
    }

    private static String faitReactivees(RapportReactivation rapport) {
        String preuve =
                rapport.confianceMinimale() == null ? "" : " (identité vérifiée : " + libelleConfiance(rapport) + ")";
        return rapport.reactivees() + " séquence(s) réactivée(s)" + preuve + ".";
    }

    private static String conclusion(RapportReactivation rapport) {
        return rapport.complete()
                ? "L'audio est de nouveau complet : le passage est écoutable."
                : "L'audio reste incomplet : " + rapport.decompte().presentes() + " séquence(s) sur "
                        + rapport.decompte().total() + " présentes.";
    }

    /// **Ce qui manquait, nommé** (#1943). Deux situations tombaient dans le même compteur : un
    /// enregistrement absent du dossier, qui appelle une action de l'utilisateur, et une tranche non
    /// régénérée, qui est un défaut de notre côté.
    ///
    /// Les plus coûteuses d'abord : c'est par elles qu'on commence à chercher. Le **plafond d'affichage**
    /// n'est plus ici - il appartient à la surface (ADR 0031).
    private static List<Detail> detailsAbsences(List<AbsenceReactivation> absences) {
        return absences.stream()
                .sorted(Comparator.comparingInt(AbsenceReactivation::sequences)
                        .reversed()
                        .thenComparing(AbsenceReactivation::nomFichier))
                .map(absence -> new Detail(
                        absence.nomFichier(),
                        absence.motif() + (absence.sequences() > 1 ? " (" + absence.sequences() + " séquences)" : "")))
                .toList();
    }

    /// Les fichiers **refusés** : jamais rebranchés en silence, chacun avec son motif.
    private static void ajouterEcarts(List<Constat> constats, List<EcartReactivation> ecarts) {
        if (ecarts.isEmpty()) {
            return;
        }
        constats.add(new Constat(
                ecarts.size() + " fichier(s) portaient le bon nom mais n'étaient pas le bon audio : ils n'ont"
                        + " pas été rebranchés (les observations auraient pointé sur le mauvais son).",
                Severite.ERREUR,
                ecarts.stream()
                        .map(ecart -> new Detail(ecart.nomFichier(), ecart.motif()))
                        .toList()));
    }

    /// Concordance acoustique en **indice** (#1682) : purement informatif. Les tranches régénérées sont
    /// acceptées sur preuve structurelle ; cet indice dit seulement à quel point les cris attendus s'y
    /// retrouvent.
    private static void ajouterIndiceAcoustique(List<Constat> constats, IndiceAcoustique indice) {
        if (indice == null || !indice.estRenseigne()) {
            return;
        }
        constats.add(Constat.de(
                "Concordance acoustique (indice, non bloquant) : " + indice.concordantes() + " séquence(s) sur "
                        + indice.mesurees() + " présentent les cris attendus.",
                Severite.INFO));
    }

    /// Ce que la **phase d'ancrage** a rapatrié (#1904) : les identifiants plateforme, et avec eux les
    /// **échanges avec le validateur** (#1867). Muet sinon.
    private static void ajouterRapatriement(List<Constat> constats, RapportAncrage rapatriement) {
        if (rapatriement == null || rapatriement.estMuet()) {
            return;
        }
        constats.add(Constat.de(rapatriement.texte(), Severite.INFO));
    }

    /// Compte rendu **honnête** d'un passage reconstruit (#1648) : ni « introuvables », ni fausse promesse.
    /// On explique pourquoi rien n'a pu être relié, et que ce n'est pas la faute des fichiers de l'utilisateur.
    private static CompteRendu reconstruit(RapportReactivation rapport) {
        return new CompteRendu(
                titre(rapport),
                "Ce passage a été reconstruit depuis Vigie-Chiro : l'application connaît le nom de ses "
                        + rapport.decompte().total()
                        + " séquence(s), mais pas la correspondance avec vos fichiers d'origine, ni les"
                        + " empreintes nécessaires pour les régénérer.",
                List.of(Constat.de(
                        "Vos fichiers ne sont pas en cause : ils n'ont simplement pas pu être reliés. La"
                                + " réactivation depuis les enregistrements bruts n'est pas encore disponible"
                                + " pour ce type de passage.",
                        Severite.INFO)),
                "");
    }
    /// Libellé du niveau de confiance **le plus faible** obtenu : c'est lui qui qualifie honnêtement la
    /// réactivation entière.
    private static String libelleConfiance(RapportReactivation rapport) {
        return switch (rapport.confianceMinimale()) {
            case CERTITUDE -> "certitude (empreinte du contenu, ou preuves structurelle et acoustique concordantes)";
            case FORTE -> "forte (nom, taille et durée concordants ; pas d'empreinte en base pour aller plus loin)";
        };
    }
}
