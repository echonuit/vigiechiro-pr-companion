package fr.univ_amu.iut.passage.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import fr.univ_amu.iut.passage.model.IndiceAcoustique;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
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
    private static final int ECARTS_DETAILLES = 5;

    /// Progression de la phase **disque** (régénération / rebranchement des séquences), 0 -> 1.
    private final ProgressionOperation progressionRegeneration = new ProgressionOperation();

    /// Progression de la phase **réseau** (acquisition de l'ancrage des observations, #1571), 0 -> 1. Ne
    /// bouge que sur un passage reconstruit dont l'audio est revenu : sinon la phase ne se déclenche pas.
    private final ProgressionOperation progressionAncrage = new ProgressionOperation();

    private final ReadOnlyStringWrapper compteRendu = new ReadOnlyStringWrapper("");
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
    public ReadOnlyStringProperty compteRenduProperty() {
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
        compteRendu.set(titre(rapport) + System.lineSeparator() + System.lineSeparator() + texte(rapport));
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
        compteRendu.set("Réactivation annulée : aucun fichier n'a été modifié.");
    }

    /// Titre du compte rendu : honnête aussi quand rien n'a pu être tenté (passage reconstruit, #1648).
    private static String titre(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            return "Passage reconstruit";
        }
        return rapport.complete() ? "Passage réactivé" : "Réactivation partielle";
    }

    /// Compte rendu : ce qui est revenu et sur quelle preuve, ce qui a été refusé et pourquoi, ce qui
    /// manque encore.
    private static String texte(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            return texteReconstruit(rapport);
        }
        StringBuilder texte = new StringBuilder();
        if (rapport.voie() == VoieReactivation.BRUTS) {
            texte.append("Ce dossier ne contenait que vos enregistrements bruts : les séquences d'écoute")
                    .append(" ont été régénérées à partir d'eux, puis vérifiées une à une.\n\n");
        }
        texte.append(rapport.reactivees()).append(" séquence(s) réactivée(s)");
        if (rapport.confianceMinimale() != null) {
            texte.append(" (identité vérifiée : ")
                    .append(libelleConfiance(rapport))
                    .append(')');
        }
        texte.append(".\n");
        if (rapport.dejaPresentes() > 0) {
            texte.append(rapport.dejaPresentes()).append(" séquence(s) étaient déjà sur le disque.\n");
        }
        if (rapport.manquantes() > 0) {
            texte.append(rapport.manquantes()).append(" séquence(s) restent introuvables dans ce dossier.\n");
        }
        ajouterEcarts(texte, rapport.ecarts());
        ajouterIndiceAcoustique(texte, rapport.indiceAcoustique());
        ajouterRapatriement(texte, rapport.rapatriement());
        texte.append('\n')
                .append(
                        rapport.complete()
                                ? "L'audio est de nouveau complet : le passage est écoutable."
                                : "L'audio reste incomplet : "
                                        + rapport.decompte().presentes() + " séquence(s) sur "
                                        + rapport.decompte().total() + " présentes.");
        return texte.toString();
    }

    /// Ce que la **phase d'ancrage** a rapatrié (#1904), quand elle s'est déclenchée : les identifiants
    /// plateforme, et avec eux les **échanges avec le validateur** (#1867). Muet sinon.
    ///
    /// C'est ici que ces messages arrivent le plus souvent : la phase ne se déclenche que sur une nuit
    /// **reconstruite**, dont les observations n'en portaient aucun. Les taire laissait l'observateur les
    /// découvrir en ouvrant la bonne ligne, par hasard.
    private static void ajouterRapatriement(StringBuilder texte, RapportAncrage rapatriement) {
        if (rapatriement == null || rapatriement.estMuet()) {
            return;
        }
        texte.append(rapatriement.texte()).append('\n');
    }

    /// Concordance acoustique en **indice** (#1682), quand elle a été mesurée (hydratation d'un passage
    /// reconstruit) : purement informatif. Les tranches régénérées sont acceptées sur preuve structurelle ;
    /// cet indice dit seulement à quel point les cris attendus s'y retrouvent.
    private static void ajouterIndiceAcoustique(StringBuilder texte, IndiceAcoustique indice) {
        if (indice == null || !indice.estRenseigne()) {
            return;
        }
        texte.append("Concordance acoustique (indice, non bloquant) : ")
                .append(indice.concordantes())
                .append(" séquence(s) sur ")
                .append(indice.mesurees())
                .append(" présentent les cris attendus.\n");
    }

    /// Compte rendu **honnête** d'un passage reconstruit (#1648) : ni « introuvables », ni fausse promesse.
    /// On explique pourquoi rien n'a pu être relié, et que ce n'est pas la faute des fichiers de l'utilisateur.
    private static String texteReconstruit(RapportReactivation rapport) {
        return "Ce passage a été reconstruit depuis Vigie-Chiro : l'application connaît le nom de ses "
                + rapport.decompte().total()
                + " séquence(s), mais pas la correspondance avec vos fichiers d'origine, ni les empreintes"
                + " nécessaires pour les régénérer.\n\n"
                + "Vos fichiers ne sont pas en cause : ils n'ont simplement pas pu être reliés. La"
                + " réactivation depuis les enregistrements bruts n'est pas encore disponible pour ce type de"
                + " passage.";
    }

    /// Les fichiers **refusés** : jamais rebranchés en silence, chacun avec son motif. Au-delà de
    /// [#ECARTS_DETAILLES], on résume pour garder la modale lisible.
    private static void ajouterEcarts(StringBuilder texte, List<EcartReactivation> ecarts) {
        if (ecarts.isEmpty()) {
            return;
        }
        texte.append('\n')
                .append(ecarts.size())
                .append(" fichier(s) portaient le bon nom mais n'étaient pas le bon audio :")
                .append(" ils n'ont pas été rebranchés (les observations auraient pointé sur le mauvais son).\n");
        ecarts.stream()
                .limit(ECARTS_DETAILLES)
                .forEach(ecart -> texte.append("  • ")
                        .append(ecart.nomFichier())
                        .append(" : ")
                        .append(ecart.motif())
                        .append('\n'));
        if (ecarts.size() > ECARTS_DETAILLES) {
            texte.append("  • … et ").append(ecarts.size() - ECARTS_DETAILLES).append(" autre(s).\n");
        }
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
