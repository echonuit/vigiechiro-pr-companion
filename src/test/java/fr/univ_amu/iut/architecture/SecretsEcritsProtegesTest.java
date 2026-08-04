package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Un secret ne s'écrit pas en direct** (#2735).
///
/// Écrire puis restreindre les permissions laisse une fenêtre pendant laquelle le fichier existe avec
/// celles de l'umask, souvent `644`. `EcritureProtegee` supprime la fenêtre en créant le fichier
/// **déjà** restreint, et remplace la cible de façon atomique.
///
/// Ce garde est **structurel**, et il l'est par nécessité : la fenêtre est un état **intermédiaire**.
/// Après coup, les deux façons d'écrire laissent exactement le même fichier à `600` - aucun test d'état
/// final ne les distingue. Sans ce garde, un retour à `writeString` + `chmod` ne ferait rougir personne.
///
/// Il lit le **source** plutôt que le bytecode, pour la même raison qu'[IsolationFeatureSourcesTest] :
/// ce qu'on veut interdire est une **forme d'écriture**, qui ne laisse aucune trace distinctive dans le
/// `.class`.
class SecretsEcritsProtegesTest {

    private static final Path RACINE = Path.of("src/main/java/fr/univ_amu/iut");

    /// Les classes qui écrivent un secret sur le disque. Cette liste se complète : toute nouvelle
    /// écriture de secret (jeton, identifiant, clé) doit y entrer, et passer par `EcritureProtegee`.
    private static final List<String> PORTEUSES_DE_SECRET = List.of("connexion/model/StockageConnexion.java");

    /// Les façons d'écrire un fichier qui posent les permissions **après** coup, c'est-à-dire toutes
    /// celles qui ne passent pas par `EcritureProtegee`.
    private static final Pattern ECRITURE_DIRECTE = Pattern.compile(
            "Files\\.write\\(|Files\\.writeString\\(|Files\\.newOutputStream\\(|new (File|Print)Writer\\(");

    @Test
    @DisplayName("#2735 : aucune classe porteuse de secret n'écrit son fichier en direct")
    void aucun_secret_ecrit_en_direct() throws IOException {
        List<String> fautifs = new ArrayList<>();
        for (String source : PORTEUSES_DE_SECRET) {
            Path chemin = RACINE.resolve(source);
            assertThat(chemin)
                    .as("la liste des porteuses de secret cite un fichier absent")
                    .exists();
            String contenu = Files.readString(chemin, StandardCharsets.UTF_8);
            if (ECRITURE_DIRECTE.matcher(contenu).find()) {
                fautifs.add(source);
            }
        }

        assertThat(fautifs)
                .as("ces classes écrivent un secret en direct : le fichier existe alors brièvement avec les"
                        + " permissions de l'umask. Passer par EcritureProtegee (commun.model), qui le crée"
                        + " déjà restreint et remplace la cible de façon atomique.")
                .isEmpty();
    }
}
