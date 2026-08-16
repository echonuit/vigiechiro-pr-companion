package fr.univ_amu.iut.importation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le passage créé pour une nuit porte les paramètres d'acquisition **de cette nuit-là** (#3460).
///
/// Là où [fr.univ_amu.iut.importation.AnalyseurLogPRTest] vérifie que le journal **collecte** toutes
/// les configurations et sait les apparier, ce fichier vérifie que la fabrique d'entités s'en **sert**
/// vraiment. La distinction compte : le journal pouvait très bien porter la bonne réponse pendant que
/// la fabrique continuait de lire les champs plats, et rien n'aurait rougi.
class PassageDeLaNuitTest {

    private final FabriqueEntitesImport fabrique = new FabriqueEntitesImport(Horloge.systeme());

    private static final String SERIE = "1925492";
    private static final LocalDate NUIT_PREMIERE_SESSION = LocalDate.of(2026, 4, 22);
    private static final LocalDate NUIT_SECONDE_SESSION = LocalDate.of(2026, 4, 25);

    /// Un journal de deux sessions, comme une carte laissée plusieurs nuits au même point : le capteur
    /// est repris entre les deux, reconfiguré à **256 kHz**, puis reposé.
    ///
    /// ⚠️ Les lignes viennent de [JournalDeCapteur] et non d'un littéral, ce que `CliquetJournalEnDurTest`
    /// exige : vingt-neuf fichiers recopiaient ce format à la main avant #2868, autant d'endroits à
    /// retoucher le jour où un champ bouge. Concaténer deux nuits est exactement ce qu'un vrai journal
    /// contient, l'enregistreur y écrivant à la suite.
    private static JournalParse journalDeuxSessions() {
        List<String> lignes = new ArrayList<>(
                JournalDeCapteur.lignes(SERIE, NUIT_PREMIERE_SESSION, true, JournalDeCapteur.FREQUENCE_KHZ_PAR_DEFAUT));
        lignes.addAll(JournalDeCapteur.lignes(SERIE, NUIT_SECONDE_SESSION, true, 256));
        return new AnalyseurLogPR().analyser(lignes);
    }

    @Test
    @DisplayName("#3460 : la nuit de la SECONDE session porte les paramètres de la seconde session")
    void la_nuit_de_la_seconde_session_porte_ses_propres_parametres() {
        // Le défaut vécu : la fréquence d'échantillonnage et la bande passante d'une autre session
        // partaient avec la nuit, jusqu'à la plateforme, sans que rien ne le signale. Ce ne sont pas
        // des métadonnées d'agrément : elles conditionnent la transformation des séquences.
        Passage nuitDu25 =
                fabrique.passage(journalDeuxSessions(), 7L, new Prefixe("640380", 2026, 1, "A1"), NUIT_SECONDE_SESSION);

        assertThat(nuitDu25.parametresAcquisition()).contains("256000").doesNotContain("384000");
        // Le journal de référence pose la même fenêtre d'acquisition aux deux sessions : c'est donc la
        // fréquence qui discrimine ici, et c'est elle que le retour de terrain nommait. La fenêtre
        // change bien de session en session dans la vraie vie, et le test unitaire du record
        // (ConfigurationAcquisitionTest) couvre ce champ-là.
        assertThat(nuitDu25.heureDebut()).isEqualTo("20:25:00");
    }

    @Test
    @DisplayName("#3460 : la nuit de la PREMIÈRE session garde les siens, elle ne prend pas la dernière")
    void la_nuit_de_la_premiere_session_garde_les_siens() {
        // Le contre-test qui compte. « Lire la dernière config du fichier » plutôt que la première
        // aurait corrigé la nuit du 25 et cassé celle du 22 : le défaut aurait changé de nuit, pas
        // disparu. C'est pourquoi la règle est « la dernière posée AU PLUS TARD cette nuit-là ».
        Passage nuitDu22 = fabrique.passage(
                journalDeuxSessions(), 7L, new Prefixe("640380", 2026, 1, "A1"), NUIT_PREMIERE_SESSION);

        assertThat(nuitDu22.parametresAcquisition()).contains("384000").doesNotContain("256000");
        assertThat(nuitDu22.heureDebut()).isEqualTo("20:25:00");
        assertThat(nuitDu22.heureFin()).isEqualTo("07:47:00");
    }
}
