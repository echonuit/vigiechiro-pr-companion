package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FichierSigne;
import fr.univ_amu.iut.commun.api.PieceJointe;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.TypePieceJointe;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/// Ce que le serveur fait d'une archive du repli manuel dont une partie des sons est déjà en ligne
/// (#5970, cas de recette `S4-105`).
///
/// Le repli manuel (ADR 5867) faisait générer des archives de **toute** la nuit, séquences déjà en
/// ligne comprises, et l'ADR tenait pour une hypothèse ce que le portail en fait. Ce banc l'observe sur
/// le code du serveur à la révision épinglée : il dépose par le client de l'application, puis fait
/// jouer au serveur l'extraction que son worker lance au traitement d'une participation.
///
/// Ce qu'il établit : le serveur ajoute chaque fichier de l'archive **sans chercher** s'il est déjà là.
/// Un son déjà en ligne figure donc deux fois dans les fichiers de la participation, alors que
/// l'analyse n'en tire qu'une donnée. Le jour où le serveur dédoublonnera, ce banc rougira, et c'est
/// le signal attendu : le repli pourra alors se relire.
///
/// C'est pourquoi le repli n'archive plus que ce qui n'est pas en ligne (#5975) : le troisième cas
/// tient le remède sur le même serveur, une archive des seuls sons absents ne laissant aucun doublon.
///
/// Ce qu'il ne joue pas : le dépôt à la main dans le navigateur du portail, et l'analyse Tadarida.
@Tag("plateforme-de-test")
@ExtendWith(PlateformeDeTest.class)
class ArchiveDuRepliSurLaPlateformeDeTestTest {

    /// Le préfixe que le serveur exige d'un son de Point Fixe, puis de quoi distinguer les trois cas : la
    /// participation d'essai sert à d'autres bancs, et chaque cas ne compte que ses propres sons.
    private static final String NUIT = "Car130711-2026-Pass1-Z1-";

    private static final int SONS_DE_LA_NUIT = 4;

    private final ClientVigieChiro client = client();
    private final String participation = PlateformeDeTest.acces().id("participations:nuit-vierge");

    private static ClientVigieChiro client() {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
        String jeton = acces.jeton("observatrice");
        return new ClientVigieChiro(acces.urlDeBase(), () -> Optional.of(jeton));
    }

    private static List<String> titres(String prefixe) {
        return IntStream.range(0, SONS_DE_LA_NUIT)
                .mapToObj(i -> prefixe + "_20260622_20250" + i + "_000.wav")
                .toList();
    }

    private static byte[] son(String titre) {
        return ("RIFF" + titre).getBytes(StandardCharsets.UTF_8);
    }

    /// Dépose un fichier comme l'application le fait : le déclarer, envoyer ses octets, le finaliser.
    private void deposer(String titre, byte[] octets, String mime) {
        ReponseApi<FichierSigne> declare = client.creerFichier(titre, participation);
        assertThat(declare).as("déclarer %s", titre).isInstanceOf(ReponseApi.Succes.class);
        FichierSigne fichier = ((ReponseApi.Succes<FichierSigne>) declare).valeur();
        assertThat(client.televerserVersS3(fichier.urlSignee(), octets, mime))
                .as("envoyer %s", titre)
                .isInstanceOf(ReponseApi.Succes.class);
        assertThat(client.finaliserFichier(fichier.id()))
                .as("finaliser %s", titre)
                .isInstanceOf(ReponseApi.Succes.class);
    }

    /// Une archive des `sons` donnés, déposée comme une archive de dépôt : toute la nuit, ou ce qui manque.
    private Path deposerUneArchive(String titreDeLArchive, List<String> sons, Path dossier) throws IOException {
        Path archive = dossier.resolve(titreDeLArchive);
        try (OutputStream flux = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(flux)) {
            for (String titre : sons) {
                zip.putNextEntry(new ZipEntry(titre));
                zip.write(son(titre));
                zip.closeEntry();
            }
        }
        deposer(titreDeLArchive, Files.readAllBytes(archive), "application/zip");
        return archive;
    }

    /// Les sons de ce cas tels que l'application les relit, chacun avec le nombre de fois où il figure.
    private Map<String, Long> sonsRelusParLApplication(String prefixe) {
        ReponseApi<List<PieceJointe>> relus = client.piecesJointes(participation, TypePieceJointe.WAV);
        assertThat(relus).isInstanceOf(ReponseApi.Succes.class);
        return ((ReponseApi.Succes<List<PieceJointe>>) relus)
                .valeur().stream()
                        .map(PieceJointe::titre)
                        .filter(titre -> titre != null && titre.startsWith(prefixe))
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static List<String> enDouble(JsonObject bilan) {
        return bilan.getAsJsonArray("en_double").asList().stream()
                .map(JsonElement::getAsString)
                .toList();
    }

    @Test
    @DisplayName("#5970 : témoin, rien en ligne avant l'archive : chaque son de la nuit arrive une fois")
    void sans_rien_en_ligne_chaque_son_arrive_une_fois(@TempDir Path dossier) throws IOException {
        String prefixe = NUIT + "temoin5970";
        List<String> sons = titres(prefixe);
        String titreDeLArchive = prefixe + "-1.zip";

        Path archive = deposerUneArchive(titreDeLArchive, sons, dossier);
        JsonObject bilan =
                WorkerDeLaPlateformeDeTest.jouerLExtraction(participation, Map.of(titreDeLArchive, archive), prefixe);

        assertThat(bilan.get("wav_avant").getAsInt()).isZero();
        assertThat(bilan.get("wav_apres").getAsInt())
                .as("le témoin : sans son déjà en ligne, l'extraction ne fabrique aucun doublon")
                .isEqualTo(SONS_DE_LA_NUIT);
        assertThat(enDouble(bilan)).isEmpty();
        assertThat(sonsRelusParLApplication(prefixe)).hasSize(SONS_DE_LA_NUIT).containsOnlyKeys(sons);
        assertThat(sonsRelusParLApplication(prefixe).values()).containsOnly(1L);
    }

    @Test
    @DisplayName("#5970 : deux sons déjà en ligne reviennent en double quand le serveur extrait l'archive de la nuit")
    void un_son_deja_en_ligne_revient_en_double(@TempDir Path dossier) throws IOException {
        String prefixe = NUIT + "repli5970";
        List<String> sons = titres(prefixe);
        List<String> dejaEnLigne = sons.subList(0, 2);
        String titreDeLArchive = prefixe + "-1.zip";

        // Ce que le dépôt en séquences WAV a laissé en ligne avant d'être refusé pour le reste.
        dejaEnLigne.forEach(titre -> deposer(titre, son(titre), "audio/wav"));
        // Le repli d'avant #5975 : l'archive de TOUTE la nuit, déposée à la main.
        Path archive = deposerUneArchive(titreDeLArchive, sons, dossier);
        JsonObject bilan =
                WorkerDeLaPlateformeDeTest.jouerLExtraction(participation, Map.of(titreDeLArchive, archive), prefixe);

        assertThat(bilan.get("wav_avant").getAsInt()).isEqualTo(2);
        assertThat(bilan.get("wav_apres").getAsInt())
                .as("le serveur ajoute chaque fichier de l'archive sans chercher s'il est déjà là")
                .isEqualTo(SONS_DE_LA_NUIT + 2);
        assertThat(bilan.get("titres_distincts").getAsInt()).isEqualTo(SONS_DE_LA_NUIT);
        assertThat(enDouble(bilan)).containsExactlyElementsOf(dejaEnLigne);
        assertThat(bilan.get("donnees").getAsInt())
                .as("l'analyse regroupe par nom : un son en double ne fait qu'une donnée")
                .isEqualTo(SONS_DE_LA_NUIT);

        Map<String, Long> relus = sonsRelusParLApplication(prefixe);
        assertThat(relus)
                .as("ce que l'application relit de la participation : les deux sons déjà en ligne y sont deux fois")
                .containsEntry(sons.get(0), 2L)
                .containsEntry(sons.get(1), 2L)
                .containsEntry(sons.get(2), 1L)
                .containsEntry(sons.get(3), 1L);
    }

    @Test
    @DisplayName("#5975 : le remède, une archive des seuls sons absents ne laisse aucun son en double")
    void l_archive_des_seuls_sons_absents_ne_laisse_aucun_doublon(@TempDir Path dossier) throws IOException {
        String prefixe = NUIT + "remede5975";
        List<String> sons = titres(prefixe);
        List<String> dejaEnLigne = sons.subList(0, 2);
        List<String> absents = sons.subList(2, SONS_DE_LA_NUIT);
        String titreDeLArchive = prefixe + "-1.zip";

        dejaEnLigne.forEach(titre -> deposer(titre, son(titre), "audio/wav"));
        // Ce que la génération produit depuis #5975 : seulement ce que le plan ne dit pas déposé.
        Path archive = deposerUneArchive(titreDeLArchive, absents, dossier);
        JsonObject bilan =
                WorkerDeLaPlateformeDeTest.jouerLExtraction(participation, Map.of(titreDeLArchive, archive), prefixe);

        assertThat(bilan.get("wav_apres").getAsInt())
                .as("deux sons en ligne, deux rendus par l'archive : quatre fichiers pour quatre sons")
                .isEqualTo(SONS_DE_LA_NUIT);
        assertThat(enDouble(bilan)).isEmpty();
        assertThat(bilan.get("donnees").getAsInt()).isEqualTo(SONS_DE_LA_NUIT);
        assertThat(sonsRelusParLApplication(prefixe)).hasSize(SONS_DE_LA_NUIT).containsOnlyKeys(sons);
        assertThat(sonsRelusParLApplication(prefixe).values()).containsOnly(1L);
    }
}
