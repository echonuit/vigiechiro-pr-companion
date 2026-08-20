package fr.univ_amu.iut.importation.outils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/// Le dossier d'exemple d'une capture : un journal, un relevé, des WAV, sous un chemin **déterministe**.
///
/// ## Pourquoi une classe, et pas une méthode privée de [CaptureImport]
///
/// Parce que son geste central - **vider avant d'écrire** - n'était éprouvé par rien, et ne pouvait pas
/// l'être : il vivait dans une méthode `private static` d'un outil qui, pour tourner, monte une scène
/// JavaFX entière (#4044). Extraire nomme le concept **et** le rend atteignable par un test.
///
/// ## Le vidage, et ce qu'il évite
///
/// ⚠️ Le chemin est déterministe, sous `java.io.tmpdir`. Sans nettoyage, les fichiers d'une exécution
/// précédente restent, et la capture montre leur **somme** : vécu en ajustant une fixture -
/// « 4 enregistrement(s) WAV détecté(s) » pour une fixture qui en déclare deux, et un aperçu de préfixe
/// portant l'ancien nom.
///
/// ⚠️ La CI ne peut pas attraper cette régression : un runner est **neuf** à chaque fois, donc l'ancien
/// comportement y produisait déjà le bon résultat. Le piège ne mord qu'en **local**, c'est-à-dire
/// précisément là où l'on met une capture au point. C'est aussi pourquoi le contraire s'est cru vrai un
/// moment : un écart mesuré entre un rendu local et un rendu de CI avait été lu comme une pollution des
/// fichiers committés, alors qu'il venait des tuiles de carte absentes hors ligne.
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
        Path sd = Path.of(System.getProperty("java.io.tmpdir"), nom);
        vider(sd);
        Files.createDirectories(sd);
        Files.writeString(sd.resolve(NOM_JOURNAL), log, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve(NOM_RELEVE), ENTETE_RELEVE, StandardCharsets.UTF_8);
        for (String wav : wavs) {
            Files.writeString(sd.resolve(wav), CONTENU_WAV);
        }
        return sd;
    }

    /// Retire ce que le dossier contient, s'il existe. Ne descend pas : une fixture est plate.
    private static void vider(Path dossier) throws IOException {
        if (!Files.isDirectory(dossier)) {
            return;
        }
        try (Stream<Path> restes = Files.list(dossier)) {
            for (Path reste : restes.toList()) {
                Files.deleteIfExists(reste);
            }
        }
    }
}
