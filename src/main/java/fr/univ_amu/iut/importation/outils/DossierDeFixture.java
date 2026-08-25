package fr.univ_amu.iut.importation.outils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Le dossier d'exemple d'une capture : un journal, un relevé, des WAV, sous un chemin imprévisible.
///
/// C'est une classe et non une méthode privée de [CaptureImport] parce que son geste central n'était
/// éprouvé par rien et ne pouvait pas l'être : il vivait dans un `private static` d'un outil qui, pour
/// tourner, monte une scène JavaFX entière (#4044).
///
/// **Un dossier neuf à chaque appel.** Le chemin était d'abord déterministe, sous `java.io.tmpdir`, ce
/// que CodeQL a signalé (`java/local-temp-file-or-directory-information-disclosure`, #4049) : un
/// chemin prévisible y est lisible **et inscriptible** par les autres utilisateurs locaux. Le risque
/// n'est pas théorique ici, cet outil fabriquant des images **publiées** - un tiers qui devine le
/// chemin peut y déposer ce qu'il veut avant le rendu, et la documentation montrerait son contenu.
/// `Files.createTempDirectory` rend un nom imprévisible et, sur POSIX, aux droits du seul propriétaire.
///
/// Cela règle par construction ce qu'un nettoyage gardait : le chemin déterministe obligeait à vider
/// avant d'écrire, sans quoi les fichiers d'une exécution précédente restaient et la capture montrait
/// leur **somme**, « 4 enregistrement(s) WAV détecté(s) » pour une fixture qui en déclare deux. Un
/// dossier neuf n'a rien à vider.
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
