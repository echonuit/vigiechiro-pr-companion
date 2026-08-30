package fr.univ_amu.iut.analyse.outils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Garde la **nuit de démonstration** de l'écran Synthèse : elle doit produire les quatre messages que
/// la colonne « Activité » sait écrire, plus le bouclier PNA.
///
/// ## Pourquoi ce test existe
///
/// Cette démonstration a menti trois fois, et jamais un test n'a rougi.
///
/// Elle a montré une Barbastelle *(indicatif)* et une Sauterelle verte « hors référentiel », deux états
/// que le référentiel contredit (#3018). Elle a affiché les **mêmes quantiles pour trois espèces
/// différentes**, et un texte tronqué. Elle a enfin perdu le nombre fondateur de l'[ADR 2351] en
/// devenant calculée, laissant la fiche utilisateur poser sa question au-dessus d'une image qui y
/// répondait autrement.
///
/// Aucun de ces défauts n'était détectable autrement qu'en **ouvrant l'image**. Ce test ferme cette
/// porte : il exécute le vrai semis et le vrai service, et lit ce que l'écran écrirait.
///
/// Il ne rend pas la revue visuelle inutile - un test ne voit ni une troncature, ni un glyphe absent.
/// Il garantit seulement que les **états** montrés restent ceux qu'on croit montrer.
class NuitDeDemonstrationSyntheseTest {

    private static List<LigneSynthese> lignes;

    /// La racine des espaces de ce banc, **statique** parce que l'appel vit dans une
    /// méthode statique. JUnit la crée une fois pour la classe et la supprime au bout
    /// (#4924).
    @TempDir
    private static Path dossierTemporaire;

    @BeforeAll
    static void semerEtAgreger() throws IOException, SQLException {
        Path workspace = Files.createTempDirectory(dossierTemporaire, "vc-test-nuit-demo");
        System.setProperty("vigiechiro.workspace", workspace.toString());

        Injector injecteur = CaptureSynthese.creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        injecteur.getInstance(MigrationSchema.class).migrer();
        long idPassage = CaptureSynthese.semerLaNuit(source);

        lignes = injecteur.getInstance(ServiceSynthese.class).pour(idPassage, false, CaptureSynthese.CARRE, null);
    }

    private static Map<String, LigneSynthese> parCode() {
        return lignes.stream().collect(Collectors.toMap(LigneSynthese::codeTaxon, Function.identity()));
    }

    @Test
    @DisplayName("#3051 : les quatre messages de la colonne « Activité » sont tous montrés")
    void les_quatre_messages_sont_montres() {
        assertThat(lignes)
                .as("la démonstration doit couvrir les quatre messages, sinon un état dérive sans témoin")
                .extracting(LigneSynthese::libelleClasse)
                .contains(
                        "Forte", // une classe fondée sur une déclinaison fiable
                        "Forte (indicatif)", // la même classe, sur une déclinaison peu fiable
                        "Pas de seuil pour ce contexte", // connue du référentiel, pas de ce contexte
                        "Non couvert par le référentiel"); // étrangère au référentiel
    }

    @Test
    @DisplayName("#2351 : la Pipistrelle de Kuhl produit les 718 contacts que citent l'ADR, la maquette et la fiche")
    void le_nombre_fondateur_est_produit() {
        LigneSynthese kuhl = parCode().get("Pipkuh");

        assertThat(kuhl.contacts())
                .as("718 est l'ancre de l'ADR 2351 : la capture doit la produire, pas s'en approcher")
                .isEqualTo(718);
        assertThat(kuhl.libelleClasse())
                .as("et 718 doit tomber en « Forte », la classe que le récit lui associe")
                .isEqualTo("Forte");
    }

    @Test
    @DisplayName("#3018 : deux espèces ne partagent jamais les mêmes seuils")
    void chaque_espece_porte_ses_propres_seuils() {
        // La démonstration bouchonnée posait « Q25 = 12 · Q75 = 480 » sur trois espèces différentes.
        // Personne ne l'a vu pendant des mois : trois lignes identiques passent pour une mise en page.
        List<String> seuils = lignes.stream()
                .map(LigneSynthese::seuils)
                .flatMap(java.util.Optional::stream)
                .map(Object::toString)
                .toList();

        assertThat(seuils)
                .as("le semis doit produire des seuils, sinon ce test ne vérifie rien")
                .isNotEmpty();
        assertThat(seuils).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("#3051 : contacts et fichiers diffèrent, comme la fiche l'enseigne")
    void les_contacts_ne_valent_pas_les_fichiers() {
        LigneSynthese kuhl = parCode().get("Pipkuh");

        assertThat(kuhl.fichiers())
                .as("la fiche explique que les deux nombres ne disent pas la même chose : "
                        + "la démonstration doit le montrer plutôt que l'affirmer")
                .isLessThan(kuhl.contacts());
    }
}
