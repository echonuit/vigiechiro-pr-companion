package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.FormatTadarida;
import fr.univ_amu.iut.validation.model.LigneObservation;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.ResultatParseTadarida;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du [ParserCsvTadarida] sur les **fixtures réelles** (473 observations, copiées dans
/// `src/test/resources/validation/` depuis le jeu de données SAÉ). Couvre :
///
/// - la détection de format Brut (tout guillemeté) vs Vu (entête nue), parcours P7 / R17 ;
/// - le mapping des 11 colonnes Tadarida en [LigneObservation] ;
/// - l'équivalence Brut ↔ Vu (les deux fichiers se parsent en observations identiques, malgré
///   l'encodage différent des champs vides) ;
/// - le round-trip parseur ↔ [ExportVuCsv] sur les 473 lignes réelles.
class ParserCsvTadaridaTest {

    private final ParserCsvTadarida parser = new ParserCsvTadarida();

    private static Path fixture(String nom) {
        try {
            return Path.of(ParserCsvTadaridaTest.class
                    .getResource("/validation/" + nom)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path brut() {
        return fixture("observations_brut.csv");
    }

    private static Path vu() {
        return fixture("observations_vu.csv");
    }

    @Test
    @DisplayName("Le format Brut est détecté (entête entièrement guillemetée)")
    void detecte_le_format_brut() {
        assertThat(parser.detecterFormat(brut())).isEqualTo(FormatTadarida.BRUT);
    }

    @Test
    @DisplayName("Le format Vu est détecté (entête nue, sans guillemets)")
    void detecte_le_format_vu() {
        assertThat(parser.detecterFormat(vu())).isEqualTo(FormatTadarida.VU);
    }

    @Test
    @DisplayName("La détection ne repose pas sur le nom de fichier mais sur le contenu")
    void detecte_le_format_sur_le_contenu() {
        String brutInline = "\"nom du fichier\";\"tadarida_taxon\"\n\"seq_000\";\"Pippip\"\n";
        String vuInline = "nom du fichier;tadarida_taxon\nseq_000;Pippip\n";
        assertThat(parser.detecterFormat(brutInline)).isEqualTo(FormatTadarida.BRUT);
        assertThat(parser.detecterFormat(vuInline)).isEqualTo(FormatTadarida.VU);
    }

    @Test
    @DisplayName("Le fichier Brut réel contient 473 observations")
    void parse_les_473_observations_brut() {
        ResultatParseTadarida parse = parser.parser(brut());

        assertThat(parse.format()).isEqualTo(FormatTadarida.BRUT);
        assertThat(parse.taille()).isEqualTo(473);
        assertThat(parse.lignes()).hasSize(473);
    }

    @Test
    @DisplayName("Le fichier Vu réel contient 473 observations")
    void parse_les_473_observations_vu() {
        ResultatParseTadarida parse = parser.parser(vu());

        assertThat(parse.format()).isEqualTo(FormatTadarida.VU);
        assertThat(parse.taille()).isEqualTo(473);
    }

    @Test
    @DisplayName("La première observation Brut est mappée colonne par colonne")
    void mappe_correctement_la_premiere_ligne() {
        LigneObservation premiere = parser.parser(brut()).lignes().get(0);

        assertThat(premiere.nomSequence()).isEqualTo("Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922_000");
        assertThat(premiere.debutS()).isEqualTo(0.3);
        assertThat(premiere.finS()).isEqualTo(3.9);
        assertThat(premiere.frequenceMedianeKHz()).isEqualTo(153);
        assertThat(premiere.taxonTadarida()).isEqualTo("noise");
        assertThat(premiere.probTadarida()).isEqualTo(0.93);
        assertThat(premiere.taxonAutreTadarida()).as("colonne vide → null").isNull();
        assertThat(premiere.taxonObservateur())
                .as("observateur non renseigné dans le Brut")
                .isNull();
        assertThat(premiere.probObservateur()).isNull();
        assertThat(premiere.modeValidation()).isEqualTo(ModeValidation.NON_VALIDE);
    }

    @Test
    @DisplayName("Une 2e proposition multi-valuée est conservée telle quelle (raw)")
    void conserve_la_seconde_proposition_multi_valuee() {
        assertThat(parser.parser(brut()).lignes())
                .anySatisfy(ligne -> assertThat(ligne.taxonAutreTadarida()).isEqualTo("Tetvir, Pippip, Phogri"));
    }

    @Test
    @DisplayName("Brut et Vu se parsent en observations strictement identiques (champs vides normalisés)")
    void brut_et_vu_parsent_en_observations_identiques() {
        assertThat(parser.parser(vu()).lignes())
                .as("le guillemet seul du Vu et le champ vide du Brut donnent le même null")
                .isEqualTo(parser.parser(brut()).lignes());
    }

    @Test
    @DisplayName("Round-trip : parser(Brut) → ExportVuCsv → parser(Vu) redonne les mêmes lignes")
    void round_trip_parseur_export() {
        ExportVuCsv export = new ExportVuCsv();
        ResultatParseTadarida initial = parser.parser(brut());

        String vuExporte = export.versChaine(initial.lignes());
        ResultatParseTadarida reparse = parser.parser(vuExporte);

        assertThat(reparse.format()).isEqualTo(FormatTadarida.VU);
        assertThat(reparse.lignes()).isEqualTo(initial.lignes());
    }

    @Test
    @DisplayName("Une entête sans la colonne obligatoire « nom du fichier » est rejetée")
    void entete_incomplete_est_rejetee() {
        String sansNom = "temps_debut;tadarida_taxon\n0.3;Pippip\n";

        assertThatThrownBy(() -> parser.parser(sansNom))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nom du fichier");
    }

    @Test
    @DisplayName("Une ligne entièrement vide (saut final) n'est pas comptée comme observation")
    void ignore_les_lignes_vides() {
        String avecLigneVide = "nom du fichier;tadarida_taxon\nseq_000;Pippip\n\n";

        assertThat(parser.parser(avecLigneVide).lignes()).hasSize(1);
    }

    @Test
    @DisplayName("Une probabilité textuelle d'un _Vu (« SUR ») est lue comme certitude observateur"
            + " (#1139), et la probabilité numérique reste inconnue")
    void capture_une_certitude_textuelle() {
        // Cas réel du dataset SAÉ (observations_Vu.csv ligne 2627) : l'observateur a saisi un code de
        // confiance « SUR » dans observateur_probabilite, là où un flottant est attendu. L'import ne
        // doit pas échouer (cf. incident « For input string: SUR ») et, depuis #1139, le jeton est
        // capturé comme certitude au lieu d'être perdu.
        String vuAvecConfiance = "nom du fichier;temps_debut;temps_fin;frequence_mediane;tadarida_taxon;"
                + "tadarida_probabilite;tadarida_taxon_autre;observateur_taxon;observateur_probabilite\n"
                + "seq_000;0.0;3.6;79.0;Rhifer;0.99;;Rhifer;SUR\n";

        LigneObservation ligne = parser.parser(vuAvecConfiance).lignes().get(0);

        assertThat(ligne.probObservateur())
                .as("confiance textuelle → probabilité numérique inconnue")
                .isNull();
        assertThat(ligne.certitudeObservateur())
                .as("le jeton SUR est capturé comme certitude, plus seulement toléré")
                .isEqualTo(fr.univ_amu.iut.commun.model.Certitude.SUR);
        assertThat(ligne.taxonObservateur()).isEqualTo("Rhifer");
        assertThat(ligne.probTadarida()).isEqualTo(0.99);
    }

    @Test
    @DisplayName("Une probabilité observateur numérique d'un _Vu reste lue en nombre, sans certitude")
    void probabilite_numerique_sans_certitude() {
        String vuNumerique = "nom du fichier;tadarida_taxon;observateur_taxon;observateur_probabilite\n"
                + "seq_000;Rhifer;Rhifer;0.85\n";

        LigneObservation ligne = parser.parser(vuNumerique).lignes().get(0);

        assertThat(ligne.probObservateur()).isEqualTo(0.85);
        assertThat(ligne.certitudeObservateur())
                .as("un nombre n'est pas un jeton de certitude")
                .isNull();
    }

    @Test
    @DisplayName("La tolérance ne s'applique qu'aux probabilités : un temps non numérique reste rejeté")
    void rejette_un_champ_non_probabilite_invalide() {
        // temps_debut hors colonnes *_probabilite : une valeur malformée doit lever (pas d'avalage
        // silencieux), pour ne pas masquer une donnée corrompue.
        String tempsInvalide = "nom du fichier;temps_debut;tadarida_taxon\nseq_000;abc;Pippip\n";

        assertThatThrownBy(() -> parser.parser(tempsInvalide)).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("Dialecte réel du CSV d'observations VigieChiro (#1565) : validateur_* ignorées, ancrage null")
    void parse_le_csv_d_observations_du_serveur() {
        // Entête exacte du serveur (probe #1568) : validateur_taxon / validateur_probabilite en plus,
        // validation_mode en moins, frequence_mediane en flottant. Le parseur repère par nom, lit ce qu'il
        // connaît, ignore le reste, et ne pose aucun ancrage (le CSV ne porte pas d'_id).
        String csv = "\"nom du fichier\";\"temps_debut\";\"temps_fin\";\"frequence_mediane\";\"tadarida_taxon\";"
                + "\"tadarida_probabilite\";\"tadarida_taxon_autre\";\"observateur_taxon\";"
                + "\"observateur_probabilite\";\"validateur_taxon\";\"validateur_probabilite\"\n"
                + "\"Car130711-Z41-PaRec_20260703_210004_000\";0.1;2.8;77.0;\"noise\";0.87;\"\";\"\";\"\";\"\";\"\"\n"
                + "\"Car130711-Z41-PaRec_20260703_210254_000\";0.0;2.7;151.0;\"Pippip\";0.93;\"Tetvir\";\"\";\"\";\"\";\"\"\n";

        ResultatParseTadarida resultat = parser.parser(csv);

        assertThat(resultat.format()).isEqualTo(FormatTadarida.BRUT);
        assertThat(resultat.lignes())
                .extracting(
                        LigneObservation::nomSequence,
                        LigneObservation::taxonTadarida,
                        LigneObservation::frequenceMedianeKHz,
                        LigneObservation::idDonneeVigieChiro)
                .containsExactly(
                        tuple("Car130711-Z41-PaRec_20260703_210004_000", "noise", 77, null),
                        tuple("Car130711-Z41-PaRec_20260703_210254_000", "Pippip", 151, null));
    }
}
