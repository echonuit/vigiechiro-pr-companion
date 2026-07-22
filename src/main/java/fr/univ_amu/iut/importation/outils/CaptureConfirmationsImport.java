package fr.univ_amu.iut.importation.outils;

import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
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

        // Doublon (#2060) et 2ᵉ confirmation d'écrasement (#2223) sont des comptes rendus structurés,
        // alignés par VueCompteRendu et réenroulés pour le snapshot (#2243). La 1ʳᵉ d'écrasement (le
        // principe) reste une phrase.
        enregistrerCompteRendu(doublon.comptesRendus().get(0), sortie.resolve("apercu-import-doublon.png"));
        enregistrer(ecrasement.messages().get(0), sortie.resolve("apercu-import-ecrasement-principe.png"));
        enregistrerCompteRendu(ecrasement.comptesRendus().get(0), sortie.resolve("apercu-import-ecrasement.png"));
    }

    /// Rend le dialogue **de production** portant un **compte rendu structuré** (#2060), tel qu'il sera
    /// montré. [VueCompteRendu] met chaque détail sur sa propre ligne alignée.
    ///
    /// Le compte rendu est d'abord **réenroulé** (#2243) : hors `showAndWait`, un libellé `wrapText`
    /// **long** ne s'enroule pas de lui-même au snapshot et se couperait par une ellipse - exactement le
    /// contournement déjà appliqué aux messages **texte** ([#enrouler]), étendu ici aux libellés
    /// structurés. Aucun mot n'est changé, seuls des retours à la ligne sont insérés aux espaces ; la
    /// modale de production, elle, s'enroule d'elle-même à l'affichage.
    private static void enregistrerCompteRendu(CompteRendu compteRendu, Path fichier) {
        Alert alerte = new ConfirmationNavigation().dialogue(enrouler(compteRendu));
        alerte.getDialogPane().setPrefWidth(540);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Réenroule tous les textes d'un compte rendu (mêmes mots, retours à la ligne insérés aux espaces),
    /// pour le snapshot (#2243). Localisé à la capture : la production s'enroule d'elle-même. Le sujet et
    /// la précision d'un détail sont enroulés séparément - [VueCompteRendu] les rejoint au rendu.
    ///
    /// Chaque texte passe par [ApercuFx#enrouler], le découpage **du socle**, que les autres outils de
    /// capture utilisent déjà : cette classe en gardait une copie privée, désormais retirée.
    ///
    /// Visible du paquet pour être testée : c'est la **seule** protection de #2243, puisque le garde-fou
    /// de fidélité ne voit pas cette troncature-là (#2265).
    static CompteRendu enrouler(CompteRendu rendu) {
        List<Constat> constats = rendu.constats().stream()
                .map(CaptureConfirmationsImport::enrouler)
                .toList();
        return new CompteRendu(
                rendu.titre(), ApercuFx.enrouler(rendu.preambule()), constats, ApercuFx.enrouler(rendu.conclusion()));
    }

    /// Réenroule le fait d'un constat et chacun de ses détails (sujet et précision).
    private static Constat enrouler(Constat constat) {
        List<Detail> details = constat.details().stream()
                .map(detail -> new Detail(ApercuFx.enrouler(detail.sujet()), ApercuFx.enrouler(detail.precision())))
                .toList();
        return new Constat(ApercuFx.enrouler(constat.fait()), constat.severite(), details);
    }

    /// Rend le dialogue **de production** ([ConfirmationNavigation#dialogue]) portant le message réel.
    ///
    /// Seule la **largeur** est imposée : hors `showAndWait`, un `DialogPane` ne contraint pas la sienne, et
    /// le texte s'étalerait sur une seule ligne interminable. On ne touche pas au texte.
    private static void enregistrer(String message, Path fichier) {
        // Le dialogue est celui de la PRODUCTION ([ConfirmationNavigation#dialogue]) : même type, mêmes
        // boutons, même titre. Seul le message est enroulé, par le socle ([ApercuFx#enrouler]).
        Alert alerte = new ConfirmationNavigation().dialogue(ApercuFx.enrouler(message));
        alerte.getDialogPane().setPrefWidth(540);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
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
