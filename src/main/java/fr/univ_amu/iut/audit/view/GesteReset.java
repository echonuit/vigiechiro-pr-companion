package fr.univ_amu.iut.audit.view;

import fr.univ_amu.iut.audit.model.BilanRecuperabilite;
import fr.univ_amu.iut.audit.model.RecuperabiliteNuit;
import fr.univ_amu.iut.audit.model.ResultatReset;
import fr.univ_amu.iut.audit.model.ServiceRecuperabilite;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.NotificationDialogue;
import fr.univ_amu.iut.commun.view.OccupationChrome;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Le **reset guidé** vu de l'IHM (#1419) : la parité de `reset-guide --executer`, en trois temps que
/// l'utilisateur traverse sans jamais avancer à l'aveugle.
///
/// 1. **Regarder.** Le bilan de récupérabilité est établi *avant* toute question — nuit par nuit, ce que
///    deviendrait l'audio. C'est une lecture du disque : elle tourne hors du fil JavaFX, sous le voile du
///    chrome ([OccupationChrome], #1215).
/// 2. **Accepter.** La confirmation **énumère les nuits qui perdraient leur audio**. Cliquer « oui » sur
///    ce texte-là *est* l'acceptation de la perte : c'est ce qui arme `accepterPerte` côté service. Un
///    « êtes-vous sûr ? » générique ne le permettrait pas — on ne peut accepter que ce qu'on a lu.
/// 3. **Repartir.** Le reset s'exécute (sauvegarde complète → base neuve → repeuplement → audit), puis
///    l'application **se ferme** : ses écrans tiennent encore l'ancienne base en mémoire, et rien ne
///    garantirait qu'ils ne l'affichent pas. Un fantôme d'écran, dans un chantier qui traque la perte
///    silencieuse, serait une ironie de trop.
///
/// Le service refuse de lui-même si la plateforme ne répond pas — une base neuve qu'on ne peut pas
/// repeupler est une destruction sèche. L'IHM n'a pas à le redire : elle affiche le refus.
///
/// Les trois dialogues sont **injectables** (#1405), et la fermeture aussi : le geste est donc testable
/// **jusqu'à son effet**, sans figer un test headless sur un `showAndWait()` ni tuer la JVM.
final class GesteReset {

    private final ServiceRecuperabilite recuperabilite;
    private final ServiceReset reset;
    private final ServiceSauvegarde sauvegarde;
    private final OccupationChrome occupation;

    /// Fermeture de l'application après un reset réussi. Injectable : un test vérifie qu'elle a bien été
    /// demandée, sans que la JVM du test y passe.
    private final Runnable fermeture;

    private final SelecteurFichierModifiable selecteur;
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Repartir d'une base neuve ?"));
    private final NotificateurModifiable notificateur;

    GesteReset(
            ServiceRecuperabilite recuperabilite,
            ServiceReset reset,
            ServiceSauvegarde sauvegarde,
            OccupationChrome occupation,
            Supplier<Window> fenetre,
            Runnable fermeture) {
        this.recuperabilite = Objects.requireNonNull(recuperabilite, "recuperabilite");
        this.reset = Objects.requireNonNull(reset, "reset");
        this.sauvegarde = Objects.requireNonNull(sauvegarde, "sauvegarde");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
        this.fermeture = Objects.requireNonNull(fermeture, "fermeture");
        Objects.requireNonNull(fenetre, "fenetre");
        this.selecteur = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(fenetre));
        this.notificateur = new NotificateurModifiable(new NotificationDialogue(fenetre));
    }

    /// Temps 1 : établir le bilan (lecture du disque, hors fil JavaFX), puis le montrer.
    void lancer() {
        occupation.occuper(
                "Analyse de ce qui reviendrait…",
                "l'analyse de récupérabilité",
                recuperabilite::bilan,
                this::demanderAcceptation,
                echec -> notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Analyse impossible", message(echec)));
    }

    /// Temps 2, sur le fil JavaFX : désigner où sauvegarder, **montrer ce qu'on perdrait**, et n'exécuter
    /// que si l'utilisateur l'accepte en connaissance de cause.
    private void demanderAcceptation(BilanRecuperabilite bilan) {
        if (bilan.nuits().isEmpty()) {
            notificateur.notifier(
                    NiveauNotification.INFORMATION,
                    "Rien à réinitialiser",
                    "Il n'y a aucune nuit en base : repartir de zéro ne changerait rien.");
            return;
        }
        Optional<Path> dossier = selecteur.choisirDossier(
                "Dossier où sauvegarder (base + audio) avant de repartir de zéro",
                Optional.of(sauvegarde.dossierParDefaut()));
        if (dossier.isEmpty()) {
            return;
        }
        if (!confirmateur.confirmer(texteDeConfirmation(bilan))) {
            return;
        }
        executer(dossier.get(), bilan.perteAnnoncee());
    }

    /// Le texte que l'utilisateur doit lire avant de dire oui. Il **nomme les nuits** qui perdraient leur
    /// audio : c'est là toute la différence entre une confirmation et un consentement.
    private static String texteDeConfirmation(BilanRecuperabilite bilan) {
        StringBuilder texte = new StringBuilder("La base va être sauvegardée, puis remise à neuf, puis"
                + " repeuplée depuis VigieChiro.\n\n"
                + bilan.resume()
                + "\n");
        if (!bilan.perteAnnoncee()) {
            return texte.append("\nAucune perte : chaque nuit retrouverait son audio.\n\nContinuer ?")
                    .toString();
        }
        texte.append("\nL'audio des nuits suivantes ne reviendra PAS :\n");
        for (RecuperabiliteNuit nuit : bilan.perdues()) {
            texte.append("  - ").append(nuit.enClair()).append('\n');
        }
        texte.append("\nElles deviendront des passages archivés : observations et vérifications")
                .append("\nconsultables, écoute impossible (réactivables si vous retrouvez les fichiers).")
                .append("\n\nAccepter cette perte et continuer ?");
        return texte.toString();
    }

    /// Temps 3 : exécuter hors du fil JavaFX, sous le voile, avec le libellé d'**opération critique**
    /// (#906) — fermer l'application pendant un reset avertit.
    private void executer(Path dossier, boolean perteAcceptee) {
        occupation.occuper(
                "Reset en cours (sauvegarde, base neuve, repeuplement)…",
                "le reset de la base",
                () -> reset.executer(dossier, perteAcceptee),
                this::annoncer,
                echec -> notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Reset impossible", message(echec)));
    }

    /// Sur le fil JavaFX : dire ce qui s'est passé. Le résultat **porte son propre message** — l'IHM
    /// n'a pas à traduire un état en phrase, elle l'affiche.
    private void annoncer(ResultatReset resultat) {
        switch (resultat) {
            case ResultatReset.Refuse refus ->
                notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Reset refusé", refus.enClair());
            case ResultatReset.Fait fait -> {
                notificateur.notifier(
                        NiveauNotification.INFORMATION,
                        "Base remise à neuf",
                        fait.enClair() + "\n\nL'application va se fermer : relancez-la pour repartir sur la base"
                                + " neuve.");
                fermeture.run();
            }
        }
    }

    private static String message(Throwable echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }

    /// Porteur de désignation exposé aux tests (#1405).
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    /// Porteur de confirmation exposé aux tests (#1013).
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Porteur de compte rendu exposé aux tests (#1405).
    NotificateurModifiable notificateur() {
        return notificateur;
    }
}
