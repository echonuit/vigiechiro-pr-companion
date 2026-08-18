package fr.univ_amu.iut.audit.view;

import fr.univ_amu.iut.audit.model.BilanRecuperabilite;
import fr.univ_amu.iut.audit.model.RecuperabiliteNuit;
import fr.univ_amu.iut.audit.model.ResultatReset;
import fr.univ_amu.iut.audit.model.ServiceRecuperabilite;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.commun.model.CauseLisible;
import fr.univ_amu.iut.commun.model.SondeAccessibilite;
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
/// 1. **Regarder.** Le bilan de récupérabilité est établi *avant* toute question : nuit par nuit, ce que
///    deviendrait l'audio. C'est une lecture du disque : elle tourne hors du fil JavaFX, sous le voile du
///    chrome ([OccupationChrome], #1215).
/// 2. **Accepter.** La confirmation **énumère les nuits qui perdraient leur audio**. Cliquer « oui » sur
///    ce texte-là *est* l'acceptation de la perte : c'est ce qui arme `accepterPerte` côté service. Un
///    « êtes-vous sûr ? » générique ne le permettrait pas : on ne peut accepter que ce qu'on a lu.
/// 3. **Repartir.** Le reset s'exécute (sauvegarde complète → base neuve → repeuplement → audit), puis
///    l'application **se ferme** : ses écrans tiennent encore l'ancienne base en mémoire, et rien ne
///    garantirait qu'ils ne l'affichent pas. Un fantôme d'écran, dans un chantier qui traque la perte
///    silencieuse, serait une ironie de trop.
///
/// Le service refuse de lui-même si la plateforme ne répond pas : une base neuve qu'on ne peut pas
/// repeupler est une destruction sèche. L'IHM n'a pas à le redire : elle affiche le refus.
///
/// Les trois dialogues sont **injectables** (#1405), et la fermeture aussi : le geste est donc testable
/// **jusqu'à son effet**, sans figer un test headless sur un `showAndWait()` ni tuer la JVM.
final class GesteReset {

    // Fournisseurs, non instances : l'entrée ☰ est bâtie au démarrage du chrome, AVANT que la base soit
    // ouverte. Résoudre ServiceReset à ce moment-là tirerait les rapprocheurs → idUtilisateurCourant → une
    // requête SQL sur une base pas encore migrée. Les services se résolvent au clic ; le GESTE, lui, naît
    // avec l'entrée : c'est ce qui le rend atteignable par un test (#1405, patron de PorteurSauvegarde).
    private final Supplier<ServiceRecuperabilite> recuperabilite;
    private final Supplier<ServiceReset> reset;
    private final Supplier<ServiceSauvegarde> sauvegarde;
    private final OccupationChrome occupation;

    /// Fermeture de l'application après un reset réussi. **Porteur remplaçable** (#1405), au même titre que
    /// les trois dialogues : sans cela, un test qui déclenche l'entrée ☰ tuerait sa propre JVM.
    private Runnable fermeture;

    private final SelecteurFichierModifiable selecteur;
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Repartir d'une base neuve ?"));
    private final NotificateurModifiable notificateur;

    GesteReset(
            Supplier<ServiceRecuperabilite> recuperabilite,
            Supplier<ServiceReset> reset,
            Supplier<ServiceSauvegarde> sauvegarde,
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
                () -> recuperabilite.get().bilan(),
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
                Optional.of(sauvegarde.get().dossierParDefaut()));
        if (dossier.isEmpty()) {
            return;
        }
        // La sauvegarde de sécurité doit pouvoir s'écrire : un dossier inutilisable est refusé AVANT le
        // reset. Découvrir l'échec après avoir remis la base à neuf serait une perte sèche.
        SondeAccessibilite.Verdict verdict = SondeAccessibilite.sonder(dossier.get());
        if (!verdict.accessible()) {
            notificateur.notifier(
                    NiveauNotification.AVERTISSEMENT,
                    "Dossier inutilisable",
                    "La sauvegarde de sécurité ne peut pas s'écrire ici (" + verdict.motif() + ") :\n" + dossier.get()
                            + "\nChoisissez un autre dossier avant de réinitialiser.");
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
                + " repeuplée depuis Vigie-Chiro.\n\n"
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
    /// (#906) : fermer l'application pendant un reset avertit.
    private void executer(Path dossier, boolean perteAcceptee) {
        occupation.occuper(
                "Reset en cours (sauvegarde, base neuve, repeuplement)…",
                "le reset de la base",
                () -> reset.get().executer(dossier, perteAcceptee),
                this::annoncer,
                echec -> notificateur.notifier(NiveauNotification.AVERTISSEMENT, "Reset impossible", message(echec)));
    }

    /// Sur le fil JavaFX : dire ce qui s'est passé. Le résultat **porte son propre message**, l'IHM
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
        return CauseLisible.messageDe(echec);
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

    /// Remplace la fermeture de l'application (tests) : on vérifie qu'elle a été **demandée**, sans que la
    /// JVM du test y passe.
    void definirFermeture(Runnable double_) {
        this.fermeture = Objects.requireNonNull(double_, "fermeture");
    }
}
