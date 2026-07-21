package fr.univ_amu.iut.importation.outils;

import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.view.ConfirmationsImport;
import fr.univ_amu.iut.importation.viewmodel.AvertissementsInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les **confirmations destructives de l'import** (#147 nuit déjà importée, #279 écrasement d'un
/// passage existant) - et les capture **pour de vrai** (#1468).
///
/// ## Pourquoi cet outil existe
///
/// Ces trois dialogues étaient jusqu'ici **reconstruits à la main** dans `CaptureDialogues`, faute de
/// pouvoir dépendre de `importation` depuis `commun`. Son propre commentaire l'assumait : *« on les
/// reconstruit ici à l'identique »*. Mais **« à l'identique » n'engage personne**, et la doc avait
/// dérivé - de deux façons :
///
/// - l'écrasement se fait en **deux** confirmations successives (le principe, puis le détail de ce qui
///   sera définitivement perdu) ; la documentation n'en montrait **qu'une** ;
/// - le message de la première était **inconnu** du lecteur, alors que c'est lui qui décide.
///
/// ## Comment il s'y prend
///
/// Il ne réécrit **aucun** texte. Il branche un [ConfirmateurCapturant] sur la vraie
/// [ConfirmationsImport] : celle-ci compose ses messages comme en production, le double les **intercepte**,
/// et le dialogue est ensuite construit par le **code de production** ([ConfirmationNavigation#dialogue]).
///
/// C'est le port `Confirmateur` (#1013) qui rend cela possible : ce qu'on a mis en place pour **tester** un
/// geste sert ici à le **photographier** honnêtement.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureConfirmationsImport {

    /// Largeur d'enroulement des messages, en caractères : le snapshot d'un dialogue n'enroule pas tout
    /// seul (cf. [#enrouler]).
    private static final int LARGEUR_LIGNE = 70;

    /// Nuit de démonstration déjà importée (#147) : **les passages**, et non une phrase. La question est
    /// composée par le code de production, comme le dialogue qui la porte.
    ///
    /// Cette constante portait auparavant une phrase écrite à la main, que l'application ne produisait
    /// pas. La capture était donc une fiction plausible : le dialogue était authentique, son contenu
    /// inventé - et rien ne pouvait le signaler, puisque aucun test ne compare une capture au réel.
    private static final List<PassageExistant> DOUBLONS = List.of(new PassageExistant(2, 2026, "640380", "A1"));

    /// Passage écrasé de démonstration : 342 séquences, dont 87 validations observateur (#279).
    private static final ApercuEcrasement ECRASEMENT = new ApercuEcrasement(342, 87);

    private CaptureConfirmationsImport() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        // On rejoue les gestes réels ; le double intercepte, sans jamais confirmer (rien ne s'exécute).
        ConfirmateurCapturant doublon = new ConfirmateurCapturant();
        new ConfirmationsImport(doublon)
                .confirmerImportNuitDejaImportee(AvertissementsInspection.questionNuitDejaImportee(DOUBLONS));

        ConfirmateurCapturant ecrasement = new ConfirmateurCapturant(true);
        new ConfirmationsImport(ecrasement).confirmerEcrasement(ECRASEMENT);

        // Doublon (#2060) et 2ᵉ confirmation d'écrasement (#2223) sont des comptes rendus structurés : le
        // dialogue les aligne par VueCompteRendu, sans enroulement manuel. La 1ʳᵉ d'écrasement (le principe)
        // reste une phrase.
        enregistrerCompteRendu(doublon.comptesRendus().get(0), sortie.resolve("apercu-import-doublon.png"));
        enregistrer(ecrasement.messages().get(0), sortie.resolve("apercu-import-ecrasement-principe.png"));
        enregistrerCompteRendu(ecrasement.comptesRendus().get(0), sortie.resolve("apercu-import-ecrasement.png"));
    }

    /// Rend le dialogue **de production** portant un **compte rendu structuré** (#2060), tel qu'il sera
    /// montré. Pas d'enroulement manuel : [VueCompteRendu] met chaque détail sur sa propre ligne alignée.
    private static void enregistrerCompteRendu(CompteRendu compteRendu, Path fichier) {
        Alert alerte = new ConfirmationNavigation().dialogue(compteRendu);
        alerte.getDialogPane().setPrefWidth(540);
        // Limite connue (#2243) : hors `showAndWait`, un détail **long** de `VueCompteRendu` ne s'enroule
        // pas au snapshot et se coupe par une ellipse - les libellés texte le contournent par `enrouler`,
        // sans équivalent structuré. En production le dialogue s'enroule ; la capture d'un détail court
        // (le doublon) est fidèle, celle d'un détail long (l'écrasement) est tronquée.
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Rend le dialogue **de production** ([ConfirmationNavigation#dialogue]) portant le message réel.
    ///
    /// Seule la **largeur** est imposée : hors `showAndWait`, un `DialogPane` ne contraint pas la sienne, et
    /// le texte s'étalerait sur une seule ligne interminable. On ne touche pas au texte.
    private static void enregistrer(String message, Path fichier) {
        // Le dialogue est celui de la PRODUCTION ([ConfirmationNavigation#dialogue]) : même type, mêmes
        // boutons, même titre. Seul le message est enroulé (cf. [#enrouler]).
        Alert alerte = new ConfirmationNavigation().dialogue(enrouler(message));
        alerte.getDialogPane().setPrefWidth(540);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Insère des retours à la ligne dans le message, **sans en changer un mot**.
    ///
    /// Hors `showAndWait`, un `DialogPane` ne contraint pas sa largeur : son libellé reste sur une ligne
    /// unique, que le snapshot coupe par une ellipse. L'enroulement automatique de JavaFX n'opère pas dans
    /// ce contexte - c'est la raison pour laquelle les anciennes captures **réécrivaient** leurs messages,
    /// retours à la ligne compris. Ici, on part du **vrai** message et on se contente de le **couper aux
    /// espaces** : aucun mot n'est ajouté, retiré ni modifié.
    private static String enrouler(String message) {
        StringBuilder enroule = new StringBuilder();
        int longueurLigne = 0;
        for (String mot : message.split(" ")) {
            if (longueurLigne > 0 && longueurLigne + mot.length() > LARGEUR_LIGNE) {
                enroule.append('\n');
                longueurLigne = 0;
            } else if (longueurLigne > 0) {
                enroule.append(' ');
                longueurLigne++;
            }
            enroule.append(mot);
            longueurLigne += mot.length();
        }
        return enroule.toString();
    }

    /// Feuilles de style partagées (palette + base) : le même thème indigo que l'application.
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css")) {
            var url = ConfirmationNavigation.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        return feuilles;
    }

    /// Confirmateur qui **enregistre** ce qu'on lui demande, au lieu de l'afficher - le même double que
    /// les tests de geste, employé ici pour récolter les **vrais** messages.
    private static final class ConfirmateurCapturant implements fr.univ_amu.iut.commun.view.Confirmateur {

        private final List<String> messages = new ArrayList<>();
        private final List<CompteRendu> comptesRendus = new ArrayList<>();

        /// `true` pour enchaîner sur la confirmation suivante (l'écrasement en demande **deux**).
        private final boolean poursuivre;

        ConfirmateurCapturant() {
            this(false);
        }

        ConfirmateurCapturant(boolean poursuivre) {
            this.poursuivre = poursuivre;
        }

        @Override
        public boolean confirmer(String message) {
            messages.add(message);
            return poursuivre;
        }

        /// On surcharge pour **intercepter le compte rendu tel quel** (#2060) : le laisser retomber sur le
        /// repli textuel du port l'aplatirait, et la capture perdrait la structure qu'on veut montrer.
        @Override
        public boolean confirmer(CompteRendu compteRendu) {
            comptesRendus.add(compteRendu);
            return poursuivre;
        }

        List<String> messages() {
            return messages;
        }

        List<CompteRendu> comptesRendus() {
            return comptesRendus;
        }
    }
}
