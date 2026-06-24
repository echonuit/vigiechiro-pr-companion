package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// Applique la convention de nommage des enregistrements originaux (R6/R7) sur les fichiers déjà
/// copiés dans le workspace.
///
/// Le firmware écrit ses fichiers sous leur nom brut `PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` (R7).
/// Avant tout dépôt, chaque fichier reçoit le préfixe `Car<carré>-<année>-Pass<n>-<point>-` (R6),
/// via [Prefixe#nommerOriginal(String)]. Les tirets sont des **tirets du 6** (U+002D
/// HYPHEN-MINUS), garantis par [Prefixe#TIRET].
///
/// Le renommage opère **dans le workspace** (sur la copie), jamais sur la carte SD (R9 : la source
/// reste intacte ; c'est [CopieProtegee] qui a déjà déposé les fichiers ici).
///
/// L'opération est **idempotente** : un fichier qui porte **déjà un préfixe R6** (`Car…`) est laissé en
/// place, qu'il concorde ou non avec le rattachement courant. On évite ainsi le **double préfixe**
/// `Car…-Car…` (#111) sur un dossier déjà renommé, et on peut relancer le renommage sans risque
/// (réimport). Une éventuelle discordance du préfixe présent est signalée en amont par l'aperçu, pas
/// corrigée ici (les noms existants sont conservés, R7).
public class Renommeur {

    /// Renomme tous les WAV de `dossierBruts` en leur appliquant le préfixe R6 (R7 conserve le
    /// suffixe d'origine). Les fichiers déjà préfixés (`Car…`) sont inchangés.
    ///
    /// @param dossierBruts dossier `bruts/` contenant les originaux à renommer
    /// @param prefixe préfixe de la session (R6)
    /// @return la liste des chemins finaux des originaux, triée par nom de fichier
    public List<Path> renommer(Path dossierBruts, Prefixe prefixe) {
        Objects.requireNonNull(dossierBruts, "dossierBruts");
        Objects.requireNonNull(prefixe, "prefixe");
        List<Path> resultats = new ArrayList<>();
        try (Stream<Path> flux = Files.list(dossierBruts)) {
            List<Path> originaux = flux.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path original : originaux) {
                resultats.add(renommerUn(original, prefixe));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Renommage impossible dans " + dossierBruts, e);
        }
        resultats.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return resultats;
    }

    /// Nom **final** qu'un fichier source portera après renommage R6, sans rien déplacer : un nom déjà
    /// préfixé (`Car…`) est conservé (idempotence, #111), sinon le préfixe est appliqué. Permet à
    /// l'appelant de savoir si un original a **déjà été copié + renommé** par un import interrompu, et de
    /// sauter sa copie (reprise #231).
    public static String nomApresRenommage(String nomSource, Prefixe prefixe) {
        Objects.requireNonNull(nomSource, "nomSource");
        Objects.requireNonNull(prefixe, "prefixe");
        return Prefixe.estNomPrefixe(nomSource) ? nomSource : prefixe.nommerOriginal(nomSource);
    }

    private Path renommerUn(Path original, Prefixe prefixe) throws IOException {
        String nom = original.getFileName().toString();
        if (Prefixe.estNomPrefixe(nom)) {
            return original; // déjà préfixé (R6) : conservé tel quel, jamais de double préfixe (#111)
        }
        Path cible = original.resolveSibling(prefixe.nommerOriginal(nom));
        return Files.move(original, cible, StandardCopyOption.ATOMIC_MOVE);
    }
}
