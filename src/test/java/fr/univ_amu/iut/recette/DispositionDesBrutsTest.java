package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.RapportInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Où** les WAV vivent sur la carte, et pourquoi le défaut a changé.
///
/// Les enregistreurs déposent leurs fichiers **à la racine** de la carte. Les seize specs de recette
/// produisaient pourtant un `bruts/`, sans qu'aucune ne puisse en décider : le champ n'existait pas.
/// La disposition du parc n'était donc jouée par aucun cas de recette, alors que `InspecteurDossier`
/// la gère exprès (#5281).
///
/// Le produit accepte les deux, et ces cas le montrent en **inspectant réellement** chaque arbre :
/// c'est ce que garde `GenerationCartesSDCliquetTest` spec par spec, et ce qui est éprouvé ici sur la
/// bascule elle-même.
class DispositionDesBrutsTest {

    private static final Path SPEC_NOMINALE = Path.of("recette", "fixtures", "spec", "sd-nominale.yaml");
    private static final int ORIGINAUX = 6;

    private final LecteurSpec lecteur = new LecteurSpec();
    private final GenerateurCartesSD generateur = new GenerateurCartesSD();
    private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());

    @TempDir
    private Path bac;

    @Test
    @DisplayName("par DÉFAUT, les WAV sont à la RACINE - ce qu'un enregistreur produit")
    void par_defaut_les_wav_sont_a_la_racine() throws IOException {
        Path carte = genererAvec(false, "plate");

        assertThat(Files.isDirectory(carte.resolve("bruts")))
                .as("une carte du parc n'a pas de dossier « bruts » : les enregistreurs déposent à la"
                        + " racine, et c'est cette disposition-là que la recette doit jouer")
                .isFalse();
        assertThat(wavSous(carte)).hasSize(ORIGINAUX);
    }

    @Test
    @DisplayName("le sous-dossier reste POSSIBLE, pour ceux qui rangent")
    void le_sous_dossier_reste_possible() throws IOException {
        Path carte = genererAvec(true, "rangee");

        assertThat(Files.isDirectory(carte.resolve("bruts"))).isTrue();
        assertThat(wavSous(carte.resolve("bruts"))).hasSize(ORIGINAUX);
        // Et rien à la racine : les deux dispositions s'excluent, sinon l'inspection compterait deux
        // fois les mêmes nuits.
        assertThat(wavSous(carte).stream().filter(w -> w.getParent().equals(carte)))
                .isEmpty();
    }

    @Test
    @DisplayName("l'inspection RÉELLE compte les mêmes originaux dans les deux dispositions")
    void l_inspection_reelle_voit_la_meme_chose() throws IOException {
        // Le vrai enjeu : le produit gère les deux branches, et une seule était jouée en recette.
        RapportInspection plate = inspecteur.inspecter(genererAvec(false, "plate"));
        RapportInspection rangee = inspecteur.inspecter(genererAvec(true, "rangee"));

        assertThat(plate.originaux()).hasSize(ORIGINAUX);
        assertThat(rangee.originaux()).hasSize(ORIGINAUX);
    }

    /// La spec nominale, avec la disposition qu'on veut éprouver.
    private Path genererAvec(boolean dansUnSousDossier, String nom) throws IOException {
        SpecCarteSd lue = lecteur.lire(SPEC_NOMINALE);
        SpecCarteSd spec = new SpecCarteSd(
                lue.fixture(),
                lue.but(),
                lue.journal(),
                lue.thlog(),
                lue.wav(),
                lue.enregistreurs(),
                lue.prefixe(),
                lue.zip(),
                dansUnSousDossier,
                lue.attendu());
        Path carte = bac.resolve(nom);
        generateur.genererVers(spec, carte);
        return carte;
    }

    private static List<Path> wavSous(Path racine) throws IOException {
        try (Stream<Path> arbre = Files.walk(racine)) {
            return arbre.filter(p -> p.getFileName().toString().endsWith(".wav"))
                    .toList();
        }
    }

    @Test
    @DisplayName("le champ est LU depuis le YAML, et pas seulement honoré une fois construit")
    void le_champ_est_lu_depuis_le_yaml() throws IOException {
        // Trouvé à la passe 6 de la clôture de #5357 : les trois cas ci-dessus construisent la spec à
        // la main, donc aucun ne traverse `LecteurSpec`. Un lecteur qui ignorerait la clé ferait
        // retomber `sd-prefixee` à plat sans que rien ne rougisse - la carte resterait inspectable, et
        // c'est précisément ce qui rend la perte silencieuse.
        SpecCarteSd prefixee = lecteur.lire(Path.of("recette", "fixtures", "spec", "sd-prefixee.yaml"));
        SpecCarteSd nominale = lecteur.lire(SPEC_NOMINALE);

        assertThat(prefixee.brutsDansUnSousDossier())
                .as("sd-prefixee est la SEULE carte du corpus rangée dans « bruts/ », et elle le déclare")
                .isTrue();
        assertThat(nominale.brutsDansUnSousDossier())
                .as("une spec qui ne dit rien décrit une carte PLATE : c'est le défaut du parc")
                .isFalse();
    }
}
