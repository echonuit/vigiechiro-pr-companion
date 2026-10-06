package fr.univ_amu.iut.qualification.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.qualification.view.QualificationController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;

/// Capture l'emport d'une nuit tel que l'écran de vérification le montre : le menu de la liste, où
/// vivent les quatre gestes, et la colonne « Avis relecteur » une fois un avis repris.
///
/// **Une classe à part de [CaptureQualification], et non deux captures de plus dans la sienne.** Ses
/// images se suivent sur un même écran piloté pas à pas : y glisser un avis de relecteur changerait
/// celles qui viennent après. Ici la base est posée d'abord, puis l'écran s'ouvre dessus, comme il
/// s'ouvre chez l'utilisateur.
///
/// La nuit est celle de [CaptureQualification]. L'état est celui de l'expéditeur après le retour de
/// son relecteur : il a jugé douze séquences, et un avis signé est rangé à côté des huit premières.
/// Les avis sont posés par le DAO de la production, celui-là même que la reprise d'un avis emploie.
public final class CaptureQualificationEmport {

    private static final String QUALIF_FXML = "/fr/univ_amu/iut/qualification/view/Qualification.fxml";
    private static final String RELECTEUR = "claire";
    private static final int TAILLE_SELECTION = 30;
    private static final int NB_JUGEES = 12;
    private static final int NB_RELUES = 8;
    private static final int LARGEUR = 1500;

    private CaptureQualificationEmport() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
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

    /// Injecteur utilisé par cet outil de capture : celui de [CaptureQualification], exposé pour le
    /// garde-fou de câblage.
    public static Injector creerInjecteur() {
        return CaptureQualification.creerInjecteur();
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-emport");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        long idPassage = CaptureQualification.seeder(source, workspace);
        poserLesDeuxAvis(injecteur, idPassage);

        FXMLLoader loader = new FXMLLoader(CaptureQualificationEmport.class.getResource(QUALIF_FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        QualificationController controleur = loader.getController();
        // Plus large que les autres aperçus de l'écran : à 1240 points, la table déborde et la colonne
        // d'avis, la dernière, est rognée par le défilement horizontal. L'image doit la donner à lire.
        Scene scene = new Scene(vue, LARGEUR, 900);
        controleur.ouvrirSur(new ContextePassage(idPassage, 1, new ContexteSite("640380", "A1", null)));

        Path colonne = sortie.resolve("apercu-qualification-avis-relecteur.png");
        ApercuFx.enregistrerPng(scene, colonne);
        System.out.println("Apercu ecrit dans " + colonne.toAbsolutePath());

        Path menu = sortie.resolve("apercu-qualification-menu-emport.png");
        if (!(vue.lookup("#menuOutils") instanceof MenuButton menuOutils)) {
            throw new IllegalStateException(
                    "Le menu de la liste est introuvable : l'aperçu de l'emport n'a rien à montrer.");
        }
        if (!ApercuFx.enregistrerMenuOuvert(menuOutils, menu)) {
            throw new IllegalStateException("Le menu de la liste ne s'est pas rendu : " + menu);
        }
        System.out.println("Apercu ecrit dans " + menu.toAbsolutePath());
    }

    /// Ce que l'expéditeur a jugé, puis ce que son relecteur en a dit : deux avis qui s'accordent ou
    /// non avec les siens, et des séquences que personne n'a relues.
    private static void poserLesDeuxAvis(Injector injecteur, long idPassage) {
        ServiceQualification service = injecteur.getInstance(ServiceQualification.class);
        SelectionDao selections = injecteur.getInstance(SelectionDao.class);
        SelectionDEcoute selection =
                service.creerSelection(idPassage, MethodeSelection.REPARTITION_TEMPORELLE, TAILLE_SELECTION);
        List<SequenceSelectionnee> lignes = selections.listerSequences(selection.id());
        for (int rang = 0; rang < NB_JUGEES && rang < lignes.size(); rang++) {
            Long idSequence = lignes.get(rang).idSequence();
            service.marquerSequenceEcoutee(selection.id(), idSequence);
            service.enregistrerVerdictFichier(idPassage, idSequence, verdictDeLExpediteur(rang));
            if (rang < NB_RELUES) {
                selections.marquerAvisDeRelecteur(selection.id(), idSequence, avisDuRelecteur(rang), RELECTEUR);
            }
        }
    }

    private static VerdictFichier verdictDeLExpediteur(int rang) {
        if (rang == 4 || rang == 8 || rang == 11) {
            return VerdictFichier.MAUVAIS;
        }
        return rang == 6 || rang == 10 ? VerdictFichier.INEXPLOITABLE : VerdictFichier.BON;
    }

    /// Le relecteur suit l'expéditeur, sauf sur deux séquences : c'est le désaccord qui donne son
    /// sens à la colonne.
    private static VerdictFichier avisDuRelecteur(int rang) {
        if (rang == 2) {
            return VerdictFichier.MAUVAIS;
        }
        return rang == 4 ? VerdictFichier.BON : verdictDeLExpediteur(rang);
    }
}
