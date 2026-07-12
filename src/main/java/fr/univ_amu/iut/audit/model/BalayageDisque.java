package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/// Balayage inverse du disque (disque vers base) : fichiers présents mais référencés par aucune ligne,
/// et dossiers de session sans ligne `recording_session`. Isolé de [ServiceAuditCoherence] pour
/// concentrer ici l'accès au système de fichiers (et alléger le moteur).
class BalayageDisque {

    private static final String BRUTS = "bruts";
    private static final String TRANSFORMES = "transformes";

    /// Fichiers présents sous `bruts/` ou `transformes/` de la session mais absents de `cheminsConnus`
    /// (ensemble des `file_path` normalisés des originaux et séquences du passage). Le dossier `depot/`
    /// est volontairement ignoré : ses archives sont régénérables et non tracées comme `file_path`.
    List<ConstatAudit> orphelinsDeSession(Long idPassage, Path racineSession, Set<String> cheminsConnus) {
        List<ConstatAudit> constats = new ArrayList<>();
        for (String sousDossier : List.of(BRUTS, TRANSFORMES)) {
            Path dossier = racineSession.resolve(sousDossier);
            if (!Files.isDirectory(dossier)) {
                continue;
            }
            try (Stream<Path> fichiers = Files.list(dossier)) {
                fichiers.filter(Files::isRegularFile)
                        .map(BalayageDisque::normaliser)
                        .filter(chemin -> !cheminsConnus.contains(chemin))
                        .forEach(chemin -> constats.add(new ConstatAudit(
                                SeveriteConstat.AVERTISSEMENT,
                                CategorieConstat.DISQUE_ORPHELIN,
                                idPassage,
                                chemin,
                                "Fichier présent sur disque mais référencé par aucune ligne en base.")));
            } catch (IOException echec) {
                throw new UncheckedIOException("Balayage impossible du dossier " + dossier, echec);
            }
        }
        return constats;
    }

    /// Dossiers ressemblant à une session (nom `CarXXXXXX-AAAA-PassN-POINT`) directement sous la racine
    /// du workspace, sans racine `recording_session` correspondante.
    List<ConstatAudit> dossiersOrphelins(Path racineWorkspace, Set<String> racinesSessionConnues) {
        List<ConstatAudit> constats = new ArrayList<>();
        if (!Files.isDirectory(racineWorkspace)) {
            return constats;
        }
        try (Stream<Path> entrees = Files.list(racineWorkspace)) {
            entrees.filter(Files::isDirectory)
                    .filter(dossier -> ressembleASession(dossier.getFileName().toString()))
                    .map(BalayageDisque::normaliser)
                    .filter(chemin -> !racinesSessionConnues.contains(chemin))
                    .forEach(chemin -> constats.add(new ConstatAudit(
                            SeveriteConstat.AVERTISSEMENT,
                            CategorieConstat.DOSSIER_ORPHELIN,
                            null,
                            chemin,
                            "Dossier de session sur disque sans passage correspondant en base.")));
        } catch (IOException echec) {
            throw new UncheckedIOException("Balayage impossible de la racine " + racineWorkspace, echec);
        }
        return constats;
    }

    private static String normaliser(Path chemin) {
        return chemin.toAbsolutePath().normalize().toString();
    }

    /// Un nom de dossier de session (`CarXXXXXX-AAAA-PassN-POINT`). On réutilise le motif de préfixe de
    /// [Prefixe#estNomPrefixe] en ajoutant un suffixe factice, car ce motif valide un nom de FICHIER
    /// (préfixe + suffixe), pas un nom de dossier nu.
    private static boolean ressembleASession(String nomDossier) {
        return Prefixe.estNomPrefixe(nomDossier + "-x");
    }
}
