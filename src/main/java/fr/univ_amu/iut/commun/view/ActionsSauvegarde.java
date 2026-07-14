package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Actions IHM de **sauvegarde / restauration** de la base (#148), déclenchées depuis le menu « ☰ » du
/// chrome. Extrait de [MainController] (pur câblage) : demande le dossier ou le fichier, confirme la
/// restauration (destructive) et présente le résultat. La copie de la base (potentiellement volumineuse)
/// tourne **hors du fil JavaFX** sous le voile du chrome ([OccupationChrome], #1215), qui pose aussi
/// l'opération critique (#906) : fermer l'application en pleine copie déclenche l'avertissement du socle.
///
/// Les **trois** dialogues sont injectables (#1405) : la désignation par [SelecteurFichierModifiable],
/// le oui/non par [ConfirmateurModifiable], le compte rendu par [NotificateurModifiable]. Il fallait bien
/// les trois : ces quatre gestes **commencent** par un sélecteur natif, qui fige un test headless
/// exactement comme un `Alert.showAndWait()`. Rendre le seul compte rendu remplaçable n'aurait donc rien
/// débloqué ici - le test se serait arrêté à la première ligne. C'est ce qui laissait la restauration,
/// qui **écrase la base**, sans le moindre test de son geste.
final class ActionsSauvegarde {

    private final ServiceSauvegarde service;
    private final OccupationChrome occupation;
    private final Runnable apresRestauration;

    /// Désignation du fichier ou du dossier : porteur partagé injectable (#1405), double répondant en test.
    private final SelecteurFichierModifiable selecteur;

    /// Confirmation d'action destructive : porteur partagé injectable (#1013), stub déterministe en test.
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Restaurer la base ?"));

    /// Compte rendu de l'action : porteur partagé injectable (#1405), double capturant en test.
    private final NotificateurModifiable notificateur;

    /// @param service service de sauvegarde/restauration
    /// @param occupation voile d'occupation du chrome (#1215)
    /// @param fenetre fournisseur de la fenêtre propriétaire des dialogues (évalué au clic)
    /// @param apresRestauration action à jouer après une restauration réussie (ex. retour à l'accueil pour
    ///     relire la base restaurée)
    ActionsSauvegarde(
            ServiceSauvegarde service,
            OccupationChrome occupation,
            Supplier<Window> fenetre,
            Runnable apresRestauration) {
        this.service = Objects.requireNonNull(service, "service");
        this.occupation = Objects.requireNonNull(occupation, "occupation");
        this.apresRestauration = Objects.requireNonNull(apresRestauration, "apresRestauration");
        Objects.requireNonNull(fenetre, "fenetre");
        this.selecteur = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(fenetre));
        this.notificateur = new NotificateurModifiable(new NotificationDialogue(fenetre));
    }

    /// Demande un dossier (par défaut `<workspace>/sauvegardes`, emplacement **configurable**), y écrit une
    /// sauvegarde horodatée **hors du fil JavaFX** (#1215) et confirme le chemin obtenu.
    void sauvegarder() {
        Optional<Path> dossier = selecteur.choisirDossier("Dossier où enregistrer la sauvegarde", dossierParDefaut());
        if (dossier.isEmpty()) {
            return;
        }
        occupation.occuper(
                "Sauvegarde de la base…",
                "la sauvegarde de la base",
                () -> service.sauvegarder(dossier.get()),
                fichier -> notificateur.notifier(
                        NiveauNotification.INFORMATION,
                        "Sauvegarde créée",
                        "La base a été sauvegardée dans :\n" + fichier),
                echec -> signalerEchec("Sauvegarde impossible", echec));
    }

    /// Sauvegarde **complète** (#1346) : la base **et** les dossiers de session (l'audio). C'est celle qu'il
    /// faut avant un reset (#1151), et la seule qui protège vraiment : la plateforme ne rend **pas** l'audio
    /// d'un dépôt en archives (#1297), le disque en est l'unique source.
    ///
    /// Le moteur existait depuis #1142 mais **personne ne pouvait l'appeler** : ni menu, ni CLI. La copie
    /// peut peser plusieurs Go : elle tourne donc hors du fil JavaFX, sous le voile du chrome, avec le
    /// libellé d'**opération critique** (#906) : fermer l'application en pleine copie avertit.
    ///
    /// Le bilan **dit ce qui manque** : une racine de session non montée (carte SD, disque débranché) est
    /// sautée, et l'annoncer est tout l'objet de l'action : une sauvegarde qu'on croit complète et qui ne
    /// l'est pas vaut moins que pas de sauvegarde du tout.
    void sauvegarderComplet() {
        Optional<Path> dossier = selecteur.choisirDossier(
                "Dossier où enregistrer la sauvegarde complète (base + audio)", dossierParDefaut());
        if (dossier.isEmpty()) {
            return;
        }
        if (!confirmateur.confirmer("La sauvegarde complète copie la base ET tous vos dossiers de session"
                + " (l'audio). Elle peut peser plusieurs gigaoctets et prendre du temps. Continuer ?")) {
            return;
        }
        occupation.occuper(
                "Sauvegarde complète (base + audio)…",
                "la sauvegarde complète",
                () -> service.sauvegarderComplet(dossier.get()),
                this::annoncerBilan,
                echec -> signalerEchec("Sauvegarde impossible", echec));
    }

    /// Annonce le bilan d'une sauvegarde complète : une information si tout a été copié, un
    /// **avertissement** s'il manque des dossiers : la sauvegarde existe alors, mais elle est incomplète.
    private void annoncerBilan(BilanSauvegarde bilan) {
        notificateur.notifier(
                bilan.incomplete() ? NiveauNotification.AVERTISSEMENT : NiveauNotification.INFORMATION,
                bilan.incomplete() ? "Sauvegarde incomplète" : "Sauvegarde complète créée",
                "Sauvegarde écrite dans :\n" + bilan.dossier() + "\n\n" + bilan.enClair());
    }

    /// Restaure une sauvegarde **complète** (#1346) : la base **et** les dossiers de session. Destructif :
    /// l'état local est écrasé, donc confirmé.
    void restaurerComplet() {
        Optional<Path> dossier =
                selecteur.choisirDossier("Choisir un dossier de sauvegarde complète à restaurer", dossierParDefaut());
        if (dossier.isEmpty()) {
            return;
        }
        String nom = nomDe(dossier.get());
        if (!confirmateur.confirmer("La base actuelle ET vos dossiers de session seront remplacés par le"
                + " contenu de « " + nom + " ». L'état courant de la base est d'abord mis de"
                + " côté (vigiechiro.db.avant-restauration), mais pas l'audio. Continuer ?")) {
            return;
        }
        occupation.occuper(
                "Restauration complète (base + audio)…",
                "la restauration complète",
                () -> {
                    service.restaurerComplet(dossier.get());
                    return nom;
                },
                this::restituerRestaurationComplete,
                echec -> signalerEchec("Restauration impossible", echec));
    }

    /// Demande un fichier de sauvegarde, **confirme** le remplacement (destructif) puis restaure **hors du
    /// fil JavaFX** (#1215). En cas de succès, joue [#apresRestauration] pour relire la base restaurée.
    void restaurer() {
        Optional<Path> fichier = selecteur.choisirFichier(
                "Choisir une sauvegarde à restaurer", dossierParDefaut(), FiltreFichier.baseSqlite());
        if (fichier.isEmpty()) {
            return;
        }
        String nom = nomDe(fichier.get());
        if (!confirmateur.confirmer("La base actuelle sera remplacée par « " + nom + " ». Son état"
                + " courant est d'abord mis de côté (vigiechiro.db.avant-restauration). Continuer ?")) {
            return;
        }
        occupation.occuper(
                "Restauration de la base…",
                "la restauration de la base",
                () -> {
                    service.restaurer(fichier.get());
                    return nom;
                },
                this::restituerRestauration,
                echec -> signalerEchec("Restauration impossible", echec));
    }

    /// Sur le fil JavaFX, après restauration de la base seule : compte rendu puis relecture de la base.
    private void restituerRestauration(String nom) {
        notificateur.notifier(
                NiveauNotification.INFORMATION, "Base restaurée", "La base a été restaurée depuis « " + nom + " ».");
        apresRestauration.run();
    }

    /// Sur le fil JavaFX, après restauration complète : compte rendu puis relecture de la base.
    private void restituerRestaurationComplete(String nom) {
        notificateur.notifier(
                NiveauNotification.INFORMATION,
                "Sauvegarde restaurée",
                "La base et les dossiers de session ont été restaurés depuis « " + nom + " ».");
        apresRestauration.run();
    }

    /// Un échec de copie : rien n'a abouti, et on le dit.
    private void signalerEchec(String entete, Throwable echec) {
        notificateur.notifier(NiveauNotification.AVERTISSEMENT, entete, message(echec));
    }

    /// Porteur de désignation exposé aux tests (#1405) : `selecteur().definir(double)`.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    /// Porteur de confirmation exposé aux tests (#1013) : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Porteur de compte rendu exposé aux tests (#1405) : `notificateur().definir(double)`.
    NotificateurModifiable notificateur() {
        return notificateur;
    }

    /// Dossier proposé à l'ouverture des sélecteurs (emplacement de sauvegarde configuré).
    private Optional<Path> dossierParDefaut() {
        return Optional.of(service.dossierParDefaut());
    }

    /// Nom lisible du fichier ou dossier désigné, tel qu'il est repris dans la confirmation.
    private static String nomDe(Path chemin) {
        return chemin.getFileName().toString();
    }

    private static String message(Throwable echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }
}
