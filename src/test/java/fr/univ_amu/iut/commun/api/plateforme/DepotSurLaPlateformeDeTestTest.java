package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.IssueDeDepot;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SuiviReprise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// Le critère de fin de #5642 : sans aucun jeton de la plateforme nationale, Companion lit `/moi` et
/// dépose une archive en parties sur la plateforme de test, et ce qu'il envoie se relit au journal du
/// faux S3.
///
/// Rouge sur sa mutation, jouée à la main sur #5663 : un faux S3 servi en `http` fait refuser l'URL de
/// dépôt par `UrlSigneeAdmise`, et le dépôt échoue.
@Tag("plateforme-de-test")
@ExtendWith(PlateformeDeTest.class)
class DepotSurLaPlateformeDeTestTest {

    private static ClientVigieChiro client(String cleUtilisateur) {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
        String jeton = acces.jeton(cleUtilisateur);
        return new ClientVigieChiro(acces.urlDeBase(), () -> Optional.of(jeton));
    }

    @ParameterizedTest
    @CsvSource({"observatrice,Observateur", "validatrice,Validateur", "administratrice,Administrateur"})
    void chaque_jeton_frappe_ouvre_moi_sous_son_role(String cle, String role) {
        ReponseApi<ProfilVigieChiro> moi = client(cle).moi();

        assertThat(moi).isInstanceOf(ReponseApi.Succes.class);
        assertThat(((ReponseApi.Succes<ProfilVigieChiro>) moi).valeur().role()).isEqualTo(role);
    }

    @Test
    void une_archive_au_dela_du_seuil_part_en_parties_entieres_et_sans_type(@TempDir Path dossier) throws IOException {
        long taille = 2 * ClientVigieChiro.SEUIL_MULTIPART_OCTETS + 12_345;
        byte[] octets = new byte[(int) taille];
        new Random(5663).nextBytes(octets);
        Path archive = Files.write(dossier.resolve("nuit.zip"), octets);
        ClientVigieChiro client = client("observatrice");
        String participation = PlateformeDeTest.acces().id("participations:nuit-vierge");

        ReponseApi<String> fichier = client.creerFichierMultipart("Car130711-2026-Pass1-Z1-5663.zip", participation);
        assertThat(fichier).isInstanceOf(ReponseApi.Succes.class);
        String idFichier = ((ReponseApi.Succes<String>) fichier).valeur();
        List<Double> progression = new ArrayList<>();
        IssueDeDepot issue = client.deposerEnParts(idFichier, archive, progression::add, SuiviReprise.SILENCIEUX);

        assertThat(issue.reponse()).isInstanceOf(ReponseApi.Succes.class);
        assertThat(progression).last().isEqualTo(1.0);
        List<JsonElement> parties = new ArrayList<>();
        JsonArray journal =
                JsonParser.parseString(PlateformeDeTest.acces().journalS3()).getAsJsonArray();
        journal.forEach(entree -> {
            if ("/".equals(entree.getAsJsonObject().get("chemin").getAsString())) {
                parties.add(entree);
            }
        });
        assertThat(parties).hasSize(3);
        assertThat(parties)
                .allSatisfy(partie -> assertThat(
                                partie.getAsJsonObject().get("type").isJsonNull())
                        .isTrue());
        assertThat(parties.stream()
                        .mapToLong(p -> p.getAsJsonObject().get("taille").getAsLong())
                        .sum())
                .isEqualTo(taille);
    }
}
