package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.view.DialogueProgression;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Action IHM « Réactiver ce passage » (#1302), extraite de [PassageController] (pur câblage, PMD
/// GodClass) : demande le dossier des fichiers réimportés, lance la réactivation **hors du fil
/// JavaFX** dans une **modale de progression annulable** ([DialogueProgression], #1597) — la
/// vérification lit chaque fichier, et un passage reconstruit rapatrie en plus ses `donnees` pour
/// s'ancrer (#1571) : plusieurs dizaines de secondes, d'où barre de progression et bouton « Annuler »
/// plutôt qu'un voile opaque. Elle présente ensuite le **rapport**.
///
/// Le rapport n'est jamais un simple « c'est fait » : il dit combien de séquences ont été
/// rebranchées et **sur quelle preuve** (niveau de confiance), combien ont été **refusées** et
/// pourquoi (un fichier homonyme au contenu différent n'est jamais rebranché en silence), et
/// combien manquent encore.
final class ActionReactivation {

    /// Nombre d'écarts détaillés dans le rapport : au-delà, on résume (une nuit peut en compter des
    /// milliers, et le dialogue doit rester lisible).
    private static final int ECARTS_DETAILLES = 5;

    private final PassageViewModel viewModel;
    private final DialogueProgression dialogue;
    private final Supplier<Window> proprietaire;
    private final NotificateurModifiable notificateur;
    private final SelecteurFichier selecteur;
    private final Runnable recharger;

    /// @param viewModel ViewModel de M-Passage (porte la réactivation)
    /// @param dialogue modale de progression **annulable** (#1597) : la réactivation, potentiellement
    ///     longue (ré-import des `donnees` pour ancrer un passage reconstruit), tourne hors du fil JavaFX
    ///     avec barre de progression et bouton « Annuler » — non plus un voile opaque
    /// @param proprietaire fenêtre propriétaire de la modale, lue **au moment du geste** (la scène n'existe
    ///     pas forcément à la construction du contrôleur)
    /// @param notificateur porteur de compte rendu partagé de l'écran (double capturant en test)
    /// @param selecteur porteur de désignation partagé de l'écran (#1431) : c'est lui qui demande le
    ///     dossier des fichiers d'origine. Un `DirectoryChooser` en dur ici **figeait** tout test du
    ///     geste - le clic ne revenait jamais, et « Réactiver » restait vérifiable seulement par le
    ///     grisage de son bouton
    /// @param recharger rejeu de l'ouverture de l'écran après réactivation (volumes, boutons)
    ActionReactivation(
            PassageViewModel viewModel,
            DialogueProgression dialogue,
            Supplier<Window> proprietaire,
            NotificateurModifiable notificateur,
            SelecteurFichier selecteur,
            Runnable recharger) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.proprietaire = Objects.requireNonNull(proprietaire, "proprietaire");
        this.notificateur = Objects.requireNonNull(notificateur, "notificateur");
        this.selecteur = Objects.requireNonNull(selecteur, "selecteur");
        this.recharger = Objects.requireNonNull(recharger, "recharger");
    }

    /// Demande le dossier des fichiers d'origine, vérifie et rebranche ce qui correspond, puis rend
    /// compte. Aucune confirmation destructive : l'opération **ajoute** de l'audio, elle n'en
    /// supprime pas (les fichiers sont copiés, la sauvegarde de l'utilisateur reste intacte).
    void reactiver() {
        Optional<Path> dossier =
                selecteur.choisirDossier("Dossier des fichiers d'origine à réimporter", Optional.empty());
        if (dossier.isEmpty()) {
            return;
        }
        Path source = dossier.orElseThrow();
        dialogue.lancer(
                proprietaire.get(),
                "Réactivation du passage",
                (progres, jeton) -> viewModel.reactiver(source, progres, jeton),
                this::restituer,
                echec -> notificateur.notifier(
                        NiveauNotification.AVERTISSEMENT, "Réactivation impossible", message(echec)));
    }

    /// Sur le fil JavaFX, après la réactivation : rechargement de l'écran puis compte rendu.
    private void restituer(RapportReactivation rapport) {
        recharger.run();
        notificateur.notifier(
                rapport.divergentes() > 0 ? NiveauNotification.AVERTISSEMENT : NiveauNotification.INFORMATION,
                rapport.complete() ? "Passage réactivé" : "Réactivation partielle",
                texte(rapport));
    }

    /// Compte rendu : ce qui est revenu et sur quelle preuve, ce qui a été refusé et pourquoi, ce
    /// qui manque encore.
    private static String texte(RapportReactivation rapport) {
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
        texte.append('\n')
                .append(
                        rapport.complete()
                                ? "L'audio est de nouveau complet : le passage est écoutable."
                                : "L'audio reste incomplet : "
                                        + rapport.decompte().presentes() + " séquence(s) sur "
                                        + rapport.decompte().total() + " présentes.");
        return texte.toString();
    }

    /// Les fichiers **refusés** : jamais rebranchés en silence, chacun avec son motif. Au-delà de
    /// [#ECARTS_DETAILLES], on résume pour garder le dialogue lisible.
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

    /// Libellé du niveau de confiance **le plus faible** obtenu : c'est lui qui qualifie honnêtement
    /// la réactivation entière.
    private static String libelleConfiance(RapportReactivation rapport) {
        return switch (rapport.confianceMinimale()) {
            case CERTITUDE -> "certitude (empreinte du contenu, ou preuves structurelle et acoustique concordantes)";
            case FORTE -> "forte (nom, taille et durée concordants ; pas d'empreinte en base pour aller plus loin)";
        };
    }

    private static String message(Throwable echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }
}
