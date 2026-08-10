package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// Construit le **bilan d'import** (#155) à partir des issues de découpage : trie les originaux
/// transformés vs rejetés et assemble le [RapportImport] (importés / rejetés / fichiers non pertinents
/// ignorés). Extrait de [ServiceImport] pour ne pas y concentrer trop de responsabilités.
final class RapportImportFabrique {

    private RapportImportFabrique() {}

    /// Résultat du tri : les transformations réussies (à persister), les rejets (pour le message), et le
    /// rapport prêt à exposer/exporter.
    record BilanImport(
            List<TransformationOriginal> transformations, List<ResultatDecoupage> rejets, RapportImport rapport) {}

    static BilanImport bilan(
            Path dossierSource,
            RapportInspection inspection,
            List<ResultatDecoupage> resultats,
            List<PassageExistant> doublonsNuit,
            List<Path> ecartesHorsSerie) {
        List<TransformationOriginal> transformations = resultats.stream()
                .filter(ResultatDecoupage::reussi)
                .map(ResultatDecoupage::transformation)
                .toList();
        List<ResultatDecoupage> rejets =
                resultats.stream().filter(r -> !r.reussi()).toList();

        List<LigneRapport> lignes = new ArrayList<>();
        for (TransformationOriginal t : transformations) {
            lignes.add(new LigneRapport(
                    t.nomOriginal(), StatutImportFichier.IMPORTE, t.sequences().size() + " séquence(s)"));
        }
        for (ResultatDecoupage rejet : rejets) {
            lignes.add(new LigneRapport(rejet.nomFichier(), StatutImportFichier.REJETE, rejet.erreur()));
        }
        // #1492 : les enregistrements d'un autre capteur sont écartés de l'import, pas escamotés. Ils
        // figurent au compte rendu au même titre que les fichiers non pertinents - un fichier qui
        // disparaît sans un mot est pire qu'un fichier importé à tort, parce que rien ne le rattrape.
        for (Path ecarte : ecartesHorsSerie) {
            lignes.add(new LigneRapport(
                    ecarte.getFileName().toString(),
                    StatutImportFichier.IGNORE,
                    "enregistré par un autre capteur que celui du journal"));
        }
        lignes.addAll(lignesIgnorees(dossierSource, inspection));
        return new BilanImport(transformations, rejets, new RapportImport(lignes, doublonsNuit));
    }

    /// Fichiers réguliers à la racine de la source qui ne sont ni un WAV, ni le journal, ni le relevé :
    /// laissés de côté (IGNORE) avec une trace, plutôt que silencieusement.
    private static List<LigneRapport> lignesIgnorees(Path dossierSource, RapportInspection inspection) {
        String nomJournal = inspection.cheminJournal() == null
                ? null
                : inspection.cheminJournal().getFileName().toString();
        String nomReleve = inspection.cheminReleveClimatique() == null
                ? null
                : inspection.cheminReleveClimatique().getFileName().toString();
        try (Stream<Path> fichiers = Files.list(dossierSource)) {
            return fichiers.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(nom -> !nom.toLowerCase().endsWith(".wav"))
                    .filter(nom -> !nom.equals(nomJournal) && !nom.equals(nomReleve))
                    .sorted()
                    .map(nom -> new LigneRapport(nom, StatutImportFichier.IGNORE, "fichier non pertinent"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier source impossible : " + dossierSource, e);
        }
    }
}
