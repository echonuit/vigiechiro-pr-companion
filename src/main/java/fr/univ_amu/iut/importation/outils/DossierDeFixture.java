package fr.univ_amu.iut.importation.outils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Le dossier d'exemple d'une capture : un journal, un relevé, des WAV, sous un chemin **déterministe**.
///
/// ## Pourquoi une classe, et pas une méthode privée de [CaptureImport]
///
/// Parce que son geste central - **vider avant d'écrire** - n'était éprouvé par rien, et ne pouvait pas
/// l'être : il vivait dans une méthode `private static` d'un outil qui, pour tourner, monte une scène
/// JavaFX entière (#4044). Extraire nomme le concept **et** le rend atteignable par un test.
///
/// ## Un dossier NEUF à chaque appel, et pourquoi ce n'est pas qu'une affaire de sécurité
///
/// Le chemin était d'abord **déterministe**, sous `java.io.tmpdir`. CodeQL l'a signalé
/// (`java/local-temp-file-or-directory-information-disclosure`, #4049) : un chemin prévisible dans le
/// répertoire temporaire est lisible **et inscriptible** par les autres utilisateurs locaux.
///
/// ⚠️ Le risque n'est pas théorique pour cet outil-ci : il fabrique des images **publiées**. Un tiers
/// qui devine le chemin peut y déposer ce qu'il veut avant que la capture ne soit rendue, et la
/// documentation montrerait son contenu.
///
/// `Files.createTempDirectory` rend un dossier au nom imprévisible et, sur POSIX, aux droits du seul
/// propriétaire.
///
/// ⚠️ **Et cela règle par construction ce qu'un nettoyage gardait.** Le chemin déterministe obligeait à
/// vider avant d'écrire, sans quoi les fichiers d'une exécution précédente restaient et la capture
/// montrait leur **somme** - « 4 enregistrement(s) WAV détecté(s) » pour une fixture qui en déclare
/// deux. Un dossier neuf n'a rien à vider. Le garde disparaît avec le défaut qu'il gardait, ce qui vaut
/// mieux que de le conserver.
///
/// ⚠️ Ce que le nom aléatoire ne coûte PAS : l'identité au bit près des captures. Le champ « Dossier
/// source » se lie à `source().libelleProperty()`, que cet outil ne pose pas ; le chemin ne paraît donc
/// sur aucune image. Vérifié avant de changer, la galerie promettant des PNG identiques au bit près.
final class DossierDeFixture {

    private static final String NOM_JOURNAL = "LogPR1925492.txt";
    private static final String NOM_RELEVE = "PaRecPR1925492_THLog.csv";
    private static final String ENTETE_RELEVE = "Date\tHour\n";
    private static final String CONTENU_WAV = "wav";

    private DossierDeFixture() {
        // Outil sans état.
    }

    /// Prépare le dossier `nom` sous le répertoire temporaire : le **vide**, puis y écrit le journal, le
    /// relevé et les WAV demandés.
    ///
    /// @param nom  le nom du dossier, déterministe d'une exécution à l'autre
    /// @param log  le contenu du journal du capteur
    /// @param wavs les noms de fichiers WAV à créer, dans l'ordre
    /// @return le chemin du dossier préparé
    static Path preparer(String nom, String log, List<String> wavs) throws IOException {
        // Le nom reste en PRÉFIXE : le dossier garde de quoi se reconnaître quand on fouille /tmp
        // après un rendu, sans que son chemin se devine à l'avance.
        Path sd = Files.createTempDirectory(nom + "-");
        Files.writeString(sd.resolve(NOM_JOURNAL), log, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve(NOM_RELEVE), ENTETE_RELEVE, StandardCharsets.UTF_8);
        for (String wav : wavs) {
            Files.writeString(sd.resolve(wav), CONTENU_WAV);
        }
        return sd;
    }
}
