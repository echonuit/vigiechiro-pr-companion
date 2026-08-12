package fr.univ_amu.iut.maj.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.VerifierMiseAJour;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.view.AnnonceChrome;
import fr.univ_amu.iut.maj.model.ConseilDeMiseAJour;
import fr.univ_amu.iut.maj.model.JarDeVersion;
import fr.univ_amu.iut.maj.model.NumeroDeVersion;
import fr.univ_amu.iut.maj.model.VerificateurMiseAJour;
import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/// La parité CLI ↔ IHM du conseil de mise à jour (ADR 0014), **vérifiée** et non affirmée.
///
/// Les deux rendus consomment la même phrase, produite par [ConseilDeMiseAJour]. C'est vrai *par
/// construction* aujourd'hui - encore faut-il que ça le reste. Le jour où quelqu'un compose le
/// conseil dans un seul des deux, ces tests le disent.
///
/// ⚠️ **Ce que ces tests prouvent dépend du système où ils tournent**, et il vaut mieux le savoir que
/// de lire leur vert de travers :
///
/// - sous **Windows**, ils prouvent que les deux rendus portent le geste `winget` ;
/// - **ailleurs** (donc en CI, sur Linux), ils prouvent qu'aucun des deux ne l'invente. C'est le
///   défaut le plus probable : une phrase codée en dur dans un rendu, qui conseillerait `winget` à un
///   utilisateur de Debian.
///
/// Dans les deux cas, ils prouvent que les deux rendus **disent la même chose**, ce qui est l'objet.
class ConseilRenduTest {

    private static final VersionDisponible PUBLIEE =
            new VersionDisponible(new NumeroDeVersion(2, 200, 0), "https://exemple/releases/v2.200.0");

    @Test
    @DisplayName("l'annonce de l'IHM et la commande CLI rendent le MÊME conseil")
    void lesDeuxRendusDisentLaMemeChose(@TempDir Path dossier) throws Exception {
        Optional<String> attendu = ConseilDeMiseAJour.pourCeSysteme();

        assertThat(messageDeLAnnonce(dossier)).satisfies(message -> verifier(message, attendu));
        assertThat(sortieDeLaCommande(dossier)).satisfies(sortie -> verifier(sortie, attendu));
    }

    @Test
    @DisplayName("aucun rendu ne conseille winget de son propre chef")
    void aucunRenduNInventeLeConseil(@TempDir Path dossier) throws Exception {
        // Le garde-fou qui vaut sur TOUT système : le conseil ne peut venir que du modèle. S'il
        // apparaît dans un rendu alors que le modèle se tait, c'est qu'il y a été écrit en dur.
        boolean leModeleConseille = ConseilDeMiseAJour.pourCeSysteme().isPresent();

        assertThat(messageDeLAnnonce(dossier).contains("winget")).isEqualTo(leModeleConseille);
        assertThat(sortieDeLaCommande(dossier).contains("winget")).isEqualTo(leModeleConseille);
    }

    private static void verifier(String rendu, Optional<String> attendu) {
        if (attendu.isPresent()) {
            assertThat(rendu).contains(attendu.orElseThrow());
        } else {
            assertThat(rendu).doesNotContain("winget");
        }
    }

    private static String messageDeLAnnonce(Path dossier) throws Exception {
        VersionApplication version = JarDeVersion.annoncant(dossier, "2.199.0");
        AnnonceMiseAJour annonce =
                new AnnonceMiseAJour(new VerificateurMiseAJour(version, () -> Optional.of(PUBLIEE)), version);
        AnnonceChrome.Annonce rendue = annonce.chercher().orElseThrow();
        return rendue.message();
    }

    private static String sortieDeLaCommande(Path dossier) throws Exception {
        VersionApplication version = JarDeVersion.annoncant(dossier, "2.199.0");
        VerifierMiseAJour commande =
                new VerifierMiseAJour(new VerificateurMiseAJour(version, () -> Optional.of(PUBLIEE)), version);

        StringWriter tampon = new StringWriter();
        int code = new CommandLine(commande).setOut(new PrintWriter(tampon)).execute();

        // 10 : « une version plus récente existe », code distinct du succès pour être pilotable en
        // script. On le vérifie ici parce qu'un rendu vide passerait sinon inaperçu.
        assertThat(code).isEqualTo(10);
        return tampon.toString();
    }
}
